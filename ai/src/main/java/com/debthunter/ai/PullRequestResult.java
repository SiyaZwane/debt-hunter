package com.debthunter.ai;

/**
 * The outcome of attempting to open a pull request. {@code merged} is always {@code false}: this
 * class never represents a merged state, because {@link FixAgent} never merges a pull request.
 */
public record PullRequestResult(
    boolean created, String pullRequestUrl, boolean merged, String reason) {

  public static PullRequestResult created(String pullRequestUrl) {
    return new PullRequestResult(true, pullRequestUrl, false, null);
  }

  public static PullRequestResult rejected(String reason) {
    return new PullRequestResult(false, null, false, reason);
  }
}
