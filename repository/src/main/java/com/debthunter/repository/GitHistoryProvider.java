package com.debthunter.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/** JGit-backed implementation of {@link RepositoryHistoryProvider}. */
public final class GitHistoryProvider implements RepositoryHistoryProvider {

  @Override
  public RepositoryInfo inspect(Path repoPath) {
    FileRepositoryBuilder builder = new FileRepositoryBuilder().findGitDir(repoPath.toFile());
    if (builder.getGitDir() == null) {
      return RepositoryInfo.notAGitRepository();
    }
    try (Repository repository = builder.build()) {
      ObjectId head = repository.resolve("HEAD");
      long commitCount = head == null ? 0 : countCommits(repository, head);
      return new RepositoryInfo(
          true,
          isShallow(repository),
          isGrafted(repository),
          commitCount,
          head == null ? null : head.getName(),
          repository.getBranch());
    } catch (IOException e) {
      throw new RepositoryAccessException("Failed to inspect repository at " + repoPath, e);
    }
  }

  @Override
  public List<CommitInfo> history(Path repoPath, HistoryWindow window) {
    FileRepositoryBuilder builder = new FileRepositoryBuilder().findGitDir(repoPath.toFile());
    if (builder.getGitDir() == null) {
      return List.of();
    }
    HistoryWindow effectiveWindow = window == null ? HistoryWindow.all() : window;
    try (Repository repository = builder.build()) {
      ObjectId head = repository.resolve("HEAD");
      if (head == null) {
        return List.of();
      }
      List<CommitInfo> commits = new ArrayList<>();
      for (RevCommit commit : Git.wrap(repository).log().add(head).call()) {
        Instant commitTime = Instant.ofEpochSecond(commit.getCommitTime());
        if (effectiveWindow.since() != null && commitTime.isBefore(effectiveWindow.since())) {
          continue;
        }
        commits.add(toCommitInfo(commit));
        if (effectiveWindow.maxCommits() != null
            && commits.size() >= effectiveWindow.maxCommits()) {
          break;
        }
      }
      return List.copyOf(commits);
    } catch (IOException | GitAPIException e) {
      throw new RepositoryAccessException("Failed to read history for " + repoPath, e);
    }
  }

  private long countCommits(Repository repository, ObjectId head) throws IOException {
    try {
      long count = 0;
      for (RevCommit ignored : Git.wrap(repository).log().add(head).call()) {
        count++;
      }
      return count;
    } catch (GitAPIException e) {
      throw new IOException(e);
    }
  }

  private boolean isShallow(Repository repository) {
    return Files.exists(repository.getDirectory().toPath().resolve("shallow"));
  }

  private boolean isGrafted(Repository repository) {
    Path grafts = repository.getDirectory().toPath().resolve("info").resolve("grafts");
    try {
      return Files.exists(grafts) && Files.size(grafts) > 0;
    } catch (IOException e) {
      return false;
    }
  }

  private CommitInfo toCommitInfo(RevCommit commit) {
    List<String> parentIds = Arrays.stream(commit.getParents()).map(RevCommit::getName).toList();
    return new CommitInfo(
        commit.getName(),
        commit.getAuthorIdent().getName(),
        commit.getAuthorIdent().getEmailAddress(),
        Instant.ofEpochSecond(commit.getCommitTime()),
        commit.getFullMessage().strip(),
        parentIds);
  }
}
