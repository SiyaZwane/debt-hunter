package com.debthunter.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Severity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AC-66: syncing the same still-open finding across multiple runs never creates a second tracker
 * item for the same fingerprint.
 */
class AC66_NoDuplicatesTest {

  @Test
  void ac66_repeatedSyncsOfTheSameOpenFindingCreateOnlyOneItem() {
    FakeWorkItemProvider provider = new FakeWorkItemProvider();
    WorkItemOrchestrator orchestrator = new WorkItemOrchestrator(provider);

    WorkItemSyncResult first =
        orchestrator.sync(
            List.of(WorkItemOrchestratorTest.finding("fp-1", Severity.CRITICAL)),
            Map.of(),
            RolloutStage.ENFORCE,
            Severity.HIGH);
    WorkItemSyncResult second =
        orchestrator.sync(
            List.of(WorkItemOrchestratorTest.finding("fp-1", Severity.CRITICAL)),
            first.items(),
            RolloutStage.ENFORCE,
            Severity.HIGH);
    WorkItemSyncResult third =
        orchestrator.sync(
            List.of(WorkItemOrchestratorTest.finding("fp-1", Severity.CRITICAL)),
            second.items(),
            RolloutStage.ENFORCE,
            Severity.HIGH);

    assertThat(provider.created).hasSize(1);
    assertThat(second.created()).isEmpty();
    assertThat(third.created()).isEmpty();
  }
}
