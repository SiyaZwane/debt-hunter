package com.debthunter.policy;

import com.debthunter.domain.Category;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.engine.spi.AnalysisMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A validated, typed policy bundle: which history depth a scan requires, which thresholds gate a
 * {@code main}-branch scan versus a pull-request scan, which findings are excluded from gating
 * entirely, and how long a suppression may stay active.
 *
 * <p>{@code suppressionsMaxExpiryDays} is parsed but not yet enforced — suppression loading and
 * expiry checking is a later FR's scope (a {@code SuppressionRegistry} that doesn't exist yet).
 */
public record PolicyBundle(
    String version,
    Map<String, Object> metadata,
    HistoryDepth minimumHistoryDepth,
    List<PolicyRule> mainRules,
    List<PolicyRule> pullRequestRules,
    List<Category> excludedCategories,
    List<String> excludedPaths,
    int suppressionsMaxExpiryDays) {

  /** Validates required fields and defensively copies every collection. */
  public PolicyBundle {
    Objects.requireNonNull(version, "version");
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    mainRules = mainRules == null ? List.of() : List.copyOf(mainRules);
    pullRequestRules = pullRequestRules == null ? List.of() : List.copyOf(pullRequestRules);
    excludedCategories = excludedCategories == null ? List.of() : List.copyOf(excludedCategories);
    excludedPaths = excludedPaths == null ? List.of() : List.copyOf(excludedPaths);
  }

  /**
   * The bundle used when no policy file is configured: no rules, no history requirement, nothing
   * excluded. Every scan passes.
   *
   * @return a bundle with no rules in either rule set
   */
  public static PolicyBundle permissive() {
    return new PolicyBundle("1.0", Map.of(), null, List.of(), List.of(), List.of(), List.of(), 0);
  }

  /**
   * The rule set that applies to a scan running in {@code mode}.
   *
   * @param mode the scan's analysis mode
   * @return {@link #pullRequestRules()} for {@link AnalysisMode#PULL_REQUEST}, {@link #mainRules()}
   *     otherwise
   */
  public List<PolicyRule> rulesFor(AnalysisMode mode) {
    return mode == AnalysisMode.PULL_REQUEST ? pullRequestRules : mainRules;
  }
}
