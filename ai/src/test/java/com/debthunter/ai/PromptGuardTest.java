package com.debthunter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptGuardTest {

  private final PromptGuard promptGuard = new PromptGuard();

  @Test
  void thePromptAlwaysIncludesTheFindingAndItsEvidence() {
    Finding finding = finding(Map.of("changeCount", 42));

    String prompt = promptGuard.buildPrompt(finding, false, null);

    assertThat(prompt)
        .contains("rule: hotspot.rule")
        .contains("path: Foo.java")
        .contains("message: Foo.java changes often")
        .contains("changeCount")
        .contains("42");
  }

  @Test
  void withoutOptInASuppliedSourceSnippetIsNeverIncluded() {
    Finding finding = finding(Map.of());

    String prompt = promptGuard.buildPrompt(finding, false, "class Foo { void secretMethod() {} }");

    assertThat(prompt).doesNotContain("secretMethod");
  }

  @Test
  void withOptInASuppliedSourceSnippetIsIncluded() {
    Finding finding = finding(Map.of());

    String prompt = promptGuard.buildPrompt(finding, true, "class Foo { void secretMethod() {} }");

    assertThat(prompt).contains("secretMethod");
  }

  @Test
  void withoutASourceSnippetOptInHasNoEffect() {
    Finding finding = finding(Map.of());

    String prompt = promptGuard.buildPrompt(finding, true, null);

    assertThat(prompt).doesNotContain("source:");
  }

  private Finding finding(Map<String, Object> evidence) {
    return Finding.builder()
        .id("f-1")
        .ruleId("hotspot.rule")
        .category(Category.HOTSPOT)
        .severity(Severity.HIGH)
        .path("Foo.java")
        .message("Foo.java changes often")
        .evidence(evidence)
        .fingerprint("fp-1")
        .build();
  }
}
