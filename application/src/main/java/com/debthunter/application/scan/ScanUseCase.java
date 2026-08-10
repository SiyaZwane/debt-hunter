package com.debthunter.application.scan;

import com.debthunter.application.history.HistoryDepthCheck;
import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.DebtMetric;
import com.debthunter.domain.EngineStatus;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.PolicyStatus;
import com.debthunter.domain.PolicyViolation;
import com.debthunter.domain.ScanResult;
import com.debthunter.engine.spi.AnalysisEngine;
import com.debthunter.engine.spi.AnalysisRequest;
import com.debthunter.engine.spi.EngineResult;
import com.debthunter.engine.spi.ProgressSink;
import com.debthunter.engine.spi.RepositoryContext;
import com.debthunter.engine.spi.VcsType;
import com.debthunter.output.JsonReporter;
import com.debthunter.output.MarkdownReporter;
import com.debthunter.output.MetricsReporter;
import com.debthunter.output.ReportWriteException;
import com.debthunter.output.SarifReporter;
import com.debthunter.policy.BaselineComparator;
import com.debthunter.policy.BaselineProvenance;
import com.debthunter.policy.BaselineResolution;
import com.debthunter.policy.BaselineResolver;
import com.debthunter.policy.PolicyBundle;
import com.debthunter.policy.PolicyBundleParser;
import com.debthunter.policy.PolicyComposer;
import com.debthunter.policy.PolicyEvaluator;
import com.debthunter.policy.PolicyLoosenedException;
import com.debthunter.policy.PolicyParseException;
import com.debthunter.repository.HistoryWindow;
import com.debthunter.repository.RepositoryAccessException;
import com.debthunter.repository.RepositoryHistoryProvider;
import com.debthunter.repository.RepositoryInfo;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Orchestrates one scan: inspects the repository, runs each applicable engine with a hard timeout,
 * aggregates the results, and writes every report.
 */
public final class ScanUseCase {

  private static final Duration DEFAULT_ENGINE_TIMEOUT = Duration.ofMinutes(5);

  // Pure, stateless, and never substituted with a fake in any test (unlike the collaborators
  // below, which every test still constructs for real too, but were designed injectable from the
  // start) — instantiated directly rather than threaded through both constructors and every one
  // of their many existing call sites.
  private final ProjectSlicer projectSlicer = new ProjectSlicer();

  private final RepositoryHistoryProvider historyProvider;
  private final JsonReporter jsonReporter;
  private final MarkdownReporter markdownReporter;
  private final MetricsReporter metricsReporter;
  private final SarifReporter sarifReporter;
  private final BaselineResolver baselineResolver;
  private final BaselineComparator baselineComparator;
  private final PolicyBundleParser policyBundleParser;
  private final PolicyEvaluator policyEvaluator;
  private final HistoryDepthEnforcer historyDepthEnforcer;
  private final String toolVersion;
  private final Duration engineTimeout;
  private final Clock clock;
  // Composed from policyBundleParser above rather than added as its own constructor parameter,
  // for the same reason as projectSlicer: no test call site needs to substitute a fake one.
  private final PolicyComposer policyComposer;

  /**
   * Creates a use case with the default 5-minute per-engine timeout and system clock.
   *
   * @param historyProvider how to inspect the repository under scan
   * @param jsonReporter writes {@code debt-hunter.json}
   * @param markdownReporter writes {@code summary.md}
   * @param metricsReporter writes {@code metrics.json}
   * @param sarifReporter writes {@code debt-hunter.sarif}
   * @param baselineResolver resolves the baseline to compare this scan against
   * @param baselineComparator classifies findings against the resolved baseline
   * @param policyBundleParser parses the policy bundle configured for this scan, if any
   * @param policyEvaluator evaluates the policy bundle against this scan's new findings
   * @param historyDepthEnforcer checks history depth and adjusts confidence when it's incomplete
   * @param toolVersion this build's version, recorded in every {@link AnalysisRun}
   */
  public ScanUseCase(
      RepositoryHistoryProvider historyProvider,
      JsonReporter jsonReporter,
      MarkdownReporter markdownReporter,
      MetricsReporter metricsReporter,
      SarifReporter sarifReporter,
      BaselineResolver baselineResolver,
      BaselineComparator baselineComparator,
      PolicyBundleParser policyBundleParser,
      PolicyEvaluator policyEvaluator,
      HistoryDepthEnforcer historyDepthEnforcer,
      String toolVersion) {
    this(
        historyProvider,
        jsonReporter,
        markdownReporter,
        metricsReporter,
        sarifReporter,
        baselineResolver,
        baselineComparator,
        policyBundleParser,
        policyEvaluator,
        historyDepthEnforcer,
        toolVersion,
        DEFAULT_ENGINE_TIMEOUT,
        Clock.systemUTC());
  }

