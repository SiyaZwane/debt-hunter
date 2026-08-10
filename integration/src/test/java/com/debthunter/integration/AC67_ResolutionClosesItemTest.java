package com.debthunter.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Severity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AC-67: once a fingerprint that had an open tracker item no longer appears among the current
 * findings, the orchestrator closes that item.
 */
class AC67_ResolutionClosesItemTest {

  @Test
  void ac67_aResolvedFindingClosesItsTrackerItem() {
    FakeWorkItemProvider provider = new FakeWorkItemProvider();
    WorkItemOrchestrator orchestrator = new WorkItemOrchestrator(provider);
    Map<String, WorkItem> existing = Map.of("fp-1", new WorkItem("fp-1", "ITEM-1", true));

    WorkItemSyncResult result =
        orchestrator.sync(List.of(), existing, RolloutStage.ENFORCE, Severity.HIGH);

    assertThat(result.closed()).containsExactly("fp-1");
    assertThat(provider.closed).containsExactly("ITEM-1");
    assertThat(result.items().get("fp-1").open()).isFalse();
  }
}
