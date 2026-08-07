package com.debthunter.repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** A single commit as seen by {@link RepositoryHistoryProvider#history}. */
public record CommitInfo(
    String id,
    String authorName,
    String authorEmail,
    Instant commitTime,
    String message,
    List<String> parentIds) {

  /** Validates required fields and defensively copies {@code parentIds}. */
  public CommitInfo {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(commitTime, "commitTime");
    parentIds = parentIds == null ? List.of() : List.copyOf(parentIds);
  }
}