  /**
   * Creates a use case with an explicit engine timeout and clock, for testing.
   *
   * @param historyProvider how to inspect the repository under scan
   * @param jsonReporter writes {@code debt-hunter.json}
   * @param markdownReporter writes {@code summary.md}
   * @param metricsReporter writes {@code metrics.json}
   * @param sarifReporter writes {@code debt-hunter.sarif}
   * @param baselineResolver resolves the baseline to compare this scan against
   * @param baselineComparator classifies findings against the resolved baseline
   * @param policyBundleParser parses the policy bundle configured for this scan, if any
   * @param policyEvaluator evaluates the policy bundle against this scan's new findings
   * @param historyDepthEnforcer checks history depth and adjusts confidence when it's incomplete
   * @param toolVersion this build's version, recorded in every {@link AnalysisRun}
   * @param engineTimeout the maximum time to wait for any single engine
   * @param clock the clock used for timestamps and duration measurement
   */
  public ScanUseCase(
      RepositoryHistoryProvider historyProvider,
      JsonReporter jsonReporter,
      MarkdownReporter markdownReporter,
      MetricsReporter metricsReporter,
      SarifReporter sarifReporter,
      BaselineResolver baselineResolver,
      BaselineComparator baselineComparator,
      PolicyBundleParser policyBundleParser,
      PolicyEvaluator policyEvaluator,
      HistoryDepthEnforcer historyDepthEnforcer,
      String toolVersion,
      Duration engineTimeout,
      Clock clock) {
    this.historyProvider = Objects.requireNonNull(historyProvider, "historyProvider");
    this.jsonReporter = Objects.requireNonNull(jsonReporter, "jsonReporter");
    this.markdownReporter = Objects.requireNonNull(markdownReporter, "markdownReporter");
    this.metricsReporter = Objects.requireNonNull(metricsReporter, "metricsReporter");
    this.sarifReporter = Objects.requireNonNull(sarifReporter, "sarifReporter");
    this.baselineResolver = Objects.requireNonNull(baselineResolver, "baselineResolver");
    this.baselineComparator = Objects.requireNonNull(baselineComparator, "baselineComparator");
    this.policyBundleParser = Objects.requireNonNull(policyBundleParser, "policyBundleParser");
    this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator");
    this.historyDepthEnforcer =
        Objects.requireNonNull(historyDepthEnforcer, "historyDepthEnforcer");
    this.toolVersion = Objects.requireNonNull(toolVersion, "toolVersion");
    this.engineTimeout = Objects.requireNonNull(engineTimeout, "engineTimeout");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.policyComposer = new PolicyComposer(this.policyBundleParser);
  }

  /**
   * Runs one scan end to end.
   *
   * @param request what to scan and how
   * @return the exit code and, if analysis ran, the completed {@link ScanResult}
   */
  public ScanOutcome execute(ScanRequest request) {
    RepositoryInfo repoInfo;
    try {
      repoInfo = historyProvider.inspect(request.repoPath());
    } catch (RepositoryAccessException e) {
      return ScanOutcome.ofError(
          ExitCode.INTERNAL_ERROR.code(), "Failed to inspect repository: " + e.getMessage());
    }

    if (!repoInfo.isGitRepo()) {
      return ScanOutcome.ofError(
          ExitCode.CONFIGURATION_ERROR.code(), "Not a Git repository: " + request.repoPath());
    }

    try {
      return analyseAndReport(request, repoInfo);
    } catch (RuntimeException e) {
      return ScanOutcome.ofError(ExitCode.INTERNAL_ERROR.code(), "Scan failed: " + e.getMessage());
    }
  }

