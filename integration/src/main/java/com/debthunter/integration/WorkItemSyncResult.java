package com.debthunter.integration;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The outcome of one {@link WorkItemOrchestrator} sync: the tracker state to carry into the next
 * sync, plus which fingerprints were created, closed, or reopened this time.
 */
public record WorkItemSyncResult(
    Map<String, WorkItem> items, List<String> created, List<String> closed, List<String> reopened) {

  /** Validates required fields and defensively copies every collection. */
  public WorkItemSyncResult {
    Objects.requireNonNull(items, "items");
    Objects.requireNonNull(created, "created");
    Objects.requireNonNull(closed, "closed");
    Objects.requireNonNull(reopened, "reopened");
    items = Map.copyOf(items);
    created = List.copyOf(created);
    closed = List.copyOf(closed);
    reopened = List.copyOf(reopened);
  }

  /**
   * A sync that took no action, carrying forward the tracker state unchanged.
   *
   * @param items the unchanged tracker state
   * @return a no-op result
   */
  public static WorkItemSyncResult unchanged(Map<String, WorkItem> items) {
    return new WorkItemSyncResult(items, List.of(), List.of(), List.of());
  }
}
