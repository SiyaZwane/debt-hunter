package com.debthunter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AC-74: without an explicit opt-in, source code is never sent to the model — the prompt stays
 * limited to the finding and its evidence, even when a source snippet is available to send.
 */
class AC74_NoSourceWithoutOptInTest {

  @Test
  void ac74_aSourceSnippetIsExcludedWhenTheRepositoryHasNotOptedIn() {
    PromptGuard promptGuard = new PromptGuard();
    Finding finding =
        Finding.builder()
            .id("f-1")
            .ruleId("hotspot.rule")
            .category(Category.HOTSPOT)
            .severity(Severity.HIGH)
            .path("Foo.java")
            .message("Foo.java changes often")
            .evidence(Map.of())
            .fingerprint("fp-1")
            .build();
    String proprietarySource = "class Foo { void proprietaryAlgorithm() {} }";

    String prompt = promptGuard.buildPrompt(finding, false, proprietarySource);

    assertThat(prompt).doesNotContain("proprietaryAlgorithm");
    assertThat(prompt).contains("rule: hotspot.rule").contains("path: Foo.java");
  }
}
