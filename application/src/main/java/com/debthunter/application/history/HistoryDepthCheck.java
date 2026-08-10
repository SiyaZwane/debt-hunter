package com.debthunter.application.history;

import com.debthunter.domain.HistoryDepth;

/** Whether a repository's available history satisfies a policy's minimum requirement. */
public record HistoryDepthCheck(boolean sufficient, String diagnosticMessage) {

  /**
   * The repository's history satisfies the policy's requirement (or the policy has none).
   *
   * @return a check with no diagnostic message
   */
  public static HistoryDepthCheck ofSufficient() {
    return new HistoryDepthCheck(true, null);
  }

  /**
   * The repository's history falls short of the policy's requirement.
   *
   * @param actual the repository's actual history depth
   * @param required the policy's minimum required depth
   * @return a check carrying a diagnostic explaining the shortfall
   */
  public static HistoryDepthCheck ofInsufficient(HistoryDepth actual, HistoryDepth required) {
    return new HistoryDepthCheck(
        false,
        "Repository history depth "
            + actual
            + " does not satisfy the policy's minimum of "
            + required);
  }
}
