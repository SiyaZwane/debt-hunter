package com.debthunter.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoryDepthEnforcerTest {

  private final HistoryDepthEnforcer enforcer = new HistoryDepthEnforcer();

  @Test
  void noMinimumIsAlwaysSufficientRegardlessOfActualDepth() {
    assertThat(enforcer.check(HistoryDepth.SHALLOW, null).sufficient()).isTrue();
    assertThat(enforcer.check(HistoryDepth.FULL, null).sufficient()).isTrue();
  }

  @Test
  void fullHistorySatisfiesEveryMinimum() {
    for (HistoryDepth minimum : HistoryDepth.values()) {
      assertThat(enforcer.check(HistoryDepth.FULL, minimum).sufficient()).isTrue();
    }
  }

  @Test
  void shallowHistoryOnlySatisfiesAShallowMinimum() {
    assertThat(enforcer.check(HistoryDepth.SHALLOW, HistoryDepth.SHALLOW).sufficient()).isTrue();
    assertThat(enforcer.check(HistoryDepth.SHALLOW, HistoryDepth.PARTIAL).sufficient()).isFalse();
    assertThat(enforcer.check(HistoryDepth.SHALLOW, HistoryDepth.FULL).sufficient()).isFalse();
  }

  @Test
  void anInsufficientCheckCarriesADescriptiveMessage() {
    HistoryDepthCheck check = enforcer.check(HistoryDepth.SHALLOW, HistoryDepth.FULL);

    assertThat(check.sufficient()).isFalse();
    assertThat(check.diagnosticMessage()).contains("SHALLOW").contains("FULL");
  }

  @Test
  void aSufficientCheckHasNoDiagnosticMessage() {
    assertThat(enforcer.check(HistoryDepth.FULL, HistoryDepth.FULL).diagnosticMessage()).isNull();
  }

  @Test
  void fullHistoryLeavesConfidenceUnchanged() {
    Finding hotspot = finding(Category.HOTSPOT, 0.9);

    List<Finding> adjusted = enforcer.adjustConfidence(List.of(hotspot), HistoryDepth.FULL);

    assertThat(adjusted).containsExactly(hotspot);
  }

  @Test
  void shallowHistoryReducesConfidenceOnHistoryDependentCategories() {
    Finding hotspot = finding(Category.HOTSPOT, 0.9);
    Finding churn = finding(Category.CHURN, 0.8);
    Finding coupling = finding(Category.TEMPORAL_COUPLING, 0.6);
    Finding knowledge = finding(Category.KNOWLEDGE_CONCENTRATION, 1.0);

    List<Finding> adjusted =
        enforcer.adjustConfidence(
            List.of(hotspot, churn, coupling, knowledge), HistoryDepth.SHALLOW);

    assertThat(adjusted.get(0).confidence()).isCloseTo(0.45, offset(1e-9));
    assertThat(adjusted.get(1).confidence()).isCloseTo(0.4, offset(1e-9));
    assertThat(adjusted.get(2).confidence()).isCloseTo(0.3, offset(1e-9));
    assertThat(adjusted.get(3).confidence()).isCloseTo(0.5, offset(1e-9));
  }

  @Test
  void partialHistoryReducesConfidenceLessThanShallowDoes() {
    Finding hotspot = finding(Category.HOTSPOT, 0.8);

    Finding partialAdjusted =
        enforcer.adjustConfidence(List.of(hotspot), HistoryDepth.PARTIAL).get(0);
    Finding shallowAdjusted =
        enforcer.adjustConfidence(List.of(hotspot), HistoryDepth.SHALLOW).get(0);

    assertThat(partialAdjusted.confidence()).isGreaterThan(shallowAdjusted.confidence());
    assertThat(partialAdjusted.confidence()).isLessThan(hotspot.confidence());
  }

  @Test
  void nonHistoryDependentCategoriesAreNeverAdjusted() {
    Finding staticFinding = finding(Category.STATIC_ANALYSIS, 0.9);
    Finding architectureFinding = finding(Category.ARCHITECTURE, 0.7);

    List<Finding> adjusted =
        enforcer.adjustConfidence(
            List.of(staticFinding, architectureFinding), HistoryDepth.SHALLOW);

    assertThat(adjusted).containsExactly(staticFinding, architectureFinding);
  }

  @Test
  void adjustmentPreservesEveryOtherFieldOnTheFinding() {
    Finding hotspot = finding(Category.HOTSPOT, 0.8);

    Finding adjusted = enforcer.adjustConfidence(List.of(hotspot), HistoryDepth.SHALLOW).get(0);

    assertThat(adjusted.id()).isEqualTo(hotspot.id());
    assertThat(adjusted.fingerprint()).isEqualTo(hotspot.fingerprint());
    assertThat(adjusted.severity()).isEqualTo(hotspot.severity());
    assertThat(adjusted.confidence()).isNotEqualTo(hotspot.confidence());
  }

  private Finding finding(Category category, double confidence) {
    return Finding.builder()
        .id("f-" + category)
        .ruleId("rule")
        .category(category)
        .severity(Severity.MEDIUM)
        .confidence(confidence)
        .path("Foo.java")
        .message("msg")
        .fingerprint("fp-" + category)
        .build();
  }
}
