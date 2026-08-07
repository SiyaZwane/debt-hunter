package com.debthunter.repository;

import java.time.Instant;

/** A bound on how much history {@link RepositoryHistoryProvider#history} should return. */
public record HistoryWindow(Instant since, Integer maxCommits) {

  /**
   * No bound: the entire available history.
   *
   * @return a window with no {@code since} or {@code maxCommits} limit
   */
  public static HistoryWindow all() {
    return new HistoryWindow(null, null);
  }

  /**
   * Only commits made on or after the given instant.
   *
   * @param since the earliest commit time to include
   * @return a window bounded by {@code since}
   */
  public static HistoryWindow since(Instant since) {
    return new HistoryWindow(since, null);
  }
}
