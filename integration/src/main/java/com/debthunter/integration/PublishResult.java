package com.debthunter.integration;

/** The outcome of one publish attempt: whether it succeeded, and why not if it didn't. */
public record PublishResult(boolean success, String reason) {

  /**
   * The publish attempt succeeded.
   *
   * @return a successful result with no reason
   */
  public static PublishResult ofSuccess() {
    return new PublishResult(true, null);
  }

  /**
   * The publish attempt failed.
   *
   * @param reason a human-readable explanation
   * @return a failed result carrying {@code reason}
   */
  public static PublishResult ofFailure(String reason) {
    return new PublishResult(false, reason);
  }
}
