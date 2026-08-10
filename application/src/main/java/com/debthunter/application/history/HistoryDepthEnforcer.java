package com.debthunter.application.history;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import java.util.List;
import java.util.Set;

/**
 * Enforces a policy's minimum history depth, and reduces confidence on findings whose category is
 * computed from commit history (not just current file content) when that history is incomplete.
 */
public final class HistoryDepthEnforcer {

  /** Categories computed from commit history rather than current-state file content. */
  private static final Set<Category> HISTORY_DEPENDENT_CATEGORIES =
      Set.of(
          Category.HOTSPOT,
          Category.TEMPORAL_COUPLING,
          Category.CHURN,
          Category.KNOWLEDGE_CONCENTRATION);

  /**
   * Confidence multiplier applied to history-dependent findings at {@link HistoryDepth#PARTIAL}.
   */
  private static final double PARTIAL_CONFIDENCE_FACTOR = 0.75;

  /**
   * Confidence multiplier applied to history-dependent findings at {@link HistoryDepth#SHALLOW}.
   */
  private static final double SHALLOW_CONFIDENCE_FACTOR = 0.5;

  /**
   * Checks {@code actualDepth} against a policy's {@code minimumRequired} depth.
   *
   * @param actualDepth the repository's actual history depth
   * @param minimumRequired the policy's minimum required depth, or {@code null} if the policy has
   *     no requirement
   * @return a sufficient check if {@code minimumRequired} is {@code null} or satisfied, an
   *     insufficient one otherwise
   */
  public HistoryDepthCheck check(HistoryDepth actualDepth, HistoryDepth minimumRequired) {
    if (minimumRequired == null || actualDepth.ordinal() <= minimumRequired.ordinal()) {
      return HistoryDepthCheck.ofSufficient();
    }
    return HistoryDepthCheck.ofInsufficient(actualDepth, minimumRequired);
  }

  /**
   * Returns a copy of {@code findings} with confidence reduced on every history-dependent finding,
   * proportional to how incomplete {@code actualDepth} is. Findings in categories that don't depend
   * on commit history (e.g. static analysis) are returned unchanged.
   *
   * @param findings the findings to adjust
   * @param actualDepth the repository's actual history depth
   * @return the adjusted findings, in the same order
   */
  public List<Finding> adjustConfidence(List<Finding> findings, HistoryDepth actualDepth) {
    double factor = confidenceFactor(actualDepth);
    if (factor == 1.0) {
      return findings;
    }
    return findings.stream()
        .map(finding -> isHistoryDependent(finding) ? reduceConfidence(finding, factor) : finding)
        .toList();
  }

  private boolean isHistoryDependent(Finding finding) {
    return HISTORY_DEPENDENT_CATEGORIES.contains(finding.category());
  }

  private double confidenceFactor(HistoryDepth depth) {
    return switch (depth) {
      case FULL -> 1.0;
      case PARTIAL -> PARTIAL_CONFIDENCE_FACTOR;
      case SHALLOW -> SHALLOW_CONFIDENCE_FACTOR;
    };
  }

  private Finding reduceConfidence(Finding finding, double factor) {
    return finding.withConfidence(finding.confidence() * factor);
  }
}
