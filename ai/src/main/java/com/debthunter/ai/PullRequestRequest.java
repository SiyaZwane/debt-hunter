package com.debthunter.ai;

import java.util.List;
import java.util.Objects;

/** A request to open a single pull request against a source-hosting API. */
public record PullRequestRequest(
    String repository,
    String branchName,
    String baseBranch,
    String title,
    String description,
    String patch,
    List<String> labels) {

  public PullRequestRequest {
    Objects.requireNonNull(repository, "repository");
    Objects.requireNonNull(branchName, "branchName");
    Objects.requireNonNull(baseBranch, "baseBranch");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(patch, "patch");
    labels = List.copyOf(labels);
  }
}
