package com.debthunter.cli;

import com.debthunter.application.publish.PublishOutcome;
import com.debthunter.application.publish.PublishSummary;
import com.debthunter.application.publish.PublishUseCase;
import com.debthunter.application.scan.ExitCode;
import com.debthunter.domain.ScanResult;
import com.debthunter.integration.HttpResultUploader;
import com.debthunter.integration.PublishConfig;
import com.debthunter.output.JsonReporter;
import com.debthunter.output.ReportReadException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Publishes a previously written {@code debt-hunter.json}, for manual or deferred publication
 * outside of the scan that produced it.
 */
@Command(name = "publish", description = "Publish a previously written scan result.")
public final class PublishCommand implements Callable<Integer> {

  @Option(
      names = "--report",
      required = true,
      description = "Path to a previously written debt-hunter.json.")
  private Path reportPath;

  @Option(
      names = "--publish-endpoint",
      required = true,
      description = "Endpoint to publish the scan result to.")
  private URI publishEndpoint;

  @Option(names = "--publish-api-key", description = "Bearer API key for --publish-endpoint.")
  private String publishApiKey;

  @Option(names = "--publish-timeout", description = "Timeout for the publish request, e.g. PT30S.")
  private Duration publishTimeout = Duration.ofSeconds(30);

  private final JsonReporter jsonReporter;
  private final PublishUseCase publishUseCase;

  /** Constructs the command as picocli will: with the default, production collaborators. */
  public PublishCommand() {
    this(new JsonReporter(), new PublishUseCase(new HttpResultUploader()));
  }

  /**
   * Constructs the command with explicit collaborators, bypassing picocli option parsing entirely,
   * for testing.
   *
   * @param jsonReporter reads the previously written report
   * @param publishUseCase publishes it
   */
  PublishCommand(JsonReporter jsonReporter, PublishUseCase publishUseCase) {
    this.jsonReporter = jsonReporter;
    this.publishUseCase = publishUseCase;
  }

  /**
   * Constructs the command with explicit collaborators and target options, bypassing picocli option
   * parsing entirely, for testing.
   *
   * @param reportPath path to a previously written debt-hunter.json
   * @param publishEndpoint endpoint to publish the scan result to
   * @param jsonReporter reads the previously written report
   * @param publishUseCase publishes it
   */
  PublishCommand(
      Path reportPath,
      URI publishEndpoint,
      JsonReporter jsonReporter,
      PublishUseCase publishUseCase) {
    this(jsonReporter, publishUseCase);
    this.reportPath = reportPath;
    this.publishEndpoint = publishEndpoint;
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

    PublishConfig config = new PublishConfig(publishEndpoint, publishApiKey, publishTimeout);
    PublishSummary summary = publishUseCase.publish(scanResult, config, false);
    if (summary.outcome() == PublishOutcome.PUBLISHED) {
      System.out.println("Published " + reportPath + " to " + publishEndpoint);
      return ExitCode.POLICY_SATISFIED.code();
    }

    System.err.println("Failed to publish " + reportPath + ": " + summary.reason());
    return ExitCode.INTERNAL_ERROR.code();
  }
}
