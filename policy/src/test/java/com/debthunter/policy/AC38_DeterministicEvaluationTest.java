package com.debthunter.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.Severity;
import com.debthunter.engine.spi.AnalysisMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AC-38: evaluating the exact same findings against the exact same policy bundle always produces
 * the exact same result — no clock, no network, no ordering surprises.
 */
class AC38_DeterministicEvaluationTest {

  @Test
  void ac38_repeatedEvaluationOfIdenticalInputsProducesAnIdenticalResult() {
    PolicyBundle bundle =
        new PolicyBundle(
            "1.0",
            Map.of(),
            null,
            List.of(
                new PolicyRule("no-new-critical", Severity.CRITICAL, 0),
                new PolicyRule("limit-new-high", Severity.HIGH, 1)),
            List.of(),
            List.of(),
            List.of(),
            0);
    List<Finding> findings =
        List.of(
            finding("f-1", Severity.CRITICAL),
            finding("f-2", Severity.HIGH),
            finding("f-3", Severity.HIGH));

    PolicyEvaluator evaluator = new PolicyEvaluator();
    PolicyResult first = evaluator.evaluate(findings, bundle, AnalysisMode.FULL);
    PolicyResult second = evaluator.evaluate(findings, bundle, AnalysisMode.FULL);

    assertThat(first).isEqualTo(second);
    // A fresh evaluator instance changes nothing: evaluation carries no internal state.
    PolicyResult third = new PolicyEvaluator().evaluate(findings, bundle, AnalysisMode.FULL);
    assertThat(first).isEqualTo(third);
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
