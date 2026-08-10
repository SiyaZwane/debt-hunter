package com.debthunter.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.PolicyStatus;
import com.debthunter.domain.Severity;
import com.debthunter.engine.spi.AnalysisMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AC-36: when several rules are each independently violated, every one of them is reported — the
 * evaluator never short-circuits after the first breach.
 */
class AC36_MultipleViolationsTest {

  @Test
  void ac36_everyViolatedRuleIsReportedNotJustTheFirst() {
    PolicyBundle bundle =
        new PolicyBundle(
            "1.0",
            Map.of(),
            null,
            List.of(
                new PolicyRule("no-new-critical", Severity.CRITICAL, 0),
                new PolicyRule("limit-new-high", Severity.HIGH, 1),
                new PolicyRule("limit-new-medium", Severity.MEDIUM, 1)),
            List.of(),
            List.of(),
            List.of(),
            0);

    List<Finding> findings =
        List.of(
            finding("f-critical", Severity.CRITICAL),
            finding("f-high-1", Severity.HIGH),
            finding("f-high-2", Severity.HIGH),
            finding("f-medium-1", Severity.MEDIUM),
            finding("f-medium-2", Severity.MEDIUM));

    var result = new PolicyEvaluator().evaluate(findings, bundle, AnalysisMode.FULL);

    assertThat(result.status()).isEqualTo(PolicyStatus.FAILED);
    assertThat(result.reasons()).hasSize(3);
    assertThat(result.reasons())
        .extracting(v -> v.rule())
        .containsExactly("no-new-critical", "limit-new-high", "limit-new-medium");
  }

  private Finding finding(String id, Severity severity) {
    return Finding.builder()
        .id(id)
        .ruleId("rule")
        .category(Category.CHURN)
        .severity(severity)
        .path("Foo.java")
        .message("msg")
        .fingerprint("fp-" + id)
        .isNew(true)
        .build();
  }
}
