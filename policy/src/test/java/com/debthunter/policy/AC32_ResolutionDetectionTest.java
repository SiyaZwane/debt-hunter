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
 * AC-32: a baseline finding whose fingerprint no longer appears in the current scan is classified
 * RESOLVED.
 */
class AC32_ResolutionDetectionTest {

  @Test
  void ac32_baselineFindingAbsentFromCurrentScanIsClassifiedResolved() {
    Finding fixedFinding =
        Finding.builder()
            .id("f-1")
            .ruleId("codemaat.churn")
            .category(Category.CHURN)
            .severity(Severity.LOW)
            .path("Foo.java")
            .message("Foo.java has changed 4 times")
            .fingerprint("fp-foo")
            .build();
    Finding stillPresentFinding =
        Finding.builder()
            .id("f-2")
            .ruleId("codemaat.churn")
            .category(Category.CHURN)
            .severity(Severity.LOW)
            .path("Bar.java")
            .message("Bar.java has changed 4 times")
            .fingerprint("fp-bar")
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
        new ScanResult(
            run,
            List.of(fixedFinding, stillPresentFinding),
            Map.of(),
            PolicyResult.passed("unversioned"));

    BaselineComparator.ComparisonResult result =
        new BaselineComparator().compare(List.of(stillPresentFinding), baseline);

    assertThat(result.classified())
        .filteredOn(c -> c.finding().id().equals("f-1"))
        .singleElement()
        .satisfies(
            classified ->
                assertThat(classified.classification())
                    .isEqualTo(BaselineComparator.Classification.RESOLVED));
    assertThat(result.classified())
        .filteredOn(c -> c.finding().id().equals("f-2"))
        .singleElement()
        .satisfies(
            classified ->
                assertThat(classified.classification())
                    .isEqualTo(BaselineComparator.Classification.EXISTING));
  }
}
