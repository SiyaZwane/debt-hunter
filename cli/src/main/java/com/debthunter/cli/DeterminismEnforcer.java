package com.debthunter.cli;

import java.time.ZoneOffset;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Forces the JVM's default time zone and locale to deterministic values at process startup. The
 * container image also sets {@code TZ=UTC} and {@code LC_ALL=C} as defence in depth, but this
 * enforces the same outcome unconditionally — including when the jar is run directly outside the
 * container, where no one has set those environment variables at all.
 */
public final class DeterminismEnforcer {

  private DeterminismEnforcer() {}

  /**
   * Sets the JVM default time zone to UTC and the default locale to the root (locale-neutral)
   * locale.
   */
  public static void enforce() {
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    Locale.setDefault(Locale.ROOT);
  }
}
