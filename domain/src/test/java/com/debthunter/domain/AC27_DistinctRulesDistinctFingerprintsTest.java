package com.debthunter.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * AC-27: two findings at the exact same location, differing only by which rule produced them, must
 * never collide onto the same fingerprint.
 */
class AC27_DistinctRulesDistinctFingerprintsTest {

  @Test
  void ac27_distinctRuleIdsProduceDistinctFingerprintsForTheSameLocation() {
    Fingerprinter fingerprinter = new Fingerprinter();

    String churn = fingerprinter.fingerprint("codemaat.churn", "Foo.java", "", "");
    String hotspot = fingerprinter.fingerprint("codemaat.hotspot", "Foo.java", "", "");

    assertThat(churn).isNotEqualTo(hotspot);
  }
}
