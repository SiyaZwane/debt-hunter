package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DeterminismEnforcerTest {

  private TimeZone originalTimeZone;
  private Locale originalLocale;

  @AfterEach
  void restoreOriginalDefaults() {
    if (originalTimeZone != null) {
      TimeZone.setDefault(originalTimeZone);
    }
    if (originalLocale != null) {
      Locale.setDefault(originalLocale);
    }
  }

  @Test
  void enforceSetsTheDefaultTimeZoneToUtcAndTheDefaultLocaleToRoot() {
    originalTimeZone = TimeZone.getDefault();
    originalLocale = Locale.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    Locale.setDefault(Locale.GERMANY);

    DeterminismEnforcer.enforce();

    assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    assertThat(Locale.getDefault()).isEqualTo(Locale.ROOT);
  }
}
