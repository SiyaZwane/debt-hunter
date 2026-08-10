package com.debthunter.cli;

import com.debthunter.ai.ExplainConfig;
import com.debthunter.ai.Explainer;
import com.debthunter.ai.Explanation;
import com.debthunter.ai.HttpExplainer;
import com.debthunter.application.scan.ExitCode;
import com.debthunter.domain.Finding;
import com.debthunter.domain.ScanResult;
import com.debthunter.output.JsonReporter;
import com.debthunter.output.ReportReadException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Explains a single finding from a previously written scan result, using an external AI service.
 * Entirely separate from {@code scan}: this is exploratory, developer-facing tooling, not part of
 * the blocking analysis path, and its outcome never affects a scan's exit code.
 */
@Command(name = "explain", description = "Explain a single finding using an AI service.")
public final class ExplainCommand implements Callable<Integer> {

  @Option(
      names = "--report",
      required = true,
      description = "Path to a previously written debt-hunter.json.")
  private Path reportPath;

  @Option(names = "--finding-id", required = true, description = "Id of the finding to explain.")
  private String findingId;

  @Option(
      names = "--explain-endpoint",
      required = true,
      description = "AI service endpoint to request the explanation from.")
  private URI explainEndpoint;

  @Option(names = "--explain-api-key", description = "Bearer API key for --explain-endpoint.")
  private String explainApiKey;

  @Option(names = "--explain-timeout", description = "Timeout for the explain request, e.g. PT30S.")
  private Duration explainTimeout = Duration.ofSeconds(30);

  private final JsonReporter jsonReporter;
  private final Explainer explainer;

  /** Constructs the command as picocli will: with the default, production collaborators. */
  public ExplainCommand() {
    this(new JsonReporter(), new HttpExplainer());
  }

  /**
   * Constructs the command with explicit collaborators, bypassing picocli option parsing entirely,
   * for testing.
   *
   * @param jsonReporter reads the previously written report
   * @param explainer requests the explanation
   */
  ExplainCommand(JsonReporter jsonReporter, Explainer explainer) {
    this.jsonReporter = jsonReporter;
    this.explainer = explainer;
  }

  /**
   * Constructs the command with explicit collaborators and target options, bypassing picocli option
   * parsing entirely, for testing.
   *
   * @param reportPath path to a previously written debt-hunter.json
   * @param findingId id of the finding to explain
   * @param explainEndpoint AI service endpoint to request the explanation from
   * @param jsonReporter reads the previously written report
   * @param explainer requests the explanation
   */
  ExplainCommand(
      Path reportPath,
      String findingId,
      URI explainEndpoint,
      JsonReporter jsonReporter,
      Explainer explainer) {
    this(jsonReporter, explainer);
    this.reportPath = reportPath;
    this.findingId = findingId;
    this.explainEndpoint = explainEndpoint;
  }

  @Override
  public Integer call() {
    ScanResult scanResult;
    try {
      scanResult = jsonReporter.read(reportPath);
    } catch (ReportReadException e) {
      System.err.println("Failed to read " + reportPath + ": " + e.getMessage());
      return ExitCode.CONFIGURATION_ERROR.code();
    }

    Optional<Finding> finding =
        scanResult.findings().stream().filter(f -> f.id().equals(findingId)).findFirst();
    if (finding.isEmpty()) {
      System.err.println("No finding with id " + findingId + " in " + reportPath);
      return ExitCode.CONFIGURATION_ERROR.code();
    }

    ExplainConfig config = new ExplainConfig(explainEndpoint, explainApiKey, explainTimeout);
    Explanation explanation = explainer.explain(finding.get(), config);
    if (explanation.available()) {
      System.out.println(explanation.text());
    } else {
      System.out.println("Explanation unavailable: " + explanation.text());
    }
    return ExitCode.POLICY_SATISFIED.code();
  }
}
