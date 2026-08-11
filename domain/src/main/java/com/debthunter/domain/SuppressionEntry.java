package com.debthunter.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A single suppression: a finding, identified by fingerprint, that {@code owner} has excused from
 * gating until {@code expiresOn}, and why.
 *
 * @param fingerprint the suppressed finding's content-anchored fingerprint
 * @param owner who approved the suppression
 * @param reason why the finding is suppressed
 * @param expiresOn the last day this suppression is active
 */
public record SuppressionEntry(
    String fingerprint, String owner, String reason, LocalDate expiresOn) {

  /** Validates required fields. */
  public SuppressionEntry {
    Objects.requireNonNull(fingerprint, "fingerprint");
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(reason, "reason");
    Objects.requireNonNull(expiresOn, "expiresOn");
  }

  /**
   * Whether this suppression is still active as of {@code date}. Deliberately anchored to a
   * caller-supplied date — the current commit's date, not the wall clock — so the same commit
   * always evaluates the same way, no matter which day the scan actually runs.
   *
   * @param date the date to check against, typically the scanned commit's date
   * @return {@code true} if {@code date} is on or before {@link #expiresOn()}
   */
  public boolean isActiveOn(LocalDate date) {
    return !expiresOn.isBefore(date);
  }
}
