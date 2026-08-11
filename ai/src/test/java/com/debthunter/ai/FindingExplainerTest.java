package com.debthunter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FindingExplainerTest {

  private static final ExplainConfig CONFIG =
      new ExplainConfig(URI.create("https://example.invalid/explain"), null, Duration.ofSeconds(5));

  @Test
  void anAvailableExplanationIsLabelledAsModelAuthored() {
    Explainer explainer = mock(Explainer.class);
    when(explainer.explain(any(), any(), any()))
        .thenReturn(Explanation.ofAvailable("f-1", "it changes with unrelated files often"));
    FindingExplainer findingExplainer = new FindingExplainer(explainer);

    ExplainedFinding result = findingExplainer.explain(finding(), CONFIG);

    assertThat(result.available()).isTrue();
    assertThat(result.explanation())
        .startsWith(ProvenanceLabeller.LABEL)
        .contains("it changes with unrelated files often");
    assertThat(result.remediation()).startsWith(ProvenanceLabeller.LABEL);
  }

  @Test
  void anUnavailableExplanationIsNotLabelled() {
    Explainer explainer = mock(Explainer.class);
    when(explainer.explain(any(), any(), any()))
        .thenReturn(Explanation.ofUnavailable("f-1", "connection refused"));
    FindingExplainer findingExplainer = new FindingExplainer(explainer);

    ExplainedFinding result = findingExplainer.explain(finding(), CONFIG);

    assertThat(result.available()).isFalse();
    assertThat(result.explanation()).isEqualTo("connection refused");
    assertThat(result.remediation()).isNull();
  }

  @Test
  void thePromptGuardBuiltPromptIsSentToTheExplainer() {
    Explainer explainer = mock(Explainer.class);
    when(explainer.explain(any(), any(), any())).thenReturn(Explanation.ofAvailable("f-1", "ok"));
    FindingExplainer findingExplainer = new FindingExplainer(explainer);

    findingExplainer.explain(finding(), CONFIG);

    verify(explainer).explain(eq(finding()), contains("rule: hotspot.rule"), eq(CONFIG));
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
}
