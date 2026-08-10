package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.DebtMetric;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonReporterTest {

  private final JsonReporter reporter = new JsonReporter();
  private final ObjectMapper reader = new ObjectMapper();

  @Test
  void writesJsonWithTopLevelSectionsForRunFindingsMetricsAndPolicy(@TempDir Path outputDir)
      throws IOException {
    Path written = reporter.write(sampleScanResult(List.of()), outputDir);

    assertThat(written).exists();
    JsonNode root = reader.readTree(written.toFile());
    assertThat(root.has("run")).isTrue();
    assertThat(root.has("findings")).isTrue();
    assertThat(root.has("metrics")).isTrue();
    assertThat(root.has("policy")).isTrue();
  }

  @Test
  void findingsAreWrittenSortedByRuleIdThenPathThenStartLine(@TempDir Path outputDir)
      throws IOException {
    Finding zRule = finding("z-rule", "b.java", 10);
    Finding aRuleB = finding("a-rule", "b.java", 5);
    Finding aRuleA = finding("a-rule", "a.java", 1);

    Path written = reporter.write(sampleScanResult(List.of(zRule, aRuleB, aRuleA)), outputDir);

    JsonNode findings = reader.readTree(written.toFile()).get("findings");
    assertThat(findings.get(0).get("ruleId").asText()).isEqualTo("a-rule");
    assertThat(findings.get(0).get("path").asText()).isEqualTo("a.java");
    assertThat(findings.get(1).get("ruleId").asText()).isEqualTo("a-rule");
    assertThat(findings.get(1).get("path").asText()).isEqualTo("b.java");
    assertThat(findings.get(2).get("ruleId").asText()).isEqualTo("z-rule");
  }

  @Test
  void writingTheSameScanResultTwiceProducesByteIdenticalFiles(@TempDir Path outputDir)
      throws IOException {
    ScanResult scanResult = sampleScanResult(List.of(finding("rule", "a.java", 1)));

    Path first = reporter.write(scanResult, outputDir.resolve("run1"));
    Path second = reporter.write(scanResult, outputDir.resolve("run2"));

    assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
  }

  @Test
  void writesSchemaVersionField(@TempDir Path outputDir) throws IOException {
    Path written = reporter.write(sampleScanResult(List.of()), outputDir);

    JsonNode root = reader.readTree(written.toFile());
    assertThat(root.get("schemaVersion").asText()).isEqualTo(JsonReport.CURRENT_SCHEMA_VERSION);
  }

  @Test
  void rejectsAFindingWithABlankRuleIdInsteadOfWritingAnIncompleteReport(@TempDir Path outputDir) {
    Finding blankRuleId =
        Finding.builder()
            .id("f-1")
            .ruleId("  ")
            .category(Category.HOTSPOT)
            .severity(Severity.LOW)
            .path("Foo.java")
            .message("message")
            .fingerprint("fp-1")
            .build();

    assertThatThrownBy(() -> reporter.write(sampleScanResult(List.of(blankRuleId)), outputDir))
        .isInstanceOf(ReportWriteException.class)
        .hasMessageContaining("ruleId");
    assertThat(outputDir.resolve(JsonReporter.FILE_NAME)).doesNotExist();
  }

  @Test
  void mapKeysAreOrderedAlphabetically(@TempDir Path outputDir) throws IOException {
    ScanResult scanResult =
        new ScanResult(
            sampleRun(List.of()),
            List.of(),
            Map.of(
                "zebra", new DebtMetric("zebra", 1, "repo"),
                "alpha", new DebtMetric("alpha", 2, "repo")),
            PolicyResult.passed("bundle-1"));

    Path written = reporter.write(scanResult, outputDir);

    String content = Files.readString(written);
    assertThat(content.indexOf("\"alpha\"")).isLessThan(content.indexOf("\"zebra\""));
  }

  @Test
  void readReturnsAScanResultEquivalentToWhatWasWritten(@TempDir Path outputDir) {
    ScanResult original = sampleScanResult(List.of(finding("rule", "a.java", 1)));
    Path written = reporter.write(original, outputDir);

    ScanResult readBack = reporter.read(written);

    assertThat(readBack.findings()).isEqualTo(original.findings());
    assertThat(readBack.run().id()).isEqualTo(original.run().id());
    assertThat(readBack.policy().status()).isEqualTo(original.policy().status());
  }

  @Test
  void readOfAMissingFileThrowsAReportReadException(@TempDir Path outputDir) {
    assertThatThrownBy(() -> reporter.read(outputDir.resolve("missing.json")))
        .isInstanceOf(ReportReadException.class);
  }

  private ScanResult sampleScanResult(List<Finding> findings) {
    return new ScanResult(
        sampleRun(List.of()), findings, Map.of(), PolicyResult.passed("bundle-1"));
  }

  private AnalysisRun sampleRun(List<com.debthunter.domain.EngineStatus> engines) {
    return AnalysisRun.builder()
        .id("run-1")
        .toolVersion("0.1.0")
        .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
        .repository("/repo")
        .commit("abc123")
        .historyDepth(HistoryDepth.FULL)
        .engines(engines)
        .build();
  }

  private Finding finding(String ruleId, String path, int startLine) {
    return Finding.builder()
        .id(ruleId + "-" + path + "-" + startLine)
        .ruleId(ruleId)
        .category(Category.HOTSPOT)
        .severity(Severity.MEDIUM)
        .path(path)
        .startLine(startLine)
        .message("message")
        .fingerprint("fp-" + ruleId + "-" + path + "-" + startLine)
        .build();
  }
}
