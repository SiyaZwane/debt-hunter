package com.debthunter.ai;

import java.util.Objects;

/**
 * The outcome of asking for an explanation of one finding: the text itself, or why there isn't one.
 */
public record Explanation(String findingId, boolean available, String text) {

  /** Validates required fields. */
  public Explanation {
    Objects.requireNonNull(findingId, "findingId");
    Objects.requireNonNull(text, "text");
  }

  /**
   * An explanation was produced.
   *
   * @param findingId the finding it explains
   * @param text the explanation itself
   * @return an available explanation
   */
  public static Explanation ofAvailable(String findingId, String text) {
    return new Explanation(findingId, true, text);
  }

  /**
   * No explanation could be produced.
   *
   * @param findingId the finding that could not be explained
   * @param reason why not
   * @return an unavailable explanation
   */
  public static Explanation ofUnavailable(String findingId, String reason) {
    return new Explanation(findingId, false, reason);
  }
}
