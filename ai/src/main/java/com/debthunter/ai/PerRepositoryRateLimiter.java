package com.debthunter.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A sliding-window rate limiter tracked independently per repository, so one noisy repository
 * cannot exhaust another's quota. Uses an injectable {@link Clock} so tests can advance time
 * deterministically rather than sleeping.
 */
public final class PerRepositoryRateLimiter implements FixAgentRateLimiter {

  private final int maxPerWindow;
  private final Duration window;
  private final Clock clock;
  private final Map<String, Deque<Instant>> attemptsByRepository = new ConcurrentHashMap<>();

  /**
   * Creates a rate limiter permitting at most {@code maxPerWindow} accepted attempts per repository
   * within any trailing {@code window} of time.
   *
   * @param maxPerWindow the maximum accepted attempts per repository per window
   * @param window the trailing duration over which attempts are counted
   * @param clock the clock attempts are timestamped against
   */
  public PerRepositoryRateLimiter(int maxPerWindow, Duration window, Clock clock) {
    if (maxPerWindow <= 0) {
      throw new IllegalArgumentException("maxPerWindow must be positive");
    }
    this.maxPerWindow = maxPerWindow;
    this.window = Objects.requireNonNull(window, "window");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public synchronized boolean tryAcquire(String repository) {
    Instant now = clock.instant();
    Deque<Instant> attempts =
        attemptsByRepository.computeIfAbsent(repository, ignored -> new ArrayDeque<>());
    Instant windowStart = now.minus(window);
    while (!attempts.isEmpty() && attempts.peekFirst().isBefore(windowStart)) {
      attempts.pollFirst();
    }
    if (attempts.size() >= maxPerWindow) {
      return false;
    }
    attempts.addLast(now);
    return true;
  }
}
