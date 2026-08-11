package com.debthunter.ai;

import com.debthunter.domain.Finding;

/** Proposes a remediation action from a finding's category, using no external service. */
public final class RuleBasedRemediationAdvisor implements RemediationAdvisor {

  @Override
  public String proposeRemediation(Finding finding) {
    return switch (finding.category()) {
      case HOTSPOT ->
          "Add characterization tests for "
              + finding.path()
              + ", then extract the frequently"
              + " changed logic into a smaller, more focused unit.";
      case CHURN ->
          "Investigate why "
              + finding.path()
              + " changes so often; consider splitting it along"
              + " its independent responsibilities.";
      case TEMPORAL_COUPLING ->
          "Review whether "
              + finding.path()
              + " and the files it co-changes with should share an"
              + " explicit interface instead of an implicit one.";
      case KNOWLEDGE_CONCENTRATION ->
          "Pair on "
              + finding.path()
              + " with another contributor to spread ownership beyond a"
              + " single author.";
      case ARCHITECTURE ->
          "Realign " + finding.path() + " with the declared architecture rule it violates.";
      case STATIC_ANALYSIS ->
          "Address the static-analysis issue reported for " + finding.path() + ".";
      case DEPENDENCY -> "Review and update the flagged dependency used by " + finding.path() + ".";
      case TEST_HEALTH -> "Improve test coverage or reliability for " + finding.path() + ".";
      case CUSTOM ->
          "Review " + finding.path() + " for the pattern described in: " + finding.message();
    };
  }
}
