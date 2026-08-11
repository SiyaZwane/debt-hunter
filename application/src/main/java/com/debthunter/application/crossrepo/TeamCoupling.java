package com.debthunter.application.crossrepo;

import java.util.List;
import java.util.Objects;

/**
 * Advisory-only coupling between two teams: the rule ids that fire in both teams' repositories,
 * hinting at shared architectural or process concerns. Carries no individual, repository, or file
 * identity — only team ids and rule ids.
 */
public record TeamCoupling(
    String teamA, String teamB, int sharedRuleCount, List<String> sharedRuleIds) {

  public TeamCoupling {
    Objects.requireNonNull(teamA, "teamA");
    Objects.requireNonNull(teamB, "teamB");
    sharedRuleIds = List.copyOf(sharedRuleIds);
  }
}
