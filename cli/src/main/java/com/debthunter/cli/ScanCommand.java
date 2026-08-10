package com.debthunter.cli;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.publish.PublishOutcome;
import com.debthunter.application.publish.PublishSummary;
import com.debthunter.application.publish.PublishUseCase;
import com.debthunter.application.scan.ScanOutcome;
import com.debthunter.application.scan.ScanRequest;
import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.debthunter.engine.spi.AnalysisEngine;
import com.debthunter.engine.spi.AnalysisMode;
import com.debthunter.integration.HttpResultUploader;
import com.debthunter.integration.PublishConfig;
import com.debthunter.output.JsonReporter;
import com.debthunter.output.MarkdownReporter;
import com.debthunter.output.MetricsReporter;
import com.debthunter.output.SarifReporter;
import com.debthunter.policy.BaselineComparator;
import com.debthunter.policy.BaselineResolver;
import com.debthunter.policy.PolicyBundleParser;
import com.debthunter.policy.PolicyEvaluator;
import com.debthunter.repository.GitHistoryProvider;
import com.debthunter.repository.HistoryWindow;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Runs a scan against a checked-out Git repository and writes the report files. */
@Command(name = "scan", description = "Analyse a Git repository and report technical debt.")
public final class ScanCommand implements Callable<Integer> {

  /** This build's version, recorded in every scan's {@code run} metadata. */
  static final String TOOL_VERSION = "0.1.0-SNAPSHOT";

  @Option(names = "--repo", description = "Path to the repository to scan.")
  private Path repoPath = Path.of(".");

  @Option(
      names = {"--output-dir", "--out"},
      required = true,
      description = "Directory to write report files into.")
  private Path outputDir;

  @Option(names = "--base-ref", description = "Base ref to compare against, for pull-request mode.")
  private String baseRef;

  @Option(names = "--mode", description = "Analysis mode: FULL or PULL_REQUEST.")
  private AnalysisMode mode = AnalysisMode.FULL;

  @Option(names = "--policy", description = "Path to a policy bundle file.")
  private Path policyPath;

  @Option(names = "--offline", description = "Skip any network-dependent steps.")
  private boolean offline;

  @Option(
      names = "--fail-on",
      description =
          "Severity threshold that should fail the build, in addition to any configured policy"
              + " (e.g. --fail-on HIGH).")
  private Severity failOn;

  @Option(
      names = "--history-window-since",
      description = "Only consider commits at or after this ISO-8601 instant.")
  private Instant historyWindowSince;

  @Option(names = "--baseline", description = "Path to a baseline artefact to compare against.")
  private Path baselinePath;

  @Option(names = "--publish-endpoint", description = "Endpoint to publish the scan result to.")
  private URI publishEndpoint;

  @Option(names = "--publish-api-key", description = "Bearer API key for --publish-endpoint.")
  private String publishApiKey;

  @Option(names = "--publish-timeout", description = "Timeout for the publish request, e.g. PT30S.")
  private Duration publishTimeout = Duration.ofSeconds(30);

  @Option(
      names = "--project",
      description =
          "Repeatable name=pathPrefixOrGlob mapping for monorepo project slicing, e.g."
              + " --project frontend=frontend/**.")
  private Map<String, String> projects = new LinkedHashMap<>();

  private final ScanUseCase scanUseCase;
  private final List<AnalysisEngine> engines;
  private final PublishUseCase publishUseCase;
  private final MarkdownReporter markdownReporter;

  /** Constructs the command as picocli will: with the default, production collaborators. */
  public ScanCommand() {
    this.scanUseCase = defaultScanUseCase();
    this.engines = List.of();
    this.publishUseCase = defaultPublishUseCase();
    this.markdownReporter = new MarkdownReporter();
  }

  /**
   * Constructs the command with explicit collaborators and target paths, bypassing picocli option
   * parsing entirely, for testing.
   *
   * @param repoPath repository path to scan; defaults to {@code "."} if {@code null}
   * @param outputDir directory to write report files into
   * @param scanUseCase the use case to delegate to
   * @param engines the analysis engines to run
   */
  ScanCommand(
      Path repoPath, Path outputDir, ScanUseCase scanUseCase, List<AnalysisEngine> engines) {
    this(repoPath, outputDir, null, scanUseCase, engines);
  }

