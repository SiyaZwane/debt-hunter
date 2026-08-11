package com.debthunter.ai;

import com.debthunter.domain.Finding;
import java.util.Objects;

/**
 * Builds the text sent to a model endpoint, keeping it to the finding and its evidence unless the
 * repository has explicitly opted in to sharing source code. Without opt-in, a supplied source
 * snippet is never included — not even redacted inline — so nothing beyond findings-and-evidence
 * ever leaves the process.
 */
public final class PromptGuard {

  /**
   * Builds a prompt describing {@code finding}, including {@code sourceSnippet} only if {@code
   * sourceOptIn} is {@code true}.
   *
   * @param finding the finding to describe
   * @param sourceOptIn whether the repository has opted in to sharing source code
   * @param sourceSnippet the source snippet to include if opted in, or {@code null} if unavailable
   * @return the prompt text to send to the model endpoint
   */
  public String buildPrompt(Finding finding, boolean sourceOptIn, String sourceSnippet) {
    Objects.requireNonNull(finding, "finding");
    StringBuilder prompt = new StringBuilder();
    prompt
        .append("Explain this technical-debt finding using only the finding and its evidence.\n")
        .append("rule: ")
        .append(finding.ruleId())
        .append("\n")
        .append("path: ")
        .append(finding.path())
        .append("\n")
        .append("message: ")
        .append(finding.message())
        .append("\n")
        .append("evidence: ")
        .append(finding.evidence())
        .append("\n");
    if (sourceOptIn && sourceSnippet != null && !sourceSnippet.isBlank()) {
      prompt.append("source:\n").append(sourceSnippet).append("\n");
    } else if (sourceSnippet != null && !sourceSnippet.isBlank()) {
      prompt.append("source: [redacted — repository has not opted in to sharing source code]\n");
    }
    return prompt.toString();
  }
}
