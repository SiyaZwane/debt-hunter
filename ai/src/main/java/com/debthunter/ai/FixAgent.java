package com.debthunter.ai;

import com.debthunter.domain.Finding;
import java.util.List;
import java.util.Objects;

/**
 * Proposes an automated fix for a finding as a pull request: never applied directly, never
 * auto-merged, and always subject to the target repository's normal CI pipeline and review, like
 * any other change. Rate-limited per repository so a burst of findings cannot flood a repository
 * with pull requests.
 */
public final class FixAgent {

  private static final List<String> LABELS = List.of("auto-generated", "debt-hunter");

  private static final String CI_NOTICE =
      "This pull request is auto-generated. It is subject to this repository's normal CI "
          + "pipeline and review process like any other change, and is never merged automatically.";

  private final SourceHostClient sourceHostClient;
  private final FixAgentRateLimiter rateLimiter;
  private final RemediationAdvisor remediationAdvisor;

  /**
   * Constructs the agent with the default, rule-based remediation advisor.
   *
   * @param sourceHostClient opens the pull request
   * @param rateLimiter limits proposals per repository
   */
  public FixAgent(SourceHostClient sourceHostClient, FixAgentRateLimiter rateLimiter) {
    this(sourceHostClient, rateLimiter, new RuleBasedRemediationAdvisor());
  }

  /**
   * Constructs the agent with an explicit remediation advisor, for testing.
   *
   * @param sourceHostClient opens the pull request
   * @param rateLimiter limits proposals per repository
   * @param remediationAdvisor proposes the patch content
   */
  FixAgent(
      SourceHostClient sourceHostClient,
      FixAgentRateLimiter rateLimiter,
      RemediationAdvisor remediationAdvisor) {
    this.sourceHostClient = Objects.requireNonNull(sourceHostClient, "sourceHostClient");
    this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
    this.remediationAdvisor = Objects.requireNonNull(remediationAdvisor, "remediationAdvisor");
  }

  /**
   * Proposes a fix for {@code finding} in {@code repository}, opening a pull request against {@code
   * baseBranch} unless the repository is currently rate-limited.
   *
   * @param finding the finding to address
   * @param repository the repository identifier the fix targets
   * @param baseBranch the branch the pull request targets
   * @return the outcome: created with a URL, or rejected (rate limit or source-host failure)
   */
  public PullRequestResult proposeFix(Finding finding, String repository, String baseBranch) {
    if (!rateLimiter.tryAcquire(repository)) {
      return PullRequestResult.rejected("Rate limit exceeded for repository " + repository);
    }
    PullRequestRequest request =
        new PullRequestRequest(
            repository,
            branchNameFor(finding),
            baseBranch,
            "Auto-fix: " + finding.ruleId(),
            description(finding),
            patch(finding),
            LABELS);
    return sourceHostClient.openPullRequest(request);
  }

  static String branchNameFor(Finding finding) {
    return "debt-hunter/fix-" + finding.id();
  }

  private String patch(Finding finding) {
    return "# Proposed remediation for "
        + finding.path()
        + " (finding "
        + finding.id()
        + ")\n"
        + remediationAdvisor.proposeRemediation(finding)
        + "\n";
  }

  private String description(Finding finding) {
    return "[auto-generated] Addresses finding "
        + finding.id()
        + " ("
        + finding.ruleId()
        + ") in "
        + finding.path()
        + ".\n\n"
        + CI_NOTICE;
  }
}