  /**
   * Constructs the command with explicit collaborators, target paths, and a baseline path,
   * bypassing picocli option parsing entirely, for testing.
   *
   * @param repoPath repository path to scan; defaults to {@code "."} if {@code null}
   * @param outputDir directory to write report files into
   * @param baselinePath path to a baseline artefact to compare against, or {@code null}
   * @param scanUseCase the use case to delegate to
   * @param engines the analysis engines to run
   */
  ScanCommand(
      Path repoPath,
      Path outputDir,
      Path baselinePath,
      ScanUseCase scanUseCase,
      List<AnalysisEngine> engines) {
    this(repoPath, outputDir, null, baselinePath, scanUseCase, engines);
  }

  /**
   * Constructs the command with explicit collaborators, target paths, a policy path, and a baseline
   * path, bypassing picocli option parsing entirely, for testing.
   *
   * @param repoPath repository path to scan; defaults to {@code "."} if {@code null}
   * @param outputDir directory to write report files into
   * @param policyPath path to a policy bundle file, or {@code null}
   * @param baselinePath path to a baseline artefact to compare against, or {@code null}
   * @param scanUseCase the use case to delegate to
   * @param engines the analysis engines to run
   */
  ScanCommand(
      Path repoPath,
      Path outputDir,
      Path policyPath,
      Path baselinePath,
      ScanUseCase scanUseCase,
      List<AnalysisEngine> engines) {
    this(
        repoPath,
        outputDir,
        policyPath,
        baselinePath,
        scanUseCase,
        engines,
        defaultPublishUseCase(),
        null,
        false);
  }

  /**
   * Constructs the command with every collaborator and option explicit, bypassing picocli option
   * parsing entirely, for testing publication behaviour.
   *
   * @param repoPath repository path to scan; defaults to {@code "."} if {@code null}
   * @param outputDir directory to write report files into
   * @param policyPath path to a policy bundle file, or {@code null}
   * @param baselinePath path to a baseline artefact to compare against, or {@code null}
   * @param scanUseCase the use case to delegate to
   * @param engines the analysis engines to run
   * @param publishUseCase the use case to delegate optional publication to
   * @param publishEndpoint where to publish the scan result, or {@code null} if not configured
   * @param offline whether to skip publication entirely
   */
  ScanCommand(
      Path repoPath,
      Path outputDir,
      Path policyPath,
      Path baselinePath,
      ScanUseCase scanUseCase,
      List<AnalysisEngine> engines,
      PublishUseCase publishUseCase,
      URI publishEndpoint,
      boolean offline) {
    this(
        repoPath,
        outputDir,
        policyPath,
        baselinePath,
        scanUseCase,
        engines,
        publishUseCase,
        publishEndpoint,
        offline,
        Map.of());
  }

  /**
   * Constructs the command with every collaborator and option explicit, including monorepo project
   * slicing, bypassing picocli option parsing entirely, for testing.
   *
   * @param repoPath repository path to scan; defaults to {@code "."} if {@code null}
   * @param outputDir directory to write report files into
   * @param policyPath path to a policy bundle file, or {@code null}
   * @param baselinePath path to a baseline artefact to compare against, or {@code null}
   * @param scanUseCase the use case to delegate to
   * @param engines the analysis engines to run
   * @param publishUseCase the use case to delegate optional publication to
   * @param publishEndpoint where to publish the scan result, or {@code null} if not configured
   * @param offline whether to skip publication entirely
   * @param projects repeatable name-to-pattern mapping for monorepo project slicing
   */
  ScanCommand(
      Path repoPath,
      Path outputDir,
      Path policyPath,
      Path baselinePath,
      ScanUseCase scanUseCase,
      List<AnalysisEngine> engines,
      PublishUseCase publishUseCase,
      URI publishEndpoint,
      boolean offline,
      Map<String, String> projects) {
    this(
        repoPath,
        outputDir,
        policyPath,
        baselinePath,
        scanUseCase,
        engines,
        publishUseCase,
        publishEndpoint,
        offline,
        projects,
        null);
  }

