package com.debthunter.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.debthunter.policy.BaselineComparator.Classification;
import com.debthunter.policy.BaselineComparator.ClassifiedFinding;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaselineComparatorTest {

  private final BaselineComparator comparator = new BaselineComparator();

  @Test
  void withNoBaselineEveryFindingIsNew() {
    Finding finding = finding("f-1", Severity.MEDIUM);

    BaselineComparator.ComparisonResult result = comparator.compare(List.of(finding), null);

    assertThat(result.findings()).containsExactly(finding.withIsNew(true));
    assertThat(result.classified())
        .containsExactly(new ClassifiedFinding(finding.withIsNew(true), Classification.NEW));
  }

  @Test
  void aFingerprintAbsentFromTheBaselineIsClassifiedNew() {
    Finding current = finding("f-1", Severity.MEDIUM);
    ScanResult baseline = baselineOf(finding("f-other", Severity.MEDIUM));

    BaselineComparator.ComparisonResult result = comparator.compare(List.of(current), baseline);

    assertThat(result.findings()).containsExactly(current.withIsNew(true));
    assertThat(classificationOf(result, "f-1")).isEqualTo(Classification.NEW);
  }

  @Test
  void aMatchingFingerprintWithTheSameSeverityIsClassifiedExisting() {
    Finding current = finding("f-1", Severity.MEDIUM);
    ScanResult baseline = baselineOf(finding("f-1", Severity.MEDIUM));

    BaselineComparator.ComparisonResult result = comparator.compare(List.of(current), baseline);

    assertThat(result.findings()).containsExactly(current.withIsNew(false));
    assertThat(classificationOf(result, "f-1")).isEqualTo(Classification.EXISTING);
  }

  @Test
  void aMatchingFingerprintWithLowerSeverityIsClassifiedExisting() {
    Finding current = finding("f-1", Severity.LOW);
    ScanResult baseline = baselineOf(finding("f-1", Severity.HIGH));

    BaselineComparator.ComparisonResult result = comparator.compare(List.of(current), baseline);

    assertThat(classificationOf(result, "f-1")).isEqualTo(Classification.EXISTING);
    assertThat(result.findings()).containsExactly(current.withIsNew(false));
  }

  @Test
  void aMatchingFingerprintWithHigherSeverityIsClassifiedRegressed() {
    Finding current = finding("f-1", Severity.CRITICAL);
    ScanResult baseline = baselineOf(finding("f-1", Severity.LOW));

    BaselineComparator.ComparisonResult result = comparator.compare(List.of(current), baseline);

    assertThat(classificationOf(result, "f-1")).isEqualTo(Classification.REGRESSED);
    assertThat(result.findings()).containsExactly(current.withIsNew(false));
  }

  @Test
  void aBaselineFindingWithNoMatchingCurrentFingerprintIsClassifiedResolved() {
    ScanResult baseline = baselineOf(finding("f-1", Severity.MEDIUM));

    BaselineComparator.ComparisonResult result = comparator.compare(List.of(), baseline);

    assertThat(result.findings()).isEmpty();
    assertThat(result.classified())
        .containsExactly(
            new ClassifiedFinding(finding("f-1", Severity.MEDIUM), Classification.RESOLVED));
  }

  private Classification classificationOf(BaselineComparator.ComparisonResult result, String id) {
    return result.classified().stream()
        .filter(c -> c.finding().id().equals(id))
        .findFirst()
        .orElseThrow()
        .classification();
  }

  private ScanResult baselineOf(Finding... findings) {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("baseline-run")
            .toolVersion("0.1.0")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    return new ScanResult(run, List.of(findings), Map.of(), PolicyResult.passed("unversioned"));
  }

  private Finding finding(String id, Severity severity) {
    return Finding.builder()
        .id(id)
        .ruleId("rule")
        .category(Category.HOTSPOT)
        .severity(severity)
        .path("Foo.java")
        .message("msg")
        .fingerprint("fp-" + id)
        .build();
  }
}
