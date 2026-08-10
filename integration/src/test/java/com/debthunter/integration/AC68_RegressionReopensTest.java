package com.debthunter.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Severity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AC-68: when a fingerprint whose tracker item was closed reappears among the current findings, the
 * orchestrator reopens the existing item rather than creating a new one.
 */
class AC68_RegressionReopensTest {

  @Test
  void ac68_aRegressedFindingReopensItsExistingTrackerItem() {
    FakeWorkItemProvider provider = new FakeWorkItemProvider();
    WorkItemOrchestrator orchestrator = new WorkItemOrchestrator(provider);
    Map<String, WorkItem> existing = Map.of("fp-1", new WorkItem("fp-1", "ITEM-1", false));

    WorkItemSyncResult result =
        orchestrator.sync(
            List.of(WorkItemOrchestratorTest.finding("fp-1", Severity.CRITICAL)),
            existing,
            RolloutStage.ENFORCE,
            Severity.HIGH);

    assertThat(result.reopened()).containsExactly("fp-1");
    assertThat(provider.reopened).containsExactly("ITEM-1");
    assertThat(provider.created).isEmpty();
    assertThat(result.items().get("fp-1").open()).isTrue();
  }
}
