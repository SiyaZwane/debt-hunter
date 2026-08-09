package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SarifReporterTest {

  private final SarifReporter reporter = new SarifReporter();
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void writesTopLevelSarifStructure(@TempDir Path outputDir) throws Exception {
    Path written = reporter.write(scanResultWith(List.of()), outputDir);

    JsonNode root = json.readTree(written.toFile());
    assertThat(root.get("version").asText()).isEqualTo("2.1.0");
    assertThat(root.has("$schema")).isTrue();
    assertThat(root.get("runs")).hasSize(1);
  }

  @Test
  void runHasToolDriverWithNameAndVersion(@TempDir Path outputDir) throws Exception {
    Path written = reporter.write(scanResultWith(List.of()), outputDir);

    JsonNode driver = json.readTree(written.toFile()).get("runs").get(0).get("tool").get("driver");
    assertThat(driver.get("name").asText()).isEqualTo("Debt Hunter");
    assertThat(driver.get("version").asText()).isEqualTo("0.1.0");
  }

  @Test
  void resultsCarryRuleIdLevelMessageLocationAndFingerprint(@TempDir Path outputDir)
      throws Exception {
    Finding finding =
        finding("static.rule", Category.STATIC_ANALYSIS, Severity.HIGH, "Foo.java", 10);

    Path written = reporter.write(scanResultWith(List.of(finding)), outputDir);

    JsonNode result = json.readTree(written.toFile()).get("runs").get(0).get("results").get(0);
    assertThat(result.get("ruleId").asText()).isEqualTo("static.rule");
    assertThat(result.get("level").asText()).isEqualTo("error");
    assertThat(result.get("message").get("text").asText()).isEqualTo("message");
    assertThat(
            result
                .get("locations")
                .get(0)
                .get("physicalLocation")
                .get("artifactLocation")
                .get("uri")
                .asText())
        .isEqualTo("Foo.java");
    assertThat(
            result
                .get("locations")
                .get(0)
                .get("physicalLocation")
                .get("region")
                .get("startLine")
                .asInt())
        .isEqualTo(10);
    assertThat(result.get("partialFingerprints").get("debtHunter/v1").asText())
        .isEqualTo(finding.fingerprint());
  }

  @Test
  void findingsWithNoSpecificLineOmitRegionEntirely(@TempDir Path outputDir) throws Exception {
    Finding fileLevelFinding =
        finding("codemaat.churn", Category.CHURN, Severity.MEDIUM, "Foo.java", 0);

    Path written = reporter.write(scanResultWith(List.of(fileLevelFinding)), outputDir);

    JsonNode physicalLocation =
        json.readTree(written.toFile())
            .get("runs")
            .get(0)
            .get("results")
            .get(0)
            .get("locations")
            .get(0)
            .get("physicalLocation");
    assertThat(physicalLocation.has("region")).isFalse();
    assertThat(physicalLocation.get("artifactLocation").get("uri").asText()).isEqualTo("Foo.java");
  }

  @Test
  void rulesArrayHasOneEntryPerDistinctRuleIdNotPerFinding(@TempDir Path outputDir)
      throws Exception {
    Finding first = finding("static.rule", Category.STATIC_ANALYSIS, Severity.LOW, "A.java", 1);
    Finding second = finding("static.rule", Category.STATIC_ANALYSIS, Severity.LOW, "B.java", 2);

    Path written = reporter.write(scanResultWith(List.of(first, second)), outputDir);

    JsonNode rules =
        json.readTree(written.toFile()).get("runs").get(0).get("tool").get("driver").get("rules");
    assertThat(rules).hasSize(1);
    assertThat(rules.get(0).get("id").asText()).isEqualTo("static.rule");
  }

  @Test
  void automationDetailsIdIsStableForTheSameProject(@TempDir Path outputDir) throws Exception {
    Path written1 = reporter.write(scanResultWith(List.of()), outputDir.resolve("run1"));
    Path written2 = reporter.write(scanResultWith(List.of()), outputDir.resolve("run2"));

    String id1 =
        json.readTree(written1.toFile())
            .get("runs")
            .get(0)
            .get("automationDetails")
            .get("id")
            .asText();
    String id2 =
        json.readTree(written2.toFile())
            .get("runs")
            .get(0)
            .get("automationDetails")
            .get("id")
            .asText();
    assertThat(id1).isEqualTo(id2).isEqualTo("debt-hunter/");
  }

  private ScanResult scanResultWith(List<Finding> findings) {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    return new ScanResult(run, findings, Map.of(), PolicyResult.passed("b"));
  }

  private Finding finding(
      String ruleId, Category category, Severity severity, String path, int startLine) {
    return Finding.builder()
        .id(ruleId + ":" + path)
        .ruleId(ruleId)
        .category(category)
        .severity(severity)
        .path(path)
        .startLine(startLine)
        .message("message")
        .fingerprint("fp-" + ruleId + ":" + path)
        .build();
  }
}
