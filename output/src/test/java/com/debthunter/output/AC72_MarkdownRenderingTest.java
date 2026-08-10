package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.EngineHealth;
import com.debthunter.domain.EngineStatus;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.PolicyStatus;
import com.debthunter.domain.PolicyViolation;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.debthunter.domain.SuppressionEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AC-72: however much is packed into it, {@code summary.md} stays valid Markdown — well-formed
 * headings, balanced inline code spans, and a stable heading hierarchy — never truncated or
 * malformed mid-render.
 */
class AC72_MarkdownRenderingTest {

  private final MarkdownReporter reporter = new MarkdownReporter();

  @Test
  void ac72_theFullyPopulatedSummaryIsValidMarkdown() {
    Finding finding =
        Finding.builder()
            .id("f-1")
            .ruleId("hotspot.rule")
            .category(Category.HOTSPOT)
            .severity(Severity.CRITICAL)
            .path("Foo.java")
            .message("Foo.java is overdue")
            .fingerprint("fp-1")
            .score(90.0)
            .isNew(true)
            .build();
    EngineStatus degraded =
        new EngineStatus("code-maat", "1.0", EngineHealth.DEGRADED, 100, "slow");
    PolicyResult policy =
        new PolicyResult(
            "bundle-1",
            PolicyStatus.FAILED,
            List.of(new PolicyViolation("no-critical", "maxCount=0", "1", List.of("fp-1"))));
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.SHALLOW)
            .engines(List.of(degraded))
            .build();
    ScanResult scanResult = new ScanResult(run, List.of(finding), Map.of(), policy);
    SuppressionEntry suppression =
        new SuppressionEntry("fp-2", "alice", "tracked in JIRA-123", LocalDate.parse("2026-06-01"));

    String markdown = reporter.render(scanResult, List.of(suppression));

    assertValidMarkdown(markdown);
  }

  @Test
  void ac72_theEmptySummaryIsValidMarkdown() {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    ScanResult scanResult =
        new ScanResult(run, List.of(), Map.of(), PolicyResult.passed("bundle-1"));

    String markdown = reporter.render(scanResult);

    assertValidMarkdown(markdown);
  }

  private void assertValidMarkdown(String markdown) {
    assertThat(markdown).startsWith("# Debt Hunter Summary\n\n");

    List<String> lines = markdown.lines().toList();
    for (String line : lines) {
      assertThat(line).doesNotContain("\t");
      long backtickCount = line.chars().filter(c -> c == '`').count();
      assertThat(backtickCount % 2)
          .withFailMessage("line has an unbalanced inline code span: %s", line)
          .isZero();
      if (line.startsWith("#")) {
        assertThat(line).matches("^#{1,2} .+$");
      }
      if (line.startsWith("-")) {
        assertThat(line).matches("^- .+$");
      }
    }

    long headingCount = lines.stream().filter(line -> line.startsWith("##")).count();
    assertThat(headingCount).isGreaterThan(0);
  }
}
