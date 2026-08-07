package com.debthunter.repository;

import java.nio.file.Path;
import java.util.List;

/** Port for reading repository metadata and commit history, independent of the VCS backend. */
public interface RepositoryHistoryProvider {

  /**
   * Inspects the repository at {@code repoPath} without walking its full history.
   *
   * @param repoPath the repository's working-tree path
   * @return what was learned; {@link RepositoryInfo#notAGitRepository()} if {@code repoPath} is not
   *     a Git repository
   */
  RepositoryInfo inspect(Path repoPath);

  /**
   * Reads commit history for the repository at {@code repoPath}.
   *
   * @param repoPath the repository's working-tree path
   * @param window how much history to return
   * @return commits within the window, most recent first; empty if not a Git repository
   */
  List<CommitInfo> history(Path repoPath, HistoryWindow window);
}
