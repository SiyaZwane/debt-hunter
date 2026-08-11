package com.debthunter.application.crossrepo;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AC-77: cross-repository analysis never surfaces individual identity — no file path, message, or
 * fingerprint from the underlying findings leaks into the team-level coupling map, even when the
 * source findings carry such detail.
 */
class AC77_NoIndividualIdentityTest {

  @Test
  void ac77_theCouplingMapContainsOnlyTeamAndRuleIdentifiers() {
    Finding platformFinding =
        Finding.builder()
            .id("f-1")
            .ruleId("temporal-coupling.rule")
            .category(Category.TEMPORAL_COUPLING)
            .severity(Severity.HIGH)
            .path("src/main/java/com/acme/auth/SecretHandler.java")
            .message("SecretHandler.java changes with UnrelatedFile.java, authored by jane.doe")
            .evidence(Map.of("author", "jane.doe@acme.example"))
            .fingerprint("fp-super-secret-fingerprint")
            .build();
    Finding billingFinding =
        Finding.builder()
            .id("f-2")
            .ruleId("temporal-coupling.rule")
            .category(Category.TEMPORAL_COUPLING)
            .severity(Severity.HIGH)
            .path("src/main/java/com/acme/billing/Invoice.java")
            .message("Invoice.java changes with Ledger.java, authored by john.smith")
            .evidence(Map.of("author", "john.smith@acme.example"))
            .fingerprint("fp-another-secret-fingerprint")
            .build();

    TeamFindingsSummary platform =
        TeamFindingsSummary.from("team-platform", List.of(platformFinding));
    TeamFindingsSummary billing = TeamFindingsSummary.from("team-billing", List.of(billingFinding));

    CouplingMap map = new CrossRepositoryAnalyser().analyse(List.of(platform, billing));

    assertThat(platform.toString())
        .doesNotContain("SecretHandler.java")
        .doesNotContain("jane.doe")
        .doesNotContain("fp-super-secret-fingerprint");
    String mapAsString = map.toString();
    assertThat(mapAsString)
        .doesNotContain("SecretHandler.java")
        .doesNotContain("Invoice.java")
        .doesNotContain("jane.doe")
        .doesNotContain("john.smith")
        .doesNotContain("fp-super-secret-fingerprint")
        .doesNotContain("fp-another-secret-fingerprint")
        .contains("team-platform")
        .contains("team-billing")
        .contains("temporal-coupling.rule");
  }
}
