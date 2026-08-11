package com.debthunter.application.crossrepo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Produces team-level coupling maps from aggregated, pseudonymised rule data across repositories.
 * Advisory only: this is control-plane scope, never wired into the {@code scan} gating path, and
 * its output never influences a policy decision or exit code.
 */
public final class CrossRepositoryAnalyser {

  /**
   * Analyses coupling between teams, based on the rules that fire in common across their
   * repositories. Summaries for the same team are merged (their rule sets unioned) before pairwise
   * comparison, so a team's coupling reflects all of its repositories in aggregate.
   *
   * @param summaries pseudonymised, team-level rule summaries, any number per team
   * @return the resulting coupling map, one entry per team pair sharing at least one rule
   */
  public CouplingMap analyse(List<TeamFindingsSummary> summaries) {
    Map<String, Set<String>> ruleIdsByTeam = new TreeMap<>();
    for (TeamFindingsSummary summary : summaries) {
      ruleIdsByTeam
          .computeIfAbsent(summary.teamId(), team -> new TreeSet<>())
          .addAll(summary.ruleIds());
    }

    List<String> teams = new ArrayList<>(ruleIdsByTeam.keySet());
    List<TeamCoupling> couplings = new ArrayList<>();
    for (int i = 0; i < teams.size(); i++) {
      for (int j = i + 1; j < teams.size(); j++) {
        String teamA = teams.get(i);
        String teamB = teams.get(j);
        Set<String> shared = new TreeSet<>(ruleIdsByTeam.get(teamA));
        shared.retainAll(ruleIdsByTeam.get(teamB));
        if (!shared.isEmpty()) {
          couplings.add(new TeamCoupling(teamA, teamB, shared.size(), List.copyOf(shared)));
        }
      }
    }
    return new CouplingMap(couplings);
  }
}
