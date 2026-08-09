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

/**
 * AC-22: hotspot rankings, coupling graphs, and knowledge concentration are not single-location
 * "problems" the way SARIF consumers expect, so they never appear in SARIF output — even though
 * they appear in the native JSON report.
 */
class AC22_ExcludedCategoriesTest {

  private final SarifReporter reporter = new SarifReporter();
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void ac22_hotspotCouplingAndKnowledgeConcentrationAreExcludedButOthersAreIncluded(
      @TempDir Path outputDir) throws Exception {
    Finding hotspot = finding("codemaat.hotspot", Category.HOTSPOT, "Foo.java");
    Finding coupling =
        finding("codemaat.temporal-coupling", Category.TEMPORAL_COUPLING, "Foo.java");
    Finding knowledge =
        finding("codemaat.knowledge-concentration", Category.KNOWLEDGE_CONCENTRATION, "Foo.java");
    Finding churn = finding("codemaat.churn", Category.CHURN, "Foo.java");
    Finding staticAnalysis = finding("static.rule", Category.STATIC_ANALYSIS, "Bar.java");

    Path written =
        reporter.write(
            scanResultWith(List.of(hotspot, coupling, knowledge, churn, staticAnalysis)),
            outputDir);

    JsonNode results = json.readTree(written.toFile()).get("runs").get(0).get("results");
    List<String> ruleIds = new java.util.ArrayList<>();
    results.forEach(result -> ruleIds.add(result.get("ruleId").asText()));

    assertThat(ruleIds)
        .doesNotContain(
            "codemaat.hotspot", "codemaat.temporal-coupling", "codemaat.knowledge-concentration")
        .containsExactlyInAnyOrder("codemaat.churn", "static.rule");
  }

  @Test
  void ac22_excludedCategoriesLeaveNoTraceInTheRulesArrayEither(@TempDir Path outputDir)
      throws Exception {
    Finding hotspot = finding("codemaat.hotspot", Category.HOTSPOT, "Foo.java");

    Path written = reporter.write(scanResultWith(List.of(hotspot)), outputDir);

    JsonNode run = json.readTree(written.toFile()).get("runs").get(0);
    assertThat(run.get("results")).isEmpty();
    assertThat(run.get("tool").get("driver").get("rules")).isEmpty();
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

  private Finding finding(String ruleId, Category category, String path) {
    return Finding.builder()
        .id(ruleId + ":" + path)
        .ruleId(ruleId)
        .category(category)
        .severity(Severity.MEDIUM)
        .path(path)
        .startLine(0)
        .message("message")
        .fingerprint("fp-" + ruleId + ":" + path)
        .build();
  }
}