  /**
   * Validates and orchestrates, in the documented pre-analysis order: configuration errors,
   * insufficient history, an unusable baseline, then the engines themselves.
   */
  private ScanOutcome analyseAndReport(ScanRequest request, RepositoryInfo repoInfo) {
    PolicyBundle policyBundle;
    try {
      PolicyBundle central = policyBundleParser.loadCentral(request.policyPath());
      policyBundle = policyComposer.compose(request.repoPath(), central).bundle();
    } catch (PolicyParseException | PolicyLoosenedException e) {
      return ScanOutcome.ofError(
          ExitCode.CONFIGURATION_ERROR.code(), "Invalid policy: " + e.getMessage());
    }

    HistoryDepth historyDepth = mapHistoryDepth(repoInfo);
    HistoryDepthCheck historyDepthCheck =
        historyDepthEnforcer.check(historyDepth, policyBundle.minimumHistoryDepth());
    if (!historyDepthCheck.sufficient()) {
      return ScanOutcome.ofError(
          ExitCode.INSUFFICIENT_HISTORY.code(), historyDepthCheck.diagnosticMessage());
    }

    BaselineResolution baselineResolution =
        baselineResolver.resolve(request.baselinePath(), toolVersion);
    if (baselineResolution.isIncompatible()) {
      return ScanOutcome.ofError(
          ExitCode.BASELINE_UNAVAILABLE.code(), baselineResolution.incompatibilityReason());
    }

    RepositoryContext context =
        new RepositoryContext(request.repoPath(), List.of(), VcsType.GIT, historyDepth);

    List<EngineStatus> engineStatuses = new ArrayList<>();
    List<Finding> allFindings = new ArrayList<>();
    Map<String, DebtMetric> allMetrics = new LinkedHashMap<>();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      for (AnalysisEngine engine : request.engines()) {
        if (!engine.supports(context)) {
          continue;
        }
        EngineResult result = runWithTimeout(executor, engine, request);
        engineStatuses.add(
            new EngineStatus(
                engine.descriptor().id(),
                engine.descriptor().version(),
                result.status(),
                result.durationMs(),
                result.reason()));
        allFindings.addAll(result.findings());
        for (DebtMetric metric : result.metrics()) {
          allMetrics.put(metric.name(), metric);
        }
      }
    } finally {
      executor.shutdownNow();
    }

    List<Finding> confidenceAdjustedFindings =
        historyDepthEnforcer.adjustConfidence(allFindings, historyDepth);

    AnalysisRun run =
        AnalysisRun.builder()
            .id(UUID.randomUUID().toString())
            .toolVersion(toolVersion)
            .timestamp(clock.instant())
            .repository(request.repoPath().toString())
            .commit(repoInfo.headCommit() == null ? "" : repoInfo.headCommit())
            .branch(repoInfo.headBranch())
            .baseCommit(request.baseRef())
            .historyDepth(historyDepth)
            .engines(engineStatuses)
            .baselineProvenance(baselineResolution.provenance().name())
            .build();

    BaselineComparator.ComparisonResult comparison =
        baselineComparator.compare(confidenceAdjustedFindings, baselineResolution.baseline());

    boolean sliceByProject = !request.projects().isEmpty();
    Map<String, List<Finding>> findingsByProject =
        sliceByProject
            ? projectSlicer.slice(comparison.findings(), projectSpecsFor(request.projects()))
            : Map.of();

    PolicyResult policyResult =
        sliceByProject
            ? evaluatePerProject(findingsByProject, policyBundle, request, baselineResolution)
            : applyObserveMode(
                policyEvaluator.evaluate(
                    comparison.findings(), policyBundle, request.mode(), request.failOn()),
                baselineResolution);
    int exitCode =
        policyResult.status() == PolicyStatus.FAILED
            ? ExitCode.POLICY_VIOLATED.code()
            : ExitCode.POLICY_SATISFIED.code();

    ScanResult scanResult =
        new ScanResult(run, comparison.findings(), Map.copyOf(allMetrics), policyResult);

