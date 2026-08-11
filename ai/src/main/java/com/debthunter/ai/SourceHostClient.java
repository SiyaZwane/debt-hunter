package com.debthunter.ai;

/** Abstracts the source-hosting API (e.g. GitHub, GitLab, Azure Repos) that hosts pull requests. */
public interface SourceHostClient {

  /**
   * Opens a single pull request.
   *
   * @param request the branch, base, title, description, patch, and labels to open it with
   * @return whether it was created, and either its URL or the reason it was not
   */
  PullRequestResult openPullRequest(PullRequestRequest request);
}
