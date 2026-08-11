package com.debthunter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * AC-80: {@link FixAgent} proposals are rate-limited per repository — a burst against one
 * repository cannot exhaust another's quota, and the limit recovers once the window elapses.
 */
class AC80_RateLimitTest {

  @Test
  void ac80_aRepositoryIsRejectedOnceItsWindowQuotaIsExhausted() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    PerRepositoryRateLimiter rateLimiter =
        new PerRepositoryRateLimiter(2, Duration.ofMinutes(10), clock);

    assertThat(rateLimiter.tryAcquire("acme/widgets")).isTrue();
    assertThat(rateLimiter.tryAcquire("acme/widgets")).isTrue();
    assertThat(rateLimiter.tryAcquire("acme/widgets")).isFalse();
  }

  @Test
  void ac80_theLimitRecoversOnceTheWindowElapses() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    PerRepositoryRateLimiter rateLimiter =
        new PerRepositoryRateLimiter(1, Duration.ofMinutes(10), clock);

    assertThat(rateLimiter.tryAcquire("acme/widgets")).isTrue();
    assertThat(rateLimiter.tryAcquire("acme/widgets")).isFalse();

    clock.advance(Duration.ofMinutes(11));

    assertThat(rateLimiter.tryAcquire("acme/widgets")).isTrue();
  }

  @Test
  void ac80_repositoriesAreRateLimitedIndependently() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    PerRepositoryRateLimiter rateLimiter =
        new PerRepositoryRateLimiter(1, Duration.ofMinutes(10), clock);

    assertThat(rateLimiter.tryAcquire("acme/widgets")).isTrue();
    assertThat(rateLimiter.tryAcquire("acme/widgets")).isFalse();
    assertThat(rateLimiter.tryAcquire("acme/gadgets")).isTrue();
  }

  @Test
  void ac80_aFixAgentRejectedByTheRateLimiterNeverCallsTheSourceHost() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    PerRepositoryRateLimiter rateLimiter =
        new PerRepositoryRateLimiter(1, Duration.ofMinutes(10), clock);
    SourceHostClient client = mock(SourceHostClient.class);
    when(client.openPullRequest(any()))
        .thenReturn(PullRequestResult.created("https://example.invalid/pulls/1"));
    FixAgent agent = new FixAgent(client, rateLimiter);

    PullRequestResult first = agent.proposeFix(finding(), "acme/widgets", "main");
    PullRequestResult second = agent.proposeFix(finding(), "acme/widgets", "main");

    assertThat(first.created()).isTrue();
    assertThat(second.created()).isFalse();
    assertThat(second.reason()).contains("acme/widgets");
    verify(client, times(1)).openPullRequest(any());
  }

  private Finding finding() {
    return Finding.builder()
        .id("f-1")
        .ruleId("hotspot.rule")
        .category(Category.HOTSPOT)
        .severity(Severity.HIGH)
        .path("Foo.java")
        .message("Foo.java changes often")
        .fingerprint("fp-1")
        .build();
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
