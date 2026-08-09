package com.debthunter.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FingerprinterTest {

  private final Fingerprinter fingerprinter = new Fingerprinter();

  @Test
  void sameInputsProduceTheSameFingerprint() {
    String first = fingerprinter.fingerprint("rule", "Foo.java", "Foo#bar", "extra");
    String second = fingerprinter.fingerprint("rule", "Foo.java", "Foo#bar", "extra");

    assertThat(first).isEqualTo(second);
  }

  @Test
  void fingerprintIsA64CharacterLowercaseHexDigest() {
    String fingerprint = fingerprinter.fingerprint("rule", "Foo.java", "", "");

    assertThat(fingerprint).hasSize(64).matches("[0-9a-f]{64}");
  }

  @Test
  void differentRuleIdProducesADifferentFingerprint() {
    String first = fingerprinter.fingerprint("rule.a", "Foo.java", "", "");
    String second = fingerprinter.fingerprint("rule.b", "Foo.java", "", "");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void differentNormalisedPathProducesADifferentFingerprint() {
    String first = fingerprinter.fingerprint("rule", "Foo.java", "", "");
    String second = fingerprinter.fingerprint("rule", "Bar.java", "", "");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void differentSymbolAnchorProducesADifferentFingerprint() {
    String first = fingerprinter.fingerprint("rule", "Foo.java", "Foo#a", "");
    String second = fingerprinter.fingerprint("rule", "Foo.java", "Foo#b", "");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void differentNormalisedEvidenceKeyProducesADifferentFingerprint() {
    String first = fingerprinter.fingerprint("rule", "Foo.java", "", "Bar.java");
    String second = fingerprinter.fingerprint("rule", "Foo.java", "", "Baz.java");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void nullSymbolAnchorIsTreatedAsEmptyString() {
    String withNull = fingerprinter.fingerprint("rule", "Foo.java", null, "");
    String withEmpty = fingerprinter.fingerprint("rule", "Foo.java", "", "");

    assertThat(withNull).isEqualTo(withEmpty);
  }

  @Test
  void componentsDoNotLeakAcrossTheSeparatorBoundary() {
    // Without a separator that cannot appear inside a component, shifting a character from the end
    // of one component to the start of the next would collide: "ab" + "c" would hash the same as
    // "a" + "bc". The NUL separator rules that out.
    String shiftedRight = fingerprinter.fingerprint("rule", "ab", "c", "");
    String shiftedLeft = fingerprinter.fingerprint("rule", "a", "bc", "");

    assertThat(shiftedRight).isNotEqualTo(shiftedLeft);
  }
}
