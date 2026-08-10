package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
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
 * AC-70: {@code summary.md} carries a complete picture of the run — verdict, new findings by
 * severity, existing count, history depth, breached thresholds with rule/threshold/actual, active
 * suppressions, and the top new findings by score — not just a bare findings list.
 */
class AC70_SummaryContentTest {

  private final MarkdownReporter reporter = new MarkdownReporter();

  @Test
  void ac70_summaryContainsVerdictNewFindingsThresholdsAndTopFindings() {
    Finding newCritical = finding("fp-new-critical", Severity.CRITICAL, 90.0, true);
    Finding newHigh = finding("fp-new-high", Severity.HIGH, 70.0, true);
    Finding newLow = finding("fp-new-low", Severity.LOW, 10.0, true);
    Finding newLowest = finding("fp-new-lowest", Severity.LOW, 1.0, true);
    Finding existing = finding("fp-existing", Severity.MEDIUM, 50.0, false);

    PolicyResult policy =
        new PolicyResult(
            "bundle-1",
            PolicyStatus.FAILED,
            List.of(
                new PolicyViolation("no-critical", "maxCount=0", "1", List.of("fp-new-critical"))));

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
        new ScanResult(
            run, List.of(newCritical, newHigh, newLow, newLowest, existing), Map.of(), policy);
    SuppressionEntry suppression =
        new SuppressionEntry("fp-suppressed", "alice", "tracked", LocalDate.parse("2026-06-01"));

    String markdown = reporter.render(scanResult, List.of(suppression));

    assertThat(markdown).contains("**Verdict:** FAILED");
    assertThat(markdown).contains("**Existing findings:** 1");
    assertThat(markdown).contains("**History depth:** full");
    assertThat(markdown)
        .contains("## New findings (4)")
        .contains("CRITICAL: 1")
        .contains("HIGH: 1")
        .contains("LOW: 2");
    assertThat(markdown)
        .contains("## Breached thresholds (1)")
        .contains("no-critical")
        .contains("maxCount=0")
        .contains("actual: 1");
    assertThat(markdown).contains("## Active suppressions (1)").contains("fp-suppressed");
    assertThat(markdown)
        .contains("## Top new findings")
        .contains("fp-new-critical")
        .contains("fp-new-high");

    int topSectionStart = markdown.indexOf("## Top new findings");
    assertThat(markdown.indexOf("fp-new-critical", topSectionStart)).isGreaterThan(0);
    assertThat(markdown.indexOf("fp-new-lowest", topSectionStart)).isLessThan(0);
  }

  private Finding finding(String fingerprint, Severity severity, double score, boolean isNew) {
    return Finding.builder()
        .id("f-" + fingerprint)
        .ruleId("hotspot.rule")
        .category(Category.HOTSPOT)
        .severity(severity)
        .path("Foo.java")
        .message("Foo.java is overdue")
        .fingerprint(fingerprint)
        .score(score)
        .isNew(isNew)
        .build();
  }
}
