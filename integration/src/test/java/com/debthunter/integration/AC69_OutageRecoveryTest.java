package com.debthunter.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * AC-69: a publish that fails while the backend is down stays queued and is delivered once the
 * backend recovers — an outage delays delivery, it never drops it.
 */
class AC69_OutageRecoveryTest {

  @Test
  void ac69_aDeliveryThatFailsDuringAnOutageSucceedsOnceTheBackendRecovers() {
    RecoveringClock clock = new RecoveringClock(Instant.parse("2026-01-01T00:00:00Z"));
    TransactionalOutbox outbox = new TransactionalOutbox(Duration.ofSeconds(5), clock);
    AtomicInteger attempts = new AtomicInteger();
    AtomicBooleanLike backendUp = new AtomicBooleanLike(false);
    outbox.enqueue(
        "work-item-1",
        () -> {
          attempts.incrementAndGet();
          return backendUp.value;
        });

    outbox.flush();
    assertThat(outbox.isPending("work-item-1")).isTrue();
    assertThat(attempts.get()).isEqualTo(1);

    clock.advance(Duration.ofSeconds(5));
    outbox.flush();
    assertThat(outbox.isPending("work-item-1")).isTrue();
    assertThat(attempts.get()).isEqualTo(2);

    backendUp.value = true;
    clock.advance(Duration.ofSeconds(10));
    outbox.flush();

    assertThat(outbox.isPending("work-item-1")).isFalse();
    assertThat(attempts.get()).isEqualTo(3);
  }

  private static final class AtomicBooleanLike {
    private volatile boolean value;

    AtomicBooleanLike(boolean value) {
      this.value = value;
    }
  }

  private static final class RecoveringClock extends Clock {
    private Instant instant;

    RecoveringClock(Instant instant) {
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
