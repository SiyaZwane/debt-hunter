package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.DebtMetric;
import com.debthunter.domain.EngineHealth;
import com.debthunter.domain.EngineStatus;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.PolicyStatus;
import com.debthunter.domain.PolicyViolation;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.debthunter.testkit.JsonSchemaValidator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Every shape of {@code debt-hunter.json} this reporter can produce must validate against v1. */
class JsonSchemaValidationTest {

  private final JsonReporter reporter = new JsonReporter();

  @Test
  void minimalScanResultValidates(@TempDir Path outputDir) {
    Path written = reporter.write(scanResultWith(List.of(), List.of(), List.of()), outputDir);

    assertThat(JsonSchemaValidator.validate(written, JsonSchemaValidator.DEBT_HUNTER_V1_SCHEMA))
        .isEmpty();
  }

  @Test
  void scanResultWithFindingsMetricsAndEnginesValidates(@TempDir Path outputDir) {
    Finding finding =
        Finding.builder()
            .id("f-1")
            .ruleId("rule")
            .category(Category.HOTSPOT)
            .severity(Severity.HIGH)
            .confidence(0.8)
            .path("Foo.java")
            .startLine(10)
            .message("message")
            .evidence(Map.of("changeFrequency", 5.0, "authors", List.of("a", "b")))
            .score(5.0)
            .isNew(true)
            .fingerprint("fp-1")
            .build();
    DebtMetric metric = new DebtMetric("churn", 5.0, "repo");
    EngineStatus engine =
        new EngineStatus("code-maat", "1.0", EngineHealth.DEGRADED, 42, "partial");

    Path written =
        reporter.write(
            scanResultWith(List.of(finding), List.of(metric), List.of(engine)), outputDir);

    assertThat(JsonSchemaValidator.validate(written, JsonSchemaValidator.DEBT_HUNTER_V1_SCHEMA))
        .isEmpty();
  }

  @Test
  void scanResultWithFailedPolicyAndReasonsValidates(@TempDir Path outputDir) {
    AnalysisRun run = runBuilder(List.of()).build();
    PolicyResult failed =
        new PolicyResult(
            "bundle-1",
            PolicyStatus.FAILED,
            List.of(new PolicyViolation("no-critical", "0", "1", List.of("f-1"))));
    ScanResult scanResult = new ScanResult(run, List.of(), Map.of(), failed);

    Path written = reporter.write(scanResult, outputDir);

    assertThat(JsonSchemaValidator.validate(written, JsonSchemaValidator.DEBT_HUNTER_V1_SCHEMA))
        .isEmpty();
  }

  private ScanResult scanResultWith(
      List<Finding> findings, List<DebtMetric> metrics, List<EngineStatus> engines) {
    AnalysisRun run = runBuilder(engines).build();
    Map<String, DebtMetric> metricsByName =
        metrics.stream().collect(java.util.stream.Collectors.toMap(DebtMetric::name, m -> m));
    return new ScanResult(run, findings, metricsByName, PolicyResult.passed("bundle-1"));
  }

  private AnalysisRun.Builder runBuilder(List<EngineStatus> engines) {
    return AnalysisRun.builder()
        .id("run-1")
        .toolVersion("0.1.0")
        .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
        .repository("/repo")
        .commit("abc123")
        .historyDepth(HistoryDepth.FULL)
        .engines(engines);
  }
}
