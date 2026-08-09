package com.debthunter.cli;

import com.debthunter.application.scan.ScanOutcome;
import com.debthunter.application.scan.ScanRequest;
import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.engine.spi.AnalysisEngine;
import com.debthunter.engine.spi.AnalysisMode;
import com.debthunter.output.JsonReporter;
import com.debthunter.output.MarkdownReporter;
import com.debthunter.output.MetricsReporter;
import com.debthunter.output.SarifReporter;
import com.debthunter.repository.GitHistoryProvider;
import com.debthunter.repository.HistoryWindow;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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

  @Option(names = "--fail-on", description = "Severity threshold that should fail the build.")
  private String failOn;

  @Option(
      names = "--history-window-since",
      description = "Only consider commits at or after this ISO-8601 instant.")
  private Instant historyWindowSince;

  private final ScanUseCase scanUseCase;
  private final List<AnalysisEngine> engines;

  /** Constructs the command as picocli will: with the default, production collaborators. */
  public ScanCommand() {
    this.scanUseCase = defaultScanUseCase();
    this.engines = List.of();
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
    this.repoPath = repoPath == null ? Path.of(".") : repoPath;
    this.outputDir = outputDir;
    this.scanUseCase = scanUseCase;
    this.engines = engines;
  }

  private static ScanUseCase defaultScanUseCase() {
    return new ScanUseCase(
        new GitHistoryProvider(),
        new JsonReporter(),
        new MarkdownReporter(),
        new MetricsReporter(),
        new SarifReporter(),
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
            failOn);

    ScanOutcome outcome = scanUseCase.execute(request);

    if (outcome.scanResult() == null && outcome.diagnosticMessage() != null) {
      System.err.println(outcome.diagnosticMessage());
    }

    return outcome.exitCode();
  }
}