  /**
   * Constructs the command with every collaborator and option explicit, including monorepo project
   * slicing and a {@code --fail-on} override, bypassing picocli option parsing entirely, for
   * testing.
   *
   * @param repoPath repository path to scan; defaults to {@code "."} if {@code null}
   * @param outputDir directory to write report files into
   * @param policyPath path to a policy bundle file, or {@code null}
   * @param baselinePath path to a baseline artefact to compare against, or {@code null}
   * @param scanUseCase the use case to delegate to
   * @param engines the analysis engines to run
   * @param publishUseCase the use case to delegate optional publication to
   * @param publishEndpoint where to publish the scan result, or {@code null} if not configured
   * @param offline whether to skip publication entirely
   * @param projects repeatable name-to-pattern mapping for monorepo project slicing
   * @param failOn severity threshold that should fail the build, or {@code null}
   */
  ScanCommand(
      Path repoPath,
      Path outputDir,
      Path policyPath,
      Path baselinePath,
      ScanUseCase scanUseCase,
      List<AnalysisEngine> engines,
      PublishUseCase publishUseCase,
      URI publishEndpoint,
      boolean offline,
      Map<String, String> projects,
      Severity failOn) {
    this(
        repoPath,
        outputDir,
        policyPath,
        baselinePath,
        scanUseCase,
        engines,
        publishUseCase,
        publishEndpoint,
        offline,
        projects,
        failOn,
        null);
  }

  /**
   * Constructs the command with every collaborator and option explicit, including a {@code
   * --history-window-since} bound, bypassing picocli option parsing entirely, for testing.
   *
   * @param repoPath repository path to scan; defaults to {@code "."} if {@code null}
   * @param outputDir directory to write report files into
   * @param policyPath path to a policy bundle file, or {@code null}
   * @param baselinePath path to a baseline artefact to compare against, or {@code null}
   * @param scanUseCase the use case to delegate to
   * @param engines the analysis engines to run
   * @param publishUseCase the use case to delegate optional publication to
   * @param publishEndpoint where to publish the scan result, or {@code null} if not configured
   * @param offline whether to skip publication entirely
   * @param projects repeatable name-to-pattern mapping for monorepo project slicing
   * @param failOn severity threshold that should fail the build, or {@code null}
   * @param historyWindowSince only consider commits at or after this instant, or {@code null}
   */
  ScanCommand(
      Path repoPath,
      Path outputDir,
      Path policyPath,
      Path baselinePath,
      ScanUseCase scanUseCase,
      List<AnalysisEngine> engines,
      PublishUseCase publishUseCase,
      URI publishEndpoint,
      boolean offline,
      Map<String, String> projects,
      Severity failOn,
      Instant historyWindowSince) {
    this.repoPath = repoPath == null ? Path.of(".") : repoPath;
    this.outputDir = outputDir;
    this.policyPath = policyPath;
    this.baselinePath = baselinePath;
    this.scanUseCase = scanUseCase;
    this.engines = engines;
    this.publishUseCase = publishUseCase;
    this.publishEndpoint = publishEndpoint;
    this.offline = offline;
    this.projects = projects;
    this.failOn = failOn;
    this.historyWindowSince = historyWindowSince;
    this.markdownReporter = new MarkdownReporter();
  }

  private static PublishUseCase defaultPublishUseCase() {
    return new PublishUseCase(new HttpResultUploader());
  }

  private static ScanUseCase defaultScanUseCase() {
    return new ScanUseCase(
        new GitHistoryProvider(),
        new JsonReporter(),
        new MarkdownReporter(),
        new MetricsReporter(),
        new SarifReporter(),
        new BaselineResolver(),
        new BaselineComparator(),
        new PolicyBundleParser(),
        new PolicyEvaluator(),
        new HistoryDepthEnforcer(),
        TOOL_VERSION);
  }

  @Override
  public Integer call() {
    ScanRequest request =
        new ScanRequest(
            repoPath,
            outputDir,
            mode,
            baseRef,
            policyPath,
            engines,
            historyWindowSince == null ? null : HistoryWindow.since(historyWindowSince),
            offline,
            failOn,
            baselinePath,
            projects);

    ScanOutcome outcome = scanUseCase.execute(request);

    if (outcome.scanResult() == null && outcome.diagnosticMessage() != null) {
      System.err.println(outcome.diagnosticMessage());
    } else if (outcome.scanResult() != null) {
      publish(outcome.scanResult());
    }

    return outcome.exitCode();
  }

  /**
   * Optionally publishes the completed result. Publication can never change the exit code already
   * decided by the scan itself: a failure here is only surfaced as a warning appended to {@code
   * summary.md}.
   */
  private void publish(ScanResult scanResult) {
    PublishConfig publishConfig =
        publishEndpoint == null
            ? null
            : new PublishConfig(publishEndpoint, publishApiKey, publishTimeout);
    PublishSummary summary = publishUseCase.publish(scanResult, publishConfig, offline);
    if (summary.outcome() == PublishOutcome.FAILED) {
      markdownReporter.appendPublishFailureWarning(outputDir, summary.reason());
    }
  }
}
