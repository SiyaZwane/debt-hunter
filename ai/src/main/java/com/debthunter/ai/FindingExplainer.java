package com.debthunter.ai;

import com.debthunter.domain.Finding;
import java.util.Objects;

/**
 * Explains a finding and proposes a remediation for it, guarding the model call to
 * findings-and-evidence-only and labelling every piece of generated text as model-authored.
 */
public final class FindingExplainer {

  private final Explainer explainer;
  private final PromptGuard promptGuard;
  private final RemediationAdvisor remediationAdvisor;

  /** Creates an explainer using the default {@link PromptGuard} and {@link RemediationAdvisor}. */
  public FindingExplainer(Explainer explainer) {
    this(explainer, new PromptGuard(), new RuleBasedRemediationAdvisor());
  }

  /**
   * Creates an explainer with explicit collaborators, for testing.
   *
   * @param explainer requests the explanation from the model endpoint
   * @param promptGuard builds the findings-and-evidence-only prompt
   * @param remediationAdvisor proposes the remediation
   */
  FindingExplainer(
      Explainer explainer, PromptGuard promptGuard, RemediationAdvisor remediationAdvisor) {
    this.explainer = Objects.requireNonNull(explainer, "explainer");
    this.promptGuard = Objects.requireNonNull(promptGuard, "promptGuard");
    this.remediationAdvisor = Objects.requireNonNull(remediationAdvisor, "remediationAdvisor");
  }

  /**
   * Explains {@code finding} without sharing any source code.
   *
   * @param finding the finding to explain
   * @param config where and how to request the explanation
   * @return a labelled explanation and remediation, or an unavailable result describing why not
   */
  public ExplainedFinding explain(Finding finding, ExplainConfig config) {
    return explain(finding, config, false, null);
  }

  /**
   * Explains {@code finding}, sharing {@code sourceSnippet} with the model only if {@code
   * sourceOptIn} is {@code true}.
   *
   * @param finding the finding to explain
   * @param config where and how to request the explanation
   * @param sourceOptIn whether the repository has opted in to sharing source code
   * @param sourceSnippet the source snippet to share if opted in, or {@code null}
   * @return a labelled explanation and remediation, or an unavailable result describing why not
   */
  public ExplainedFinding explain(
      Finding finding, ExplainConfig config, boolean sourceOptIn, String sourceSnippet) {
    String prompt = promptGuard.buildPrompt(finding, sourceOptIn, sourceSnippet);
    Explanation explanation = explainer.explain(finding, prompt, config);
    if (!explanation.available()) {
      return ExplainedFinding.ofUnavailable(finding.id(), explanation.text());
    }
    String remediation = remediationAdvisor.proposeRemediation(finding);
    return ExplainedFinding.ofAvailable(
        finding.id(),
        ProvenanceLabeller.label(explanation.text()),
        ProvenanceLabeller.label(remediation));
  }
}
