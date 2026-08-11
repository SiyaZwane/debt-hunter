package com.debthunter.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TransactionalOutboxTest {

  @Test
  void aSuccessfulActionIsDeliveredAndRemoved() {
    TransactionalOutbox outbox = new TransactionalOutbox();
    AtomicInteger attempts = new AtomicInteger();
    outbox.enqueue(
        "key-1",
        () -> {
          attempts.incrementAndGet();
          return true;
        });

    outbox.flush();

    assertThat(attempts.get()).isEqualTo(1);
    assertThat(outbox.isPending("key-1")).isFalse();
    assertThat(outbox.pendingCount()).isZero();
  }

  @Test
  void enqueueingTheSameKeyTwiceWhilePendingDoesNotDuplicate() {
    TransactionalOutbox outbox = new TransactionalOutbox();
    AtomicInteger firstAttempts = new AtomicInteger();
    AtomicInteger secondAttempts = new AtomicInteger();
    outbox.enqueue(
        "key-1",
        () -> {
          firstAttempts.incrementAndGet();
          return false;
        });
    outbox.enqueue(
        "key-1",
        () -> {
          secondAttempts.incrementAndGet();
          return true;
        });

    outbox.flush();

    assertThat(secondAttempts.get()).isZero();
    assertThat(firstAttempts.get()).isEqualTo(1);
  }

  @Test
  void aFailingActionStaysPendingAndIsRetriedAfterBackoffElapses() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    TransactionalOutbox outbox = new TransactionalOutbox(Duration.ofSeconds(10), clock);
    AtomicInteger attempts = new AtomicInteger();
    outbox.enqueue(
        "key-1",
        () -> {
          attempts.incrementAndGet();
          return attempts.get() >= 3;
        });

    outbox.flush();
    assertThat(attempts.get()).isEqualTo(1);
    assertThat(outbox.isPending("key-1")).isTrue();

    outbox.flush();
    assertThat(attempts.get()).isEqualTo(1);

    clock.advance(Duration.ofSeconds(10));
    outbox.flush();
    assertThat(attempts.get()).isEqualTo(2);
    assertThat(outbox.isPending("key-1")).isTrue();

    clock.advance(Duration.ofSeconds(20));
    outbox.flush();
    assertThat(attempts.get()).isEqualTo(3);
    assertThat(outbox.isPending("key-1")).isFalse();
  }

  @Test
  void anOutageThatLaterRecoversStillDeliversAtLeastOnce() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    TransactionalOutbox outbox = new TransactionalOutbox(Duration.ofSeconds(1), clock);
    AtomicInteger attempts = new AtomicInteger();
    outbox.enqueue(
        "outage-item",
        () -> {
          attempts.incrementAndGet();
          return attempts.get() > 5;
        });

    for (int i = 0; i < 5; i++) {
      outbox.flush();
      clock.advance(Duration.ofMinutes(1));
    }
    assertThat(outbox.isPending("outage-item")).isTrue();

    outbox.flush();

    assertThat(outbox.isPending("outage-item")).isFalse();
    assertThat(attempts.get()).isEqualTo(6);
  }

  @Test
  void multiplePendingEntriesAreEachDeliveredIndependently() {
    TransactionalOutbox outbox = new TransactionalOutbox();
    AtomicInteger firstAttempts = new AtomicInteger();
    AtomicInteger secondAttempts = new AtomicInteger();
    outbox.enqueue(
        "key-1",
        () -> {
          firstAttempts.incrementAndGet();
          return true;
        });
    outbox.enqueue(
        "key-2",
        () -> {
          secondAttempts.incrementAndGet();
          return true;
        });

    outbox.flush();

    assertThat(firstAttempts.get()).isEqualTo(1);
    assertThat(secondAttempts.get()).isEqualTo(1);
    assertThat(outbox.pendingCount()).isZero();
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
