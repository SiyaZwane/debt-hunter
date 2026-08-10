package com.debthunter.policy;

import com.debthunter.domain.Finding;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.PolicyStatus;
import com.debthunter.domain.PolicyViolation;
import com.debthunter.domain.Severity;
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
    return evaluate(findings, bundle, mode, null);
  }

  /**
   * Evaluates {@code findings} against {@code bundle}'s rule set for {@code mode}, plus an
   * additional, ad hoc threshold: at most zero new findings at severity {@code failOn} or more
   * severe. This lets a local developer run ({@code debt-hunter scan --fail-on HIGH}) gate on a
   * severity threshold without first authoring a policy bundle, on top of whatever the bundle
   * itself already enforces — {@code failOn} tightens the effective policy for this run, it never
   * loosens it.
   *
   * @param findings this scan's findings, with {@link Finding#isNew()} already set
   * @param bundle the policy bundle to evaluate against
   * @param mode which rule set applies: {@link AnalysisMode#PULL_REQUEST} or {@link
   *     AnalysisMode#FULL}
   * @param failOn the {@code --fail-on} override, or {@code null} if none was given
   * @return {@link PolicyStatus#PASSED} with no reasons, or {@link PolicyStatus#FAILED} with every
   *     violated rule, including a {@code fail-on} violation if {@code failOn} applies
   */
  public PolicyResult evaluate(
      List<Finding> findings, PolicyBundle bundle, AnalysisMode mode, Severity failOn) {
    List<Finding> eligible = eligibleFindings(findings, bundle);

    List<PolicyViolation> violations = new ArrayList<>();
    for (PolicyRule rule : bundle.rulesFor(mode)) {
      List<Finding> qualifying = atOrAboveSeverity(eligible, rule.minSeverity());
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

    if (failOn != null) {
      List<Finding> overrideQualifying = atOrAboveSeverity(eligible, failOn);
      if (!overrideQualifying.isEmpty()) {
        violations.add(
            new PolicyViolation(
                "fail-on:" + failOn,
                "no new finding(s) at severity >= " + failOn,
                String.valueOf(overrideQualifying.size()),
                overrideQualifying.stream().map(Finding::id).toList()));
      }
    }

    PolicyStatus status = violations.isEmpty() ? PolicyStatus.PASSED : PolicyStatus.FAILED;
    return new PolicyResult(bundle.version(), status, violations);
  }

  private List<Finding> eligibleFindings(List<Finding> findings, PolicyBundle bundle) {
    return findings.stream().filter(Finding::isNew).filter(f -> !isExcluded(f, bundle)).toList();
  }

  private List<Finding> atOrAboveSeverity(List<Finding> findings, Severity minSeverity) {
    return findings.stream().filter(f -> f.severity().ordinal() <= minSeverity.ordinal()).toList();
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
