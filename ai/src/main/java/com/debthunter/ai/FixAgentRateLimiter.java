package com.debthunter.ai;

/** Limits how often {@link FixAgent} may propose a fix for a given repository. */
public interface FixAgentRateLimiter {

  /**
   * Attempts to acquire permission to propose a fix for {@code repository} now.
   *
   * @param repository the repository identifier the fix would target
   * @return {@code true} if permitted (and the attempt is now counted), {@code false} if the
   *     repository is currently rate-limited
   */
  boolean tryAcquire(String repository);
}
