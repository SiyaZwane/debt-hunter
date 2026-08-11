package com.debthunter.integration;

import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps a tracker backend in sync with the current findings: one item per fingerprint, created no
 * more than once, closed when its finding disappears, and reopened — never recreated — when a
 * closed finding's fingerprint reappears. Creation is gated by both the policy's severity floor and
 * the repository's {@link RolloutStage}; during {@link RolloutStage#OBSERVE} nothing is created,
 * closed, or reopened.
 */
public final class WorkItemOrchestrator {

  private final WorkItemProvider provider;

  /**
   * Creates an orchestrator publishing through {@code provider}.
   *
   * @param provider the tracker backend to create, close, and reopen items on
   */
  public WorkItemOrchestrator(WorkItemProvider provider) {
    this.provider = Objects.requireNonNull(provider, "provider");
  }

  /**
   * Syncs tracker state against the current findings.
   *
   * @param currentFindings findings from the latest scan
   * @param existingItems tracker state carried forward from the previous sync, keyed by fingerprint
   * @param stage the repository's rollout stage
   * @param minSeverity the minimum severity a finding must have to warrant a tracker item
   * @return the updated tracker state and the actions taken this sync
   */
  public WorkItemSyncResult sync(
      List<Finding> currentFindings,
      Map<String, WorkItem> existingItems,
      RolloutStage stage,
      Severity minSeverity) {
    if (stage != RolloutStage.ENFORCE) {
      return WorkItemSyncResult.unchanged(existingItems);
    }

    Map<String, Finding> eligible = new LinkedHashMap<>();
    for (Finding finding : currentFindings) {
      if (finding.severity().ordinal() <= minSeverity.ordinal()) {
        eligible.putIfAbsent(finding.fingerprint(), finding);
      }
    }

    Map<String, WorkItem> updated = new LinkedHashMap<>(existingItems);
    List<String> created = new ArrayList<>();
    List<String> closed = new ArrayList<>();
    List<String> reopened = new ArrayList<>();

    for (Map.Entry<String, Finding> entry : eligible.entrySet()) {
      String fingerprint = entry.getKey();
      WorkItem existing = updated.get(fingerprint);
      if (existing == null) {
        String externalId = provider.createItem(entry.getValue());
        updated.put(fingerprint, new WorkItem(fingerprint, externalId, true));
        created.add(fingerprint);
      } else if (!existing.open()) {
        provider.reopenItem(existing.externalId());
        updated.put(fingerprint, new WorkItem(fingerprint, existing.externalId(), true));
        reopened.add(fingerprint);
      }
    }

    for (WorkItem existing : List.copyOf(updated.values())) {
      if (existing.open() && !eligible.containsKey(existing.fingerprint())) {
        provider.closeItem(existing.externalId());
        updated.put(
            existing.fingerprint(),
            new WorkItem(existing.fingerprint(), existing.externalId(), false));
        closed.add(existing.fingerprint());
      }
    }

    return new WorkItemSyncResult(updated, created, closed, reopened);
  }
}
