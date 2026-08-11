package com.debthunter.policy;

/** A suppression entry's expiry exceeds the policy's {@code suppressions.maxExpiryDays}. */
public final class SuppressionRejectedException extends RuntimeException {

  /**
   * Creates the exception.
   *
   * @param message a human-readable description of which entry was rejected, and why
   */
  public SuppressionRejectedException(String message) {
    super(message);
  }
}
