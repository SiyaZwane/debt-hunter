package com.debthunter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaselineArtifactTest {

  private ScanResult scanResult() {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0-SNAPSHOT")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    return new ScanResult(run, List.of(), Map.of(), PolicyResult.passed("unversioned"));
  }

  @Test
  void unsignedTakesTheToolVersionFromTheScanResultsRun() {
    ScanResult scanResult = scanResult();

    BaselineArtifact artifact = BaselineArtifact.unsigned(scanResult);

    assertThat(artifact.schemaVersion()).isEqualTo(BaselineArtifact.CURRENT_SCHEMA_VERSION);
    assertThat(artifact.toolVersion()).isEqualTo("0.1.0-SNAPSHOT");
    assertThat(artifact.scanResult()).isEqualTo(scanResult);
    assertThat(artifact.signature()).isNull();
  }

  @Test
  void withSignatureReturnsACopyWithOnlySignatureChanged() {
    BaselineArtifact unsigned = BaselineArtifact.unsigned(scanResult());

    BaselineArtifact signed = unsigned.withSignature("abc123");

    assertThat(signed.signature()).isEqualTo("abc123");
    assertThat(signed.schemaVersion()).isEqualTo(unsigned.schemaVersion());
    assertThat(signed.toolVersion()).isEqualTo(unsigned.toolVersion());
    assertThat(signed.scanResult()).isEqualTo(unsigned.scanResult());
  }

  @Test
  void requiresNonNullScanResult() {
    assertThatThrownBy(() -> new BaselineArtifact("1.0", "0.1.0", null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("scanResult");
  }
}
