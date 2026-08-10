package com.debthunter.application.publish;

/** What {@link PublishUseCase#publish} actually did. */
public enum PublishOutcome {
  /** No publish endpoint was configured; nothing was attempted. */
  NOT_CONFIGURED,
  /** {@code --offline} was set; publication was skipped even though it was configured. */
  OFFLINE_SKIPPED,
  /** The result was published successfully. */
  PUBLISHED,
  /** Publication was attempted and failed. */
  FAILED
}
