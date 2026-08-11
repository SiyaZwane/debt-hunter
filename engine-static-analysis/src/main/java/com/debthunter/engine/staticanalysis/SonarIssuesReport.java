package com.debthunter.engine.staticanalysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** The root of a SonarQube issues-search export: {@code {"total": N, "issues": [...]}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SonarIssuesReport(List<SonarIssue> issues) {

  public SonarIssuesReport {
    issues = issues == null ? List.of() : List.copyOf(issues);
  }

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "issues is always the result of List.copyOf(), already unmodifiable")
  public List<SonarIssue> issues() {
    return issues;
  }
}
