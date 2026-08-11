package com.debthunter.application.crossrepo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CrossRepositoryAnalyserTest {

  private final CrossRepositoryAnalyser analyser = new CrossRepositoryAnalyser();

  @Test
  void twoTeamsWithNoSharedRulesProduceNoCoupling() {
    CouplingMap map =
        analyser.analyse(
            List.of(
                new TeamFindingsSummary("team-a", Set.of("hotspot.rule")),
                new TeamFindingsSummary("team-b", Set.of("churn.rule"))));

    assertThat(map.couplings()).isEmpty();
  }

  @Test
  void twoTeamsWithASharedRuleProduceOneCoupling() {
    CouplingMap map =
        analyser.analyse(
            List.of(
                new TeamFindingsSummary("team-a", Set.of("hotspot.rule", "churn.rule")),
                new TeamFindingsSummary("team-b", Set.of("churn.rule"))));

    assertThat(map.couplings()).hasSize(1);
    TeamCoupling coupling = map.couplings().get(0);
    assertThat(coupling.teamA()).isEqualTo("team-a");
    assertThat(coupling.teamB()).isEqualTo("team-b");
    assertThat(coupling.sharedRuleCount()).isEqualTo(1);
    assertThat(coupling.sharedRuleIds()).containsExactly("churn.rule");
  }

  @Test
  void multipleSummariesForTheSameTeamAreMergedBeforeComparison() {
    CouplingMap map =
        analyser.analyse(
            List.of(
                new TeamFindingsSummary("team-a", Set.of("hotspot.rule")),
                new TeamFindingsSummary("team-a", Set.of("churn.rule")),
                new TeamFindingsSummary("team-b", Set.of("churn.rule"))));

    assertThat(map.couplings()).hasSize(1);
    assertThat(map.couplings().get(0).sharedRuleIds()).containsExactly("churn.rule");
  }

  @Test
  void aSingleTeamProducesNoCoupling() {
    CouplingMap map =
        analyser.analyse(List.of(new TeamFindingsSummary("team-a", Set.of("hotspot.rule"))));

    assertThat(map.couplings()).isEmpty();
  }

  @Test
  void couplingsAreOrderedByTeamPairForDeterminism() {
    CouplingMap map =
        analyser.analyse(
            List.of(
                new TeamFindingsSummary("team-c", Set.of("shared.rule")),
                new TeamFindingsSummary("team-a", Set.of("shared.rule")),
                new TeamFindingsSummary("team-b", Set.of("shared.rule"))));

    assertThat(map.couplings())
        .extracting(TeamCoupling::teamA, TeamCoupling::teamB)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("team-a", "team-b"),
            org.assertj.core.groups.Tuple.tuple("team-a", "team-c"),
            org.assertj.core.groups.Tuple.tuple("team-b", "team-c"));
  }
}
