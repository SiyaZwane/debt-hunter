package com.debthunter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FindingTest {

  private Finding.Builder validBuilder() {
    return Finding.builder()
        .id("f-1")
        .ruleId("hotspot.large-file")
        .category(Category.HOTSPOT)
        .severity(Severity.HIGH)
        .confidence(0.9)
        .path("src/main/java/Foo.java")
        .startLine(42)
        .message("Foo.java is a change hotspot")
        .evidence(Map.of("changeFrequency", 12.0))
        .score(7.5)
        .isNew(true)
        .fingerprint("abc123");
  }

  @Test
  void ac01_buildsWithAllFieldsPopulated() {
    Finding finding = validBuilder().build();

    assertThat(finding.id()).isEqualTo("f-1");
    assertThat(finding.ruleId()).isEqualTo("hotspot.large-file");
    assertThat(finding.category()).isEqualTo(Category.HOTSPOT);
    assertThat(finding.severity()).isEqualTo(Severity.HIGH);
    assertThat(finding.confidence()).isEqualTo(0.9);
    assertThat(finding.path()).isEqualTo("src/main/java/Foo.java");
    assertThat(finding.startLine()).isEqualTo(42);
    assertThat(finding.message()).isEqualTo("Foo.java is a change hotspot");
    assertThat(finding.evidence()).containsEntry("changeFrequency", 12.0);
    assertThat(finding.score()).isEqualTo(7.5);
    assertThat(finding.isNew()).isTrue();
    assertThat(finding.fingerprint()).isEqualTo("abc123");
  }

  @Test
  void evidenceMapIsDefensivelyCopiedAndImmutable() {
    var mutableEvidence = new java.util.HashMap<String, Object>();
    mutableEvidence.put("engine", "code-maat");
    Finding finding = validBuilder().evidence(mutableEvidence).build();

    mutableEvidence.put("engine", "mutated-after-build");

    assertThat(finding.evidence()).containsEntry("engine", "code-maat");
    assertThatThrownBy(() -> finding.evidence().put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void nullEvidenceDefaultsToEmptyMap() {
    Finding finding = validBuilder().evidence(null).build();

    assertThat(finding.evidence()).isEmpty();
  }

  @Test
  void requiresNonNullId() {
    assertThatThrownBy(() -> validBuilder().id(null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("id");
  }

  @Test
  void requiresNonNullFingerprint() {
    assertThatThrownBy(() -> validBuilder().fingerprint(null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fingerprint");
  }

  @Test
  void findingsWithSameFieldsAreEqual() {
    Finding a = validBuilder().build();
    Finding b = validBuilder().build();

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  void findingsWithDifferentIdsAreNotEqual() {
    Finding a = validBuilder().id("f-1").build();
    Finding b = validBuilder().id("f-2").build();

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void evidenceViewExposesTypedAccessors() {
    Finding finding =
        validBuilder().evidence(Map.of("changeFrequency", 12.0, "engine", "code-maat")).build();

    Evidence evidence = finding.evidenceView();

    assertThat(evidence.changeFrequency()).contains(12.0);
    assertThat(evidence.engine()).contains("code-maat");
  }
}
