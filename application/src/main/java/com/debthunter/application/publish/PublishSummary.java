package com.debthunter.application.publish;

import java.util.Objects;

/** What happened when {@link PublishUseCase#publish} ran, and why if it failed. */
public record PublishSummary(PublishOutcome outcome, String reason) {

  /** Validates required fields; {@code reason} is intentionally nullable. */
  public PublishSummary {
    Objects.requireNonNull(outcome, "outcome");
  }

  /**
   * Nothing was attempted because no publish endpoint was configured.
   *
   * @return a summary with {@link PublishOutcome#NOT_CONFIGURED}
   */
  public static PublishSummary notConfigured() {
    return new PublishSummary(PublishOutcome.NOT_CONFIGURED, null);
  }

  /**
   * Publication was skipped because the scan was run with {@code --offline}.
   *
   * @return a summary with {@link PublishOutcome#OFFLINE_SKIPPED}
   */
  public static PublishSummary offlineSkipped() {
    return new PublishSummary(PublishOutcome.OFFLINE_SKIPPED, null);
  }

  /**
   * The result was published successfully.
   *
   * @return a summary with {@link PublishOutcome#PUBLISHED}
   */
  public static PublishSummary published() {
    return new PublishSummary(PublishOutcome.PUBLISHED, null);
  }

  /**
   * Publication was attempted and failed.
   *
   * @param reason why it failed
   * @return a summary with {@link PublishOutcome#FAILED}
   */
  public static PublishSummary failed(String reason) {
    return new PublishSummary(PublishOutcome.FAILED, reason);
  }
}
