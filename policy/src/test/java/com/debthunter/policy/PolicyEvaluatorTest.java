package com.debthunter.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.PolicyStatus;
import com.debthunter.domain.Severity;
import com.debthunter.engine.spi.AnalysisMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyEvaluatorTest {

  private final PolicyEvaluator evaluator = new PolicyEvaluator();

  @Test
  void aBundleWithNoRulesAlwaysPasses() {
    var result =
        evaluator.evaluate(
            List.of(finding("f-1", Severity.CRITICAL, true)),
            PolicyBundle.permissive(),
            AnalysisMode.FULL);

    assertThat(result.status()).isEqualTo(PolicyStatus.PASSED);
    assertThat(result.reasons()).isEmpty();
  }

  @Test
  void aNewFindingAtOrAboveTheThresholdSeverityViolatesTheRule() {
    PolicyBundle bundle = bundleWithMainRule("no-critical", Severity.CRITICAL, 0);
    Finding critical = finding("f-1", Severity.CRITICAL, true);

    var result = evaluator.evaluate(List.of(critical), bundle, AnalysisMode.FULL);

    assertThat(result.status()).isEqualTo(PolicyStatus.FAILED);
    assertThat(result.reasons()).hasSize(1);
    assertThat(result.reasons().get(0).rule()).isEqualTo("no-critical");
    assertThat(result.reasons().get(0).actual()).isEqualTo("1");
    assertThat(result.reasons().get(0).findingIds()).containsExactly("f-1");
  }

  @Test
  void aNewFindingBelowTheThresholdSeverityDoesNotCount() {
    PolicyBundle bundle = bundleWithMainRule("no-critical", Severity.CRITICAL, 0);
    Finding low = finding("f-1", Severity.LOW, true);

    var result = evaluator.evaluate(List.of(low), bundle, AnalysisMode.FULL);

    assertThat(result.status()).isEqualTo(PolicyStatus.PASSED);
  }

  @Test
  void anExistingNonNewFindingIsNeverCountedTowardAnyThreshold() {
    PolicyBundle bundle = bundleWithMainRule("no-critical", Severity.CRITICAL, 0);
    Finding existingCritical = finding("f-1", Severity.CRITICAL, false);

    var result = evaluator.evaluate(List.of(existingCritical), bundle, AnalysisMode.FULL);

    assertThat(result.status()).isEqualTo(PolicyStatus.PASSED);
  }

  @Test
  void aCountAtExactlyMaxCountDoesNotViolate() {
    PolicyBundle bundle = bundleWithMainRule("limit-high", Severity.HIGH, 2);
    var findings =
        List.of(finding("f-1", Severity.HIGH, true), finding("f-2", Severity.HIGH, true));

    var result = evaluator.evaluate(findings, bundle, AnalysisMode.FULL);

    assertThat(result.status()).isEqualTo(PolicyStatus.PASSED);
  }

  @Test
  void aCountOneOverMaxCountViolates() {
    PolicyBundle bundle = bundleWithMainRule("limit-high", Severity.HIGH, 2);
    var findings =
        List.of(
            finding("f-1", Severity.HIGH, true),
            finding("f-2", Severity.HIGH, true),
            finding("f-3", Severity.HIGH, true));

    var result = evaluator.evaluate(findings, bundle, AnalysisMode.FULL);

    assertThat(result.status()).isEqualTo(PolicyStatus.FAILED);
    assertThat(result.reasons().get(0).actual()).isEqualTo("3");
  }

  @Test
  void pullRequestModeUsesThePullRequestRuleSet() {
    PolicyBundle bundle = bundleWithPullRequestRule("no-high-in-pr", Severity.HIGH, 0);
    Finding high = finding("f-1", Severity.HIGH, true);

    var mainResult = evaluator.evaluate(List.of(high), bundle, AnalysisMode.FULL);
    var pullRequestResult = evaluator.evaluate(List.of(high), bundle, AnalysisMode.PULL_REQUEST);

    assertThat(mainResult.status()).isEqualTo(PolicyStatus.PASSED);
    assertThat(pullRequestResult.status()).isEqualTo(PolicyStatus.FAILED);
  }

  @Test
  void anExcludedCategoryIsNeverCountedTowardAnyThreshold() {
    PolicyBundle base = bundleWithMainRule("no-critical", Severity.CRITICAL, 0);
    PolicyBundle bundle =
        new PolicyBundle(
            base.version(),
            base.metadata(),
            base.minimumHistoryDepth(),
            base.mainRules(),
            base.pullRequestRules(),
            List.of(Category.HOTSPOT),
            base.excludedPaths(),
            base.suppressionsMaxExpiryDays());
    Finding excludedHotspot =
        Finding.builder()
            .id("f-1")
            .ruleId("hotspot.rule")
            .category(Category.HOTSPOT)
            .severity(Severity.CRITICAL)
            .path("Foo.java")
            .message("msg")
            .fingerprint("fp-f-1")
            .isNew(true)
            .build();

    var result = evaluator.evaluate(List.of(excludedHotspot), bundle, AnalysisMode.FULL);

    assertThat(result.status()).isEqualTo(PolicyStatus.PASSED);
  }

  @Test
  void anExcludedPathIsNeverCountedTowardAnyThreshold() {
    PolicyBundle base = bundleWithMainRule("no-critical", Severity.CRITICAL, 0);
    PolicyBundle bundle =
        new PolicyBundle(
            base.version(),
            base.metadata(),
            base.minimumHistoryDepth(),
            base.mainRules(),
            base.pullRequestRules(),
            base.excludedCategories(),
            List.of("generated"),
            base.suppressionsMaxExpiryDays());
    Finding inExcludedDir =
        Finding.builder()
            .id("f-1")
            .ruleId("rule")
            .category(Category.CHURN)
            .severity(Severity.CRITICAL)
            .path("generated/Foo.java")
            .message("msg")
            .fingerprint("fp-f-1")
            .isNew(true)
            .build();

    var result = evaluator.evaluate(List.of(inExcludedDir), bundle, AnalysisMode.FULL);

    assertThat(result.status()).isEqualTo(PolicyStatus.PASSED);
  }

  @Test
  void withNoFailOnOverrideAPermissiveBundlePasses() {
    var result =
        evaluator.evaluate(
            List.of(finding("f-1", Severity.CRITICAL, true)),
            PolicyBundle.permissive(),
            AnalysisMode.FULL,
            null);

    assertThat(result.status()).isEqualTo(PolicyStatus.PASSED);
  }

  @Test
  void aFailOnOverrideFailsAPermissiveBundleThatWouldOtherwisePass() {
    Finding high = finding("f-1", Severity.HIGH, true);

    var result =
        evaluator.evaluate(
            List.of(high), PolicyBundle.permissive(), AnalysisMode.FULL, Severity.HIGH);

    assertThat(result.status()).isEqualTo(PolicyStatus.FAILED);
    assertThat(result.reasons()).hasSize(1);
    assertThat(result.reasons().get(0).rule()).isEqualTo("fail-on:HIGH");
    assertThat(result.reasons().get(0).findingIds()).containsExactly("f-1");
  }

  @Test
  void aFailOnOverrideDoesNotTriggerBelowItsSeverity() {
    Finding low = finding("f-1", Severity.LOW, true);

    var result =
        evaluator.evaluate(
            List.of(low), PolicyBundle.permissive(), AnalysisMode.FULL, Severity.HIGH);

    assertThat(result.status()).isEqualTo(PolicyStatus.PASSED);
  }

  @Test
  void aFailOnOverrideNeverCountsAnExistingNonNewFinding() {
    Finding existingHigh = finding("f-1", Severity.HIGH, false);

    var result =
        evaluator.evaluate(
            List.of(existingHigh), PolicyBundle.permissive(), AnalysisMode.FULL, Severity.HIGH);

    assertThat(result.status()).isEqualTo(PolicyStatus.PASSED);
  }

  @Test
  void aFailOnOverrideNeverCountsAnExcludedFinding() {
    PolicyBundle base = PolicyBundle.permissive();
    PolicyBundle bundle =
        new PolicyBundle(
            base.version(),
            base.metadata(),
            base.minimumHistoryDepth(),
            base.mainRules(),
            base.pullRequestRules(),
            List.of(Category.HOTSPOT),
            base.excludedPaths(),
            base.suppressionsMaxExpiryDays());
    Finding excludedHotspot =
        Finding.builder()
            .id("f-1")
            .ruleId("hotspot.rule")
            .category(Category.HOTSPOT)
            .severity(Severity.CRITICAL)
            .path("Foo.java")
            .message("msg")
            .fingerprint("fp-f-1")
            .isNew(true)
            .build();

    var result =
        evaluator.evaluate(List.of(excludedHotspot), bundle, AnalysisMode.FULL, Severity.CRITICAL);

    assertThat(result.status()).isEqualTo(PolicyStatus.PASSED);
  }

  @Test
  void aFailOnOverrideAddsToRatherThanReplacesTheBundlesOwnViolations() {
    PolicyBundle bundle = bundleWithMainRule("no-critical", Severity.CRITICAL, 0);
    Finding critical = finding("f-1", Severity.CRITICAL, true);
    Finding high = finding("f-2", Severity.HIGH, true);

    var result =
        evaluator.evaluate(List.of(critical, high), bundle, AnalysisMode.FULL, Severity.HIGH);

    assertThat(result.status()).isEqualTo(PolicyStatus.FAILED);
    assertThat(result.reasons()).hasSize(2);
    assertThat(result.reasons().stream().map(v -> v.rule()).toList())
        .containsExactlyInAnyOrder("no-critical", "fail-on:HIGH");
  }

  private PolicyBundle bundleWithMainRule(String id, Severity severity, int maxCount) {
    return new PolicyBundle(
        "1.0",
        java.util.Map.of(),
        null,
        List.of(new PolicyRule(id, severity, maxCount)),
        List.of(),
        List.of(),
        List.of(),
        0);
  }

  private PolicyBundle bundleWithPullRequestRule(String id, Severity severity, int maxCount) {
    return new PolicyBundle(
        "1.0",
        java.util.Map.of(),
        null,
        List.of(),
        List.of(new PolicyRule(id, severity, maxCount)),
        List.of(),
        List.of(),
        0);
  }

  private Finding finding(String id, Severity severity, boolean isNew) {
    return Finding.builder()
        .id(id)
        .ruleId("rule")
        .category(Category.CHURN)
        .severity(severity)
        .path("Foo.java")
        .message("msg")
        .fingerprint("fp-" + id)
        .isNew(isNew)
        .build();
  }
}
