package com.debthunter.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AC-31: a finding whose severity has escalated since the baseline (same fingerprint, now more
 * severe) is classified REGRESSED, not EXISTING.
 */
class AC31_RegressionDetectionTest {

  @Test
  void ac31_severityEscalationOnTheSameFingerprintIsClassifiedRegressed() {
    Finding baselineFinding =
        Finding.builder()
            .id("f-1")
            .ruleId("codemaat.churn")
            .category(Category.CHURN)
            .severity(Severity.LOW)
            .path("Foo.java")
            .message("Foo.java has changed 4 times")
            .fingerprint("fp-foo")
            .build();
    Finding currentFinding =
        Finding.builder()
            .id("f-1")
            .ruleId("codemaat.churn")
            .category(Category.CHURN)
            .severity(Severity.CRITICAL)
            .path("Foo.java")
            .message("Foo.java has changed 40 times")
            .fingerprint("fp-foo")
            .build();

    AnalysisRun run =
        AnalysisRun.builder()
            .id("baseline-run")
            .toolVersion("0.1.0")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    ScanResult baseline =
        new ScanResult(run, List.of(baselineFinding), Map.of(), PolicyResult.passed("unversioned"));

    BaselineComparator.ComparisonResult result =
        new BaselineComparator().compare(List.of(currentFinding), baseline);

    assertThat(result.classified())
        .anySatisfy(
            classified -> {
              assertThat(classified.finding().id()).isEqualTo("f-1");
              assertThat(classified.classification())
                  .isEqualTo(BaselineComparator.Classification.REGRESSED);
            });
    assertThat(result.findings()).containsExactly(currentFinding.withIsNew(false));
  }
}
