package com.debthunter.domain;

import java.util.List;
import java.util.Objects;

/** A single breached policy rule, and the findings that caused the breach. */
public record PolicyViolation(
    String rule, String threshold, String actual, List<String> findingIds) {

  /** Validates required fields and defensively copies {@code findingIds}. */
  public PolicyViolation {
    Objects.requireNonNull(rule, "rule");
    Objects.requireNonNull(threshold, "threshold");
    Objects.requireNonNull(actual, "actual");
    findingIds = findingIds == null ? List.of() : List.copyOf(findingIds);
  }
}
