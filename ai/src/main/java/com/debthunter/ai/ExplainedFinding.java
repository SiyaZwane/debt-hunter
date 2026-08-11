package com.debthunter.ai;

import java.util.Objects;

/**
 * The outcome of explaining and proposing a remediation for one finding: both pieces of text, or
 * why neither is available.
 */
public record ExplainedFinding(
    String findingId, boolean available, String explanation, String remediation) {

  /** Validates required fields; {@code remediation} is {@code null} when unavailable. */
  public ExplainedFinding {
    Objects.requireNonNull(findingId, "findingId");
    Objects.requireNonNull(explanation, "explanation");
  }

  /**
   * An explanation and remediation were produced.
   *
   * @param findingId the finding they explain
   * @param explanation the labelled explanation text
   * @param remediation the labelled remediation text
   * @return an available result
   */
  public static ExplainedFinding ofAvailable(
      String findingId, String explanation, String remediation) {
    return new ExplainedFinding(findingId, true, explanation, remediation);
  }

  /**
   * No explanation could be produced.
   *
   * @param findingId the finding that could not be explained
   * @param reason why not
   * @return an unavailable result
   */
  public static ExplainedFinding ofUnavailable(String findingId, String reason) {
    return new ExplainedFinding(findingId, false, reason, null);
  }
}
