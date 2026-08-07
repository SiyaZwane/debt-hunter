package com.debthunter.engine.spi;

import com.debthunter.domain.HistoryDepth;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** What an engine needs to know about a repository to decide whether it applies. */
public record RepositoryContext(
    Path repoPath, List<String> languages, VcsType vcsType, HistoryDepth historyDepth) {

  /** Validates required fields and defensively copies {@code languages}. */
  public RepositoryContext {
    Objects.requireNonNull(repoPath, "repoPath");
    languages = languages == null ? List.of() : List.copyOf(languages);
    Objects.requireNonNull(vcsType, "vcsType");
    Objects.requireNonNull(historyDepth, "historyDepth");
  }
}
