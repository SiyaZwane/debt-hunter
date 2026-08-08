package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.EngineHealth;
import com.debthunter.domain.EngineStatus;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AC-16: a degraded or failed engine is reflected in both its own status and run.degraded. */
class AC16_DegradedEngineStatusTest {

  private final JsonReporter reporter = new JsonReporter();
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void ac16_degradedEngineSetsRunDegradedAndReportsItsOwnStatusAndReason(@TempDir Path outputDir)
      throws Exception {
    EngineStatus ok = new EngineStatus("engine-a", "1.0", EngineHealth.OK, 10, null);
    EngineStatus degraded =
        new EngineStatus("engine-b", "2.0", EngineHealth.DEGRADED, 20, "partial parse");

    Path written = reporter.write(scanResultWithEngines(List.of(ok, degraded)), outputDir);

    JsonNode root = json.readTree(written.toFile());
    assertThat(root.get("run").get("degraded").asBoolean()).isTrue();
    JsonNode engines = root.get("run").get("engines");
    assertThat(engines).hasSize(2);
    JsonNode degradedNode =
        engines.get(0).get("id").asText().equals("engine-b") ? engines.get(0) : engines.get(1);
    assertThat(degradedNode.get("status").asText()).isEqualTo("DEGRADED");
    assertThat(degradedNode.get("reason").asText()).isEqualTo("partial parse");
  }

  @Test
  void ac16_allEnginesOkLeavesRunNotDegraded(@TempDir Path outputDir) throws Exception {
    EngineStatus ok = new EngineStatus("engine-a", "1.0", EngineHealth.OK, 10, null);

    Path written = reporter.write(scanResultWithEngines(List.of(ok)), outputDir);

    JsonNode root = json.readTree(written.toFile());
    assertThat(root.get("run").get("degraded").asBoolean()).isFalse();
  }

  @Test
  void ac16_failedEngineAlsoSetsRunDegraded(@TempDir Path outputDir) throws Exception {
    EngineStatus failed =
        new EngineStatus("engine-a", "1.0", EngineHealth.FAILED, 5, "binary not found");

    Path written = reporter.write(scanResultWithEngines(List.of(failed)), outputDir);

    JsonNode root = json.readTree(written.toFile());
    assertThat(root.get("run").get("degraded").asBoolean()).isTrue();
  }

  private ScanResult scanResultWithEngines(List<EngineStatus> engines) {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .engines(engines)
            .build();
    return new ScanResult(run, List.of(), Map.of(), PolicyResult.passed("b"));
  }
}
