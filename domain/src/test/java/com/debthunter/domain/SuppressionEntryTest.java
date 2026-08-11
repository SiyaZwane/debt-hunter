package com.debthunter.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SuppressionEntryTest {

  private final SuppressionEntry entry =
      new SuppressionEntry("fp-1", "alice", "tracked in JIRA-123", LocalDate.parse("2026-06-01"));

  @Test
  void isActiveOnADateBeforeExpiry() {
    assertThat(entry.isActiveOn(LocalDate.parse("2026-01-01"))).isTrue();
  }

  @Test
  void isActiveOnTheExpiryDateItself() {
    assertThat(entry.isActiveOn(LocalDate.parse("2026-06-01"))).isTrue();
  }

  @Test
  void isNotActiveTheDayAfterExpiry() {
    assertThat(entry.isActiveOn(LocalDate.parse("2026-06-02"))).isFalse();
  }
}
