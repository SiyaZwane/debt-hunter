package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.debthunter.application.publish.PublishSummary;
import com.debthunter.application.publish.PublishUseCase;
import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.integration.PublishConfig;
import com.debthunter.output.JsonReporter;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class PublishCommandTest {

  private static final URI ENDPOINT = URI.create("https://example.invalid/results");

  @Test
  void aSuccessfulPublishReturnsThePolicySatisfiedExitCode(@TempDir Path outputDir) {
    Path reportFile = writeReport(outputDir);
    PublishUseCase publishUseCase = mock(PublishUseCase.class);
    when(publishUseCase.publish(any(), any(), org.mockito.ArgumentMatchers.eq(false)))
        .thenReturn(PublishSummary.published());
    PublishCommand command =
        new PublishCommand(reportFile, ENDPOINT, new JsonReporter(), publishUseCase);

    int exitCode = command.call();

    assertThat(exitCode).isZero();
  }

  @Test
  void theEndpointIsForwardedAsThePublishConfig(@TempDir Path outputDir) {
    Path reportFile = writeReport(outputDir);
    PublishUseCase publishUseCase = mock(PublishUseCase.class);
    when(publishUseCase.publish(any(), any(), org.mockito.ArgumentMatchers.eq(false)))
        .thenReturn(PublishSummary.published());
    PublishCommand command =
        new PublishCommand(reportFile, ENDPOINT, new JsonReporter(), publishUseCase);

    command.call();

    ArgumentCaptor<PublishConfig> captor = ArgumentCaptor.forClass(PublishConfig.class);
    verify(publishUseCase)
        .publish(any(ScanResult.class), captor.capture(), org.mockito.ArgumentMatchers.eq(false));
    assertThat(captor.getValue().endpoint()).isEqualTo(ENDPOINT);
  }

  @Test
  void aFailedPublishReturnsTheInternalErrorExitCode(@TempDir Path outputDir) {
    Path reportFile = writeReport(outputDir);
    PublishUseCase publishUseCase = mock(PublishUseCase.class);
    when(publishUseCase.publish(any(), any(), org.mockito.ArgumentMatchers.eq(false)))
        .thenReturn(PublishSummary.failed("connection refused"));
    PublishCommand command =
        new PublishCommand(reportFile, ENDPOINT, new JsonReporter(), publishUseCase);

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(10);
  }

  @Test
  void aMissingReportFileReturnsTheConfigurationErrorExitCode(@TempDir Path outputDir) {
    PublishUseCase publishUseCase = mock(PublishUseCase.class);
    PublishCommand command =
        new PublishCommand(
            outputDir.resolve("missing.json"), ENDPOINT, new JsonReporter(), publishUseCase);

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(2);
  }

  private Path writeReport(Path outputDir) {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0-test")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    ScanResult scanResult =
        new ScanResult(run, List.of(), Map.of(), PolicyResult.passed("unversioned"));
    return new JsonReporter().write(scanResult, outputDir);
  }
}
