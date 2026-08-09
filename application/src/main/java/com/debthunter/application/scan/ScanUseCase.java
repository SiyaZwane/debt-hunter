package com.debthunter.application.scan;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.DebtMetric;
import com.debthunter.domain.EngineStatus;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.PolicyStatus;
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
import com.debthunter.policy.BaselineResolution;
import com.debthunter.policy.BaselineResolver;
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

  /** Internal error: something failed that the caller cannot fix by changing their input. */
  private static final int EXIT_INTERNAL_ERROR = 10;

  /** Configuration error: the target path is not usable as given. */
  private static final int EXIT_CONFIGURATION_ERROR = 2;

  /** Baseline error: an explicit or cached baseline was found but cannot be used. */
  private static final int EXIT_BASELINE_INCOMPATIBLE = 5;

  private static final Duration DEFAULT_ENGINE_TIMEOUT = Duration.ofMinutes(5);

  private final RepositoryHistoryProvider historyProvider;
  private final JsonReporter jsonReporter;
  private final MarkdownReporter markdownReporter;
  private final MetricsReporter metricsReporter;
  private final SarifReporter sarifReporter;
  private final BaselineResolver baselineResolver;
  private final BaselineComparator baselineComparator;
  private final String toolVersion;
  private final Duration engineTimeout;
  private final Clock clock;

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
      String toolVersion) {
    this(
        historyProvider,
        jsonReporter,
        markdownReporter,
        metricsReporter,
        sarifReporter,
        baselineResolver,
        baselineComparator,
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
    this.toolVersion = Objects.requireNonNull(toolVersion, "toolVersion");
    this.engineTimeout = Objects.requireNonNull(engineTimeout, "engineTimeout");
    this.clock = Objects.requireNonNull(clock, "clock");
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
          EXIT_INTERNAL_ERROR, "Failed to inspect repository: " + e.getMessage());
    }

    if (!repoInfo.isGitRepo()) {
      return ScanOutcome.ofError(
          EXIT_CONFIGURATION_ERROR, "Not a Git repository: " + request.repoPath());
    }

    try {
      return analyseAndReport(request, repoInfo);
    } catch (RuntimeException e) {
      return ScanOutcome.ofError(EXIT_INTERNAL_ERROR, "Scan failed: " + e.getMessage());
    }
  }

  private ScanOutcome analyseAndReport(ScanRequest request, RepositoryInfo repoInfo) {
    BaselineResolution baselineResolution =
        baselineResolver.resolve(request.baselinePath(), toolVersion);
    if (baselineResolution.isIncompatible()) {
      return ScanOutcome.ofError(
          EXIT_BASELINE_INCOMPATIBLE, baselineResolution.incompatibilityReason());
    }

    HistoryDepth historyDepth = mapHistoryDepth(repoInfo);
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
        baselineComparator.compare(allFindings, baselineResolution.baseline());

    // Policy evaluation is stubbed until FR-08; every scan currently passes.
    PolicyResult policyResult = PolicyResult.passed("unversioned");

    ScanResult scanResult =
        new ScanResult(run, comparison.findings(), Map.copyOf(allMetrics), policyResult);

    try {
      jsonReporter.write(scanResult, request.outputDir());
      markdownReporter.write(scanResult, request.outputDir());
      metricsReporter.write(scanResult, request.outputDir());
      sarifReporter.write(scanResult, request.outputDir());
    } catch (ReportWriteException e) {
      return ScanOutcome.ofError(EXIT_INTERNAL_ERROR, "Failed to write outputs: " + e.getMessage());
    }

    int exitCode = policyResult.status() == PolicyStatus.PASSED ? 0 : 1;
    return ScanOutcome.ofResult(exitCode, scanResult);
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