    try {
      jsonReporter.write(scanResult, request.outputDir());
      markdownReporter.write(scanResult, request.outputDir());
      metricsReporter.write(scanResult, request.outputDir());
      if (sliceByProject) {
        sarifReporter.writeMultiProject(findingsByProject, toolVersion, request.outputDir());
      } else {
        sarifReporter.write(scanResult, request.outputDir());
      }
    } catch (ReportWriteException e) {
      return ScanOutcome.ofError(
          ExitCode.INTERNAL_ERROR.code(), "Failed to write outputs: " + e.getMessage());
    }

    return ScanOutcome.ofResult(exitCode, scanResult);
  }

  private List<ProjectSlicer.ProjectSpec> projectSpecsFor(Map<String, String> projects) {
    return projects.entrySet().stream()
        .map(entry -> new ProjectSlicer.ProjectSpec(entry.getKey(), entry.getValue()))
        .toList();
  }

  /**
   * Evaluates the policy bundle once per project, then combines the results into one {@link
   * PolicyResult}: {@link PolicyStatus#FAILED} if any project failed, each violation's rule
   * prefixed with the project it came from so a single combined result stays traceable to its
   * origin project.
   */
  private PolicyResult evaluatePerProject(
      Map<String, List<Finding>> findingsByProject,
      PolicyBundle policyBundle,
      ScanRequest request,
      BaselineResolution baselineResolution) {
    List<PolicyViolation> combinedReasons = new ArrayList<>();
    boolean anyFailed = false;
    boolean anyWouldFail = false;
    for (Map.Entry<String, List<Finding>> entry : findingsByProject.entrySet()) {
      PolicyResult perProject =
          applyObserveMode(
              policyEvaluator.evaluate(
                  entry.getValue(), policyBundle, request.mode(), request.failOn()),
              baselineResolution);
      anyFailed |= perProject.status() == PolicyStatus.FAILED;
      anyWouldFail |= perProject.status() == PolicyStatus.WOULD_FAIL;
      for (PolicyViolation violation : perProject.reasons()) {
        combinedReasons.add(
            new PolicyViolation(
                entry.getKey() + ": " + violation.rule(),
                violation.threshold(),
                violation.actual(),
                violation.findingIds()));
      }
    }
    PolicyStatus combinedStatus =
        anyFailed
            ? PolicyStatus.FAILED
            : anyWouldFail ? PolicyStatus.WOULD_FAIL : PolicyStatus.PASSED;
    return new PolicyResult(policyBundle.version(), combinedStatus, combinedReasons);
  }

  /**
   * Observe mode: no baseline exists yet to gate against, so a first-ever scan reports what
   * enforcement *would* have done without actually failing the build.
   */
  private PolicyResult applyObserveMode(
      PolicyResult evaluated, BaselineResolution baselineResolution) {
    if (evaluated.status() == PolicyStatus.FAILED
        && baselineResolution.provenance() == BaselineProvenance.NONE) {
      return new PolicyResult(
          evaluated.bundleVersion(), PolicyStatus.WOULD_FAIL, evaluated.reasons());
    }
    return evaluated;
  }

  private EngineResult runWithTimeout(
      ExecutorService executor, AnalysisEngine engine, ScanRequest request) {
    long start = clock.millis();
    AnalysisRequest analysisRequest =
        new AnalysisRequest(
            request.repoPath(),
            request.baseRef(),
            request.mode(),
            toEngineTimeoutWindow(request.historyWindow()),
            Map.of(),
            engineTimeout,
            0);
    Future<EngineResult> future =
        executor.submit(() -> engine.analyse(analysisRequest, ProgressSink.NO_OP));
    try {
      return future.get(engineTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      return EngineResult.failed("Engine timed out after " + engineTimeout, clock.millis() - start);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return EngineResult.failed("Engine interrupted", clock.millis() - start);
    } catch (ExecutionException e) {
      String reason = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
      return EngineResult.failed(reason, clock.millis() - start);
    }
  }

  private HistoryDepth mapHistoryDepth(RepositoryInfo info) {
    return info.isShallow() || info.isGrafted() ? HistoryDepth.SHALLOW : HistoryDepth.FULL;
  }

  private Duration toEngineTimeoutWindow(HistoryWindow window) {
    if (window == null || window.since() == null) {
      return null;
    }
    return Duration.between(window.since(), clock.instant());
  }
}
