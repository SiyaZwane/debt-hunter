package com.debthunter.domain;

/** How much commit history was available to engines that need it. */
public enum HistoryDepth {
  FULL,
  PARTIAL,
  SHALLOW;

  /**
   * Human-readable label for this depth, suitable for CLI output and reports.
   *
   * @return a lower-case display label
   */
  public String displayName() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
