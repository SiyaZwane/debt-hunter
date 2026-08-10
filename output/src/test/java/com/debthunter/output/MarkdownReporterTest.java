package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.EngineHealth;
import com.debthunter.domain.EngineStatus;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownReporterTest {

  private final MarkdownReporter reporter = new MarkdownReporter();

  @Test
  void rendersHeadingVerdictAndCommit() {
    ScanResult scanResult = scanResultWith(List.of(), List.of());

    String markdown = reporter.render(scanResult);

    assertThat(markdown).startsWith("# Debt Hunter Summary");
    assertThat(markdown).contains("**Verdict:** PASSED");
    assertThat(markdown).contains("**Commit:** abc123");
    assertThat(markdown).contains("**History depth:** full");
  }

  @Test
  void rendersNoFindingsMessageWhenEmpty() {
    ScanResult scanResult = scanResultWith(List.of(), List.of());

    String markdown = reporter.render(scanResult);

    assertThat(markdown).contains("## Findings (0)");
    assertThat(markdown).contains("No findings.");
  }

  @Test
  void rendersFindingCountsGroupedBySeverity() {
    Finding high = finding(Severity.HIGH, "rule-1", "a.java");
    Finding low1 = finding(Severity.LOW, "rule-2", "b.java");
    Finding low2 = finding(Severity.LOW, "rule-3", "c.java");

    ScanResult scanResult = scanResultWith(List.of(high, low1, low2), List.of());

    String markdown = reporter.render(scanResult);

    assertThat(markdown).contains("## Findings (3)");
    assertThat(markdown).contains("HIGH: 1");
    assertThat(markdown).contains("LOW: 2");
  }

  @Test
  void rendersEngineStatusesIncludingFailureReason() {
    EngineStatus failed =
        new EngineStatus("code-maat", "1.0", EngineHealth.FAILED, 42, "binary not found");
    ScanResult scanResult = scanResultWith(List.of(), List.of(failed));

    String markdown = reporter.render(scanResult);

    assertThat(markdown).contains("## Engines (1)");
    assertThat(markdown).contains("code-maat (1.0): FAILED");
    assertThat(markdown).contains("binary not found");
    assertThat(markdown).contains("**Degraded:** true");
  }

  @Test
  void writesFileToOutputDirectory(@TempDir Path outputDir) throws IOException {
    Path written = reporter.write(scanResultWith(List.of(), List.of()), outputDir);

    assertThat(written).exists();
    assertThat(Files.readString(written)).contains("# Debt Hunter Summary");
  }

  @Test
  void appendsAPublishFailureWarningToAnAlreadyWrittenFile(@TempDir Path outputDir)
      throws IOException {
    Path written = reporter.write(scanResultWith(List.of(), List.of()), outputDir);
    String before = Files.readString(written);

    reporter.appendPublishFailureWarning(outputDir, "connection refused");

    String after = Files.readString(written);
    assertThat(after).startsWith(before);
    assertThat(after).contains("**Warning:** failed to publish scan result: connection refused");
  }

  @Test
  void rendersNoHistoryWarningWhenHistoryIsFull() {
    ScanResult scanResult = scanResultWith(List.of(), List.of(), HistoryDepth.FULL);

    String markdown = reporter.render(scanResult);

    assertThat(markdown).doesNotContain("Warning");
  }

  @Test
  void rendersAHistoryWarningWhenHistoryIsShallow() {
    ScanResult scanResult = scanResultWith(List.of(), List.of(), HistoryDepth.SHALLOW);

    String markdown = reporter.render(scanResult);

    assertThat(markdown)
        .contains("**Warning:**")
        .contains("shallow")
        .contains("debt-hunter doctor");
  }

  @Test
  void rendersAHistoryWarningWhenHistoryIsPartial() {
    ScanResult scanResult = scanResultWith(List.of(), List.of(), HistoryDepth.PARTIAL);

    String markdown = reporter.render(scanResult);

    assertThat(markdown).contains("**Warning:**").contains("partial");
  }

  private ScanResult scanResultWith(List<Finding> findings, List<EngineStatus> engines) {
    return scanResultWith(findings, engines, HistoryDepth.FULL);
  }

  private ScanResult scanResultWith(
      List<Finding> findings, List<EngineStatus> engines, HistoryDepth historyDepth) {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(historyDepth)
            .engines(engines)
            .build();
    return new ScanResult(run, findings, Map.of(), PolicyResult.passed("bundle-1"));
  }

  private Finding finding(Severity severity, String ruleId, String path) {
    return Finding.builder()
        .id(ruleId)
        .ruleId(ruleId)
        .category(Category.HOTSPOT)
        .severity(severity)
        .path(path)
        .startLine(1)
        .message("message")
        .fingerprint("fp-" + ruleId)
        .build();
  }
}
