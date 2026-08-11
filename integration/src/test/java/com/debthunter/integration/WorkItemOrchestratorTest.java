package com.debthunter.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkItemOrchestratorTest {

  private final FakeWorkItemProvider provider = new FakeWorkItemProvider();
  private final WorkItemOrchestrator orchestrator = new WorkItemOrchestrator(provider);

  @Test
  void aNewEligibleFindingCreatesExactlyOneItem() {
    WorkItemSyncResult result =
        orchestrator.sync(
            List.of(finding("fp-1", Severity.CRITICAL)),
            Map.of(),
            RolloutStage.ENFORCE,
            Severity.HIGH);

    assertThat(result.created()).containsExactly("fp-1");
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get("fp-1").open()).isTrue();
    assertThat(provider.created).hasSize(1);
  }

  @Test
  void reSyncingTheSameOpenFindingCreatesNoSecondItem() {
    Map<String, WorkItem> existing = Map.of("fp-1", new WorkItem("fp-1", "ITEM-1", true));

    WorkItemSyncResult result =
        orchestrator.sync(
            List.of(finding("fp-1", Severity.CRITICAL)),
            existing,
            RolloutStage.ENFORCE,
            Severity.HIGH);

    assertThat(result.created()).isEmpty();
    assertThat(provider.created).isEmpty();
    assertThat(result.items().get("fp-1")).isEqualTo(existing.get("fp-1"));
  }

  @Test
  void aFindingThatDisappearsClosesItsItem() {
    Map<String, WorkItem> existing = Map.of("fp-1", new WorkItem("fp-1", "ITEM-1", true));

    WorkItemSyncResult result =
        orchestrator.sync(List.of(), existing, RolloutStage.ENFORCE, Severity.HIGH);

    assertThat(result.closed()).containsExactly("fp-1");
    assertThat(provider.closed).containsExactly("ITEM-1");
    assertThat(result.items().get("fp-1").open()).isFalse();
  }

  @Test
  void aFindingThatReappearsAfterClosureReopensRatherThanRecreates() {
    Map<String, WorkItem> existing = Map.of("fp-1", new WorkItem("fp-1", "ITEM-1", false));

    WorkItemSyncResult result =
        orchestrator.sync(
            List.of(finding("fp-1", Severity.CRITICAL)),
            existing,
            RolloutStage.ENFORCE,
            Severity.HIGH);

    assertThat(result.reopened()).containsExactly("fp-1");
    assertThat(result.created()).isEmpty();
    assertThat(provider.reopened).containsExactly("ITEM-1");
    assertThat(provider.created).isEmpty();
    assertThat(result.items().get("fp-1").open()).isTrue();
  }

  @Test
  void findingsBelowTheSeverityFloorAreNotPublished() {
    WorkItemSyncResult result =
        orchestrator.sync(
            List.of(finding("fp-1", Severity.LOW)), Map.of(), RolloutStage.ENFORCE, Severity.HIGH);

    assertThat(result.created()).isEmpty();
    assertThat(provider.created).isEmpty();
  }

  @Test
  void observeStageNeverCreatesClosesOrReopens() {
    Map<String, WorkItem> existing = Map.of("fp-2", new WorkItem("fp-2", "ITEM-2", true));

    WorkItemSyncResult result =
        orchestrator.sync(
            List.of(finding("fp-1", Severity.CRITICAL)),
            existing,
            RolloutStage.OBSERVE,
            Severity.HIGH);

    assertThat(result.created()).isEmpty();
    assertThat(result.closed()).isEmpty();
    assertThat(result.reopened()).isEmpty();
    assertThat(provider.created).isEmpty();
    assertThat(provider.closed).isEmpty();
    assertThat(result.items()).isEqualTo(existing);
  }

  static Finding finding(String fingerprint, Severity severity) {
    return Finding.builder()
        .id("f-" + fingerprint)
        .ruleId("hotspot.rule")
        .category(Category.HOTSPOT)
        .severity(severity)
        .path("Foo.java")
        .message("Foo.java is overdue")
        .fingerprint(fingerprint)
        .build();
  }
}
