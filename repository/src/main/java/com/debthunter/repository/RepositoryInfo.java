package com.debthunter.repository;

import java.time.Instant;

/**
 * What was learned about a repository by inspecting it, without walking its full history.
 *
 * @param isGitRepo whether the path is a Git repository at all
 * @param isShallow whether the clone is shallow
 * @param isGrafted whether the clone has history grafts
 * @param commitCount how many commits are reachable from HEAD
 * @param headCommit HEAD's commit SHA, or {@code null} if there is no HEAD (e.g. an empty repo)
 * @param headBranch the currently checked-out branch name
 * @param headCommitDate HEAD's commit timestamp, or {@code null} if there is no HEAD
 */
public record RepositoryInfo(
    boolean isGitRepo,
    boolean isShallow,
    boolean isGrafted,
    long commitCount,
    String headCommit,
    String headBranch,
    Instant headCommitDate) {

  /**
   * Convenience constructor predating {@link #headCommitDate()}, used by every call site that
   * doesn't need a commit date; defaults it to {@code null}.
   *
   * @param isGitRepo whether the path is a Git repository at all
   * @param isShallow whether the clone is shallow
   * @param isGrafted whether the clone has history grafts
   * @param commitCount how many commits are reachable from HEAD
   * @param headCommit HEAD's commit SHA, or {@code null} if there is no HEAD
   * @param headBranch the currently checked-out branch name
   */
  public RepositoryInfo(
      boolean isGitRepo,
      boolean isShallow,
      boolean isGrafted,
      long commitCount,
      String headCommit,
      String headBranch) {
    this(isGitRepo, isShallow, isGrafted, commitCount, headCommit, headBranch, null);
  }

  /**
   * The result of inspecting a path that is not a Git repository at all.
   *
   * @return a {@link RepositoryInfo} with every flag false and no commit/branch information
   */
  public static RepositoryInfo notAGitRepository() {
    return new RepositoryInfo(false, false, false, 0, null, null, null);
  }
}
