package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.testkit.JsonSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AC-13: the JSON report file is always present, and always valid against the v1 schema. */
class AC13_OutputPresentAndValidTest {

  @Test
  void ac13_outputFileIsPresentAndValid(@TempDir Path outputDir) throws Exception {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    ScanResult scanResult = new ScanResult(run, List.of(), Map.of(), PolicyResult.passed("b"));

    Path written = new JsonReporter().write(scanResult, outputDir);

    assertThat(Files.exists(written)).isTrue();
    assertThat(JsonSchemaValidator.validate(written, JsonSchemaValidator.DEBT_HUNTER_V1_SCHEMA))
        .isEmpty();

    JsonNode root = new ObjectMapper().readTree(written.toFile());
    assertThat(root.get("schemaVersion").asText()).isEqualTo(JsonReport.CURRENT_SCHEMA_VERSION);
  }
}
