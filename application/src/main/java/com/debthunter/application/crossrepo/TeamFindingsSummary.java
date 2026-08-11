package com.debthunter.application.crossrepo;

import com.debthunter.domain.Finding;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A pseudonymised, team-level rollup of which rules fired somewhere in a team's repositories.
 * Deliberately discards everything that could identify an individual repository, file, author, or
 * commit — only the team id and the set of triggered rule ids survive.
 */
public record TeamFindingsSummary(String teamId, Set<String> ruleIds) {

  public TeamFindingsSummary {
    Objects.requireNonNull(teamId, "teamId");
    ruleIds = Set.copyOf(ruleIds);
  }

  /**
   * Builds a summary from a team's findings, keeping only the team id and the distinct rule ids
   * that fired — discarding paths, messages, evidence, and fingerprints.
   *
   * @param teamId the pseudonymous team identifier
   * @param findings the team's findings, from any number of its repositories
   * @return the pseudonymised summary
   */
  public static TeamFindingsSummary from(String teamId, List<Finding> findings) {
    Set<String> ruleIds = findings.stream().map(Finding::ruleId).collect(Collectors.toSet());
    return new TeamFindingsSummary(teamId, ruleIds);
  }
}
