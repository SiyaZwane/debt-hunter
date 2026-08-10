package com.debthunter.policy;

import com.debthunter.domain.Finding;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.PolicyStatus;
import com.debthunter.domain.PolicyViolation;
import com.debthunter.engine.spi.AnalysisMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a policy bundle's thresholds against a scan's new findings.
 *
 * <p>Pure function: the same findings and the same bundle always produce the same result. No
 * network access, no clock — nothing here depends on when it runs, only on what it's given. Every
 * rule in the active mode's rule set is checked; a violation in one rule never suppresses checking
 * the rest, so {@link PolicyResult#reasons()} always lists every breach, not just the first.
 */
public final class PolicyEvaluator {

  /**
   * Evaluates {@code findings} against {@code bundle}'s rule set for {@code mode}.
   *
   * @param findings this scan's findings, with {@link Finding#isNew()} already set
   * @param bundle the policy bundle to evaluate against
   * @param mode which rule set applies: {@link AnalysisMode#PULL_REQUEST} or {@link
   *     AnalysisMode#FULL}
   * @return {@link PolicyStatus#PASSED} with no reasons, or {@link PolicyStatus#FAILED} with every
   *     violated rule
   */
  public PolicyResult evaluate(List<Finding> findings, PolicyBundle bundle, AnalysisMode mode) {
    List<Finding> eligible =
        findings.stream().filter(Finding::isNew).filter(f -> !isExcluded(f, bundle)).toList();

    List<PolicyViolation> violations = new ArrayList<>();
    for (PolicyRule rule : bundle.rulesFor(mode)) {
      List<Finding> qualifying =
          eligible.stream()
              .filter(f -> f.severity().ordinal() <= rule.minSeverity().ordinal())
              .toList();
      if (qualifying.size() > rule.maxCount()) {
        violations.add(
            new PolicyViolation(
                rule.id(),
                "at most "
                    + rule.maxCount()
                    + " new finding(s) at severity >= "
                    + rule.minSeverity(),
                String.valueOf(qualifying.size()),
                qualifying.stream().map(Finding::id).toList()));
      }
    }

    PolicyStatus status = violations.isEmpty() ? PolicyStatus.PASSED : PolicyStatus.FAILED;
    return new PolicyResult(bundle.version(), status, violations);
  }

  private boolean isExcluded(Finding finding, PolicyBundle bundle) {
    if (bundle.excludedCategories().contains(finding.category())) {
      return true;
    }
    for (String excludedPath : bundle.excludedPaths()) {
      if (finding.path().equals(excludedPath) || finding.path().startsWith(excludedPath + "/")) {
        return true;
      }
    }
    return false;
  }
}
