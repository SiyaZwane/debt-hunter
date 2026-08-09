package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.BaselineArtifact;
import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaselineWriterTest {

  private final ObjectMapper objectMapper = DeterministicObjectMapper.create();

  @Test
  void unsignedBaselineRoundTripsExactly(@TempDir Path outputDir) throws Exception {
    ScanResult scanResult = scanResult();

    Path written = new BaselineWriter().write(scanResult, outputDir);

    assertThat(written).exists();
    BaselineArtifact readBack = objectMapper.readValue(written.toFile(), BaselineArtifact.class);
    assertThat(readBack.schemaVersion()).isEqualTo(BaselineArtifact.CURRENT_SCHEMA_VERSION);
    assertThat(readBack.toolVersion()).isEqualTo(scanResult.run().toolVersion());
    assertThat(readBack.scanResult()).isEqualTo(scanResult);
    assertThat(readBack.signature()).isNull();
  }

  @Test
  void signedBaselineCarriesAVerifiableSignature(@TempDir Path outputDir) throws Exception {
    ScanResult scanResult = scanResult();

    Path written = new BaselineWriter("shared-secret").write(scanResult, outputDir);

    BaselineArtifact readBack = objectMapper.readValue(written.toFile(), BaselineArtifact.class);
    assertThat(readBack.signature()).isNotBlank();

    BaselineArtifact unsigned =
        new BaselineArtifact(
            readBack.schemaVersion(), readBack.toolVersion(), readBack.scanResult(), null);
    String expectedSignature =
        new BaselineSigner().sign(objectMapper.writeValueAsString(unsigned), "shared-secret");
    assertThat(readBack.signature()).isEqualTo(expectedSignature);
  }

  @Test
  void writesToTheConventionalFileName(@TempDir Path outputDir) {
    Path written = new BaselineWriter().write(scanResult(), outputDir);

    assertThat(written.getFileName().toString()).isEqualTo(BaselineWriter.FILE_NAME);
  }

  private ScanResult scanResult() {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0-SNAPSHOT")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    Finding finding =
        Finding.builder()
            .id("f-1")
            .ruleId("codemaat.churn")
            .category(Category.CHURN)
            .severity(Severity.LOW)
            .path("Foo.java")
            .message("Foo.java has changed 4 times")
            .fingerprint("fp-foo")
            .build();
    return new ScanResult(run, List.of(finding), Map.of(), PolicyResult.passed("unversioned"));
  }
}
