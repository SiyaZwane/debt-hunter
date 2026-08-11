package com.debthunter.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Queues delivery actions and retries them with exponential backoff until each one succeeds:
 * at-least-once delivery, so a backend outage delays a publish rather than losing it. Enqueueing
 * the same key twice while it is still pending is a no-op — never a duplicate delivery attempt.
 */
public final class TransactionalOutbox {

  /** One unit of work the outbox retries until it reports success. */
  public interface Action {

    /**
     * Attempts delivery once.
     *
     * @return {@code true} if delivery succeeded and the entry can be forgotten
     */
    boolean attempt();
  }

  private record Entry(Action action, int attempts, Instant nextAttemptAt) {}

  private final Map<String, Entry> pending = new LinkedHashMap<>();
  private final Duration baseBackoff;
  private final Clock clock;

  /** Creates an outbox with a one-second base backoff, using the system clock. */
  public TransactionalOutbox() {
    this(Duration.ofSeconds(1), Clock.systemUTC());
  }

  /**
   * Creates an outbox with explicit backoff and clock, for testing.
   *
   * @param baseBackoff the delay before the first retry; doubles on each subsequent failure
   * @param clock the clock to schedule retries against
   */
  public TransactionalOutbox(Duration baseBackoff, Clock clock) {
    this.baseBackoff = Objects.requireNonNull(baseBackoff, "baseBackoff");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Enqueues {@code action} under {@code key}, unless a delivery for that key is already pending.
   *
   * @param key a stable identifier for the item being delivered
   * @param action the delivery action to retry until it succeeds
   */
  public void enqueue(String key, Action action) {
    pending.putIfAbsent(key, new Entry(action, 0, Instant.MIN));
  }

  /**
   * Attempts delivery for every pending entry whose backoff has elapsed, removing entries that
   * succeed and rescheduling entries that fail.
   */
  public void flush() {
    Instant now = clock.instant();
    for (String key : List.copyOf(pending.keySet())) {
      Entry entry = pending.get(key);
      if (entry == null || now.isBefore(entry.nextAttemptAt())) {
        continue;
      }
      if (entry.action().attempt()) {
        pending.remove(key);
      } else {
        int attempts = entry.attempts() + 1;
        pending.put(key, new Entry(entry.action(), attempts, now.plus(backoffFor(attempts))));
      }
    }
  }

  /**
   * How many deliveries are still pending.
   *
   * @return the number of entries not yet successfully delivered
   */
  public int pendingCount() {
    return pending.size();
  }

  /**
   * Whether a delivery for {@code key} is still pending.
   *
   * @param key the key to check
   * @return {@code true} if {@code key} has not yet been delivered
   */
  public boolean isPending(String key) {
    return pending.containsKey(key);
  }

  private Duration backoffFor(int attempts) {
    return baseBackoff.multipliedBy(1L << Math.min(attempts - 1, 16));
  }
}
