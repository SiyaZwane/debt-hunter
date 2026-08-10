package com.debthunter.application.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.integration.PublishConfig;
import com.debthunter.integration.PublishResult;
import com.debthunter.integration.ResultUploader;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublishUseCaseTest {

  private static final ScanResult RESULT = scanResult();
  private static final PublishConfig CONFIG =
      new PublishConfig(URI.create("https://example.invalid/results"), null, Duration.ofSeconds(5));

  @Test
  void offlineSkipsPublicationEvenWhenConfigured() {
    RecordingUploader uploader = new RecordingUploader(PublishResult.ofSuccess());
    PublishUseCase useCase = new PublishUseCase(uploader);

    PublishSummary summary = useCase.publish(RESULT, CONFIG, true);

    assertThat(summary.outcome()).isEqualTo(PublishOutcome.OFFLINE_SKIPPED);
    assertThat(summary.reason()).isNull();
    assertThat(uploader.invoked).isFalse();
  }

  @Test
  void noConfigIsANoOpWithoutTouchingTheNetwork() {
    RecordingUploader uploader = new RecordingUploader(PublishResult.ofSuccess());
    PublishUseCase useCase = new PublishUseCase(uploader);

    PublishSummary summary = useCase.publish(RESULT, null, false);

    assertThat(summary.outcome()).isEqualTo(PublishOutcome.NOT_CONFIGURED);
    assertThat(summary.reason()).isNull();
    assertThat(uploader.invoked).isFalse();
  }

  @Test
  void offlineTakesPrecedenceOverAMissingConfig() {
    RecordingUploader uploader = new RecordingUploader(PublishResult.ofSuccess());
    PublishUseCase useCase = new PublishUseCase(uploader);

    PublishSummary summary = useCase.publish(RESULT, null, true);

    assertThat(summary.outcome()).isEqualTo(PublishOutcome.OFFLINE_SKIPPED);
    assertThat(uploader.invoked).isFalse();
  }

  @Test
  void aSuccessfulUploadIsReportedAsPublished() {
    RecordingUploader uploader = new RecordingUploader(PublishResult.ofSuccess());
    PublishUseCase useCase = new PublishUseCase(uploader);

    PublishSummary summary = useCase.publish(RESULT, CONFIG, false);

    assertThat(summary.outcome()).isEqualTo(PublishOutcome.PUBLISHED);
    assertThat(summary.reason()).isNull();
    assertThat(uploader.invoked).isTrue();
  }

  @Test
  void aFailedUploadIsReportedAsFailedWithoutThrowing() {
    RecordingUploader uploader =
        new RecordingUploader(PublishResult.ofFailure("connection refused"));
    PublishUseCase useCase = new PublishUseCase(uploader);

    PublishSummary summary = useCase.publish(RESULT, CONFIG, false);

    assertThat(summary.outcome()).isEqualTo(PublishOutcome.FAILED);
    assertThat(summary.reason()).isEqualTo("connection refused");
  }

  @Test
  void everyOutcomeIsPurelyInformationalAndCarriesNoVerdict() {
    RecordingUploader uploader = new RecordingUploader(PublishResult.ofFailure("timeout"));
    PublishUseCase useCase = new PublishUseCase(uploader);

    PublishSummary summary = useCase.publish(RESULT, CONFIG, false);

    // PublishSummary has no exit-code or verdict concept at all: a caller that ignores it
    // entirely still gets the scan's own exit code, unaffected by publish failure.
    assertThat(summary).isInstanceOf(PublishSummary.class);
    assertThat(summary.outcome()).isEqualTo(PublishOutcome.FAILED);
  }

  private static ScanResult scanResult() {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0-test")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    return new ScanResult(run, List.of(), Map.of(), PolicyResult.passed("unversioned"));
  }

  private static final class RecordingUploader implements ResultUploader {
    private final PublishResult toReturn;
    private boolean invoked;

    RecordingUploader(PublishResult toReturn) {
      this.toReturn = toReturn;
    }

    @Override
    public PublishResult publish(ScanResult result, PublishConfig config) {
      invoked = true;
      return toReturn;
    }
  }
}
