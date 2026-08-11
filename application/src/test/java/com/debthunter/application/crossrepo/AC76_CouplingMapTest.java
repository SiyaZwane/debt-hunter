package com.debthunter.application.crossrepo;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AC-76: aggregated, pseudonymised findings across repositories produce a team-level coupling map
 * that is advisory only — it is a plain, standalone data structure, never consulted by policy
 * evaluation or the {@code scan} exit code.
 */
class AC76_CouplingMapTest {

  @Test
  void ac76_findingsSharedAcrossTeamsProduceACouplingMapEntry() {
    List<Finding> platformFindings =
        List.of(
            finding("f-1", "temporal-coupling.rule", Category.TEMPORAL_COUPLING, "Auth.java"),
            finding("f-2", "hotspot.rule", Category.HOTSPOT, "Auth.java"));
    List<Finding> billingFindings =
        List.of(
            finding("f-3", "temporal-coupling.rule", Category.TEMPORAL_COUPLING, "Invoice.java"),
            finding("f-4", "dependency.rule", Category.DEPENDENCY, "Invoice.java"));

    TeamFindingsSummary platform = TeamFindingsSummary.from("team-platform", platformFindings);
    TeamFindingsSummary billing = TeamFindingsSummary.from("team-billing", billingFindings);

    CouplingMap map = new CrossRepositoryAnalyser().analyse(List.of(platform, billing));

    assertThat(map.couplings()).hasSize(1);
    TeamCoupling coupling = map.couplings().get(0);
    assertThat(coupling.teamA()).isEqualTo("team-billing");
    assertThat(coupling.teamB()).isEqualTo("team-platform");
    assertThat(coupling.sharedRuleIds()).containsExactly("temporal-coupling.rule");
  }

  private Finding finding(String id, String ruleId, Category category, String path) {
    return Finding.builder()
        .id(id)
        .ruleId(ruleId)
        .category(category)
        .severity(Severity.MEDIUM)
        .path(path)
        .message(path + " is involved in " + ruleId)
        .fingerprint("fp-" + id)
        .build();
  }
}
