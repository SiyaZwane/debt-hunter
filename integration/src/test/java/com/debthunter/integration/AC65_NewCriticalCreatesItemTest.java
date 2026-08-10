package com.debthunter.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Severity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AC-65: a new CRITICAL finding, at rollout stage ENFORCE, causes the orchestrator to create
 * exactly one tracker item for it.
 */
class AC65_NewCriticalCreatesItemTest {

  @Test
  void ac65_aNewCriticalFindingCreatesATrackerItem() {
    FakeWorkItemProvider provider = new FakeWorkItemProvider();
    WorkItemOrchestrator orchestrator = new WorkItemOrchestrator(provider);

    WorkItemSyncResult result =
        orchestrator.sync(
            List.of(WorkItemOrchestratorTest.finding("fp-critical", Severity.CRITICAL)),
            Map.of(),
            RolloutStage.ENFORCE,
            Severity.HIGH);

    assertThat(result.created()).containsExactly("fp-critical");
    assertThat(provider.created).hasSize(1);
    assertThat(result.items().get("fp-critical").open()).isTrue();
  }
}
