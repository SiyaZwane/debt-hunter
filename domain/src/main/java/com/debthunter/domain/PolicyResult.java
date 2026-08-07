package com.debthunter.domain;

import java.util.List;
import java.util.Objects;

/** The outcome of evaluating a policy bundle against a scan's findings. */
public record PolicyResult(
    String bundleVersion, PolicyStatus status, List<PolicyViolation> reasons) {

  /** Validates required fields and defensively copies {@code reasons}. */
  public PolicyResult {
    Objects.requireNonNull(bundleVersion, "bundleVersion");
    Objects.requireNonNull(status, "status");
    reasons = reasons == null ? List.of() : List.copyOf(reasons);
  }

  /**
   * A passing result with no violations.
   *
   * @param bundleVersion the policy bundle version that was evaluated
   * @return a {@link PolicyResult} with status {@link PolicyStatus#PASSED} and no reasons
   */
  public static PolicyResult passed(String bundleVersion) {
    return new PolicyResult(bundleVersion, PolicyStatus.PASSED, List.of());
  }
}
