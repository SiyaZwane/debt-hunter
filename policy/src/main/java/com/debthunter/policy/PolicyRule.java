package com.debthunter.policy;

import com.debthunter.domain.Severity;
import java.util.Objects;

/**
 * A single threshold: at most {@code maxCount} new findings at severity {@code minSeverity} or more
 * severe are tolerated.
 */
public record PolicyRule(String id, Severity minSeverity, int maxCount) {

  /** Validates required fields and rejects a negative threshold. */
  public PolicyRule {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(minSeverity, "minSeverity");
    if (maxCount < 0) {
      throw new IllegalArgumentException("maxCount must not be negative: " + maxCount);
    }
  }
}
