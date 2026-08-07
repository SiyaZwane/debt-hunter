package com.debthunter.repository;

/** What was learned about a repository by inspecting it, without walking its full history. */
public record RepositoryInfo(
    boolean isGitRepo,
    boolean isShallow,
    boolean isGrafted,
    long commitCount,
    String headCommit,
    String headBranch) {

  /**
   * The result of inspecting a path that is not a Git repository at all.
   *
   * @return a {@link RepositoryInfo} with every flag false and no commit/branch information
   */
  public static RepositoryInfo notAGitRepository() {
    return new RepositoryInfo(false, false, false, 0, null, null);
  }
}
