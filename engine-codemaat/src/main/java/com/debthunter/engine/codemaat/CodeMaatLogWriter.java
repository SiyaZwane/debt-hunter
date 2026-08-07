package com.debthunter.engine.codemaat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * Builds a commit log in Code Maat's "git2" text format directly from a repository's JGit object
 * database, so the engine never needs a native {@code git} binary on the analysis path.
 *
 * <p>Only the changed-file paths per commit matter for the revisions, coupling, age, and authors
 * analyses this engine runs — none of them consult added/deleted line counts — so every numstat
 * line is written as {@code 0\t0\t<path>}, matching the format Code Maat's parser expects without
 * the cost of computing real diff statistics. Merge commits are skipped, matching {@code git log}
 * without {@code -m}.
 */
public final class CodeMaatLogWriter {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

  /**
   * Writes {@code repoPath}'s commit history, in Code Maat's git2 format, to {@code targetLogFile}.
   *
   * @param repoPath the repository's working-tree path
   * @param targetLogFile the file to write; created or overwritten
   * @return {@code targetLogFile}, for chaining
   */
  public Path writeLog(Path repoPath, Path targetLogFile) {
    FileRepositoryBuilder builder = new FileRepositoryBuilder().findGitDir(repoPath.toFile());
    if (builder.getGitDir() == null) {
      throw new IllegalArgumentException("Not a Git repository: " + repoPath);
    }
    try (Repository repository = builder.build();
        RevWalk revWalk = new RevWalk(repository);
        Writer writer = Files.newBufferedWriter(targetLogFile, StandardCharsets.UTF_8)) {
      ObjectId head = repository.resolve("HEAD");
      if (head == null) {
        return targetLogFile;
      }
      revWalk.markStart(revWalk.parseCommit(head));
      for (RevCommit commit : revWalk) {
        if (commit.getParentCount() > 1) {
          continue;
        }
        writeCommit(repository, revWalk, commit, writer);
      }
      return targetLogFile;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void writeCommit(Repository repository, RevWalk revWalk, RevCommit commit, Writer writer)
      throws IOException {
    PersonIdent author = commit.getAuthorIdent();
    String date =
        DATE_FORMAT.format(Instant.ofEpochSecond(commit.getCommitTime()).atZone(ZoneOffset.UTC));
    writer.write("--" + commit.getName().substring(0, 7) + "--" + date + "--" + author.getName());
    writer.write(System.lineSeparator());

    for (String path : changedPaths(repository, revWalk, commit)) {
      writer.write("0\t0\t" + path);
      writer.write(System.lineSeparator());
    }
    writer.write(System.lineSeparator());
  }

  private List<String> changedPaths(Repository repository, RevWalk revWalk, RevCommit commit)
      throws IOException {
    try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      formatter.setRepository(repository);
      formatter.setDetectRenames(false);
      List<DiffEntry> diffs =
          formatter.scan(
              parentTreeIterator(repository, revWalk, commit), treeIterator(repository, commit));
      return diffs.stream()
          .map(
              entry ->
                  entry.getChangeType() == DiffEntry.ChangeType.DELETE
                      ? entry.getOldPath()
                      : entry.getNewPath())
          .distinct()
          .toList();
    }
  }

  private CanonicalTreeParser treeIterator(Repository repository, RevCommit commit)
      throws IOException {
    try (ObjectReader reader = repository.newObjectReader()) {
      CanonicalTreeParser parser = new CanonicalTreeParser();
      parser.reset(reader, commit.getTree());
      return parser;
    }
  }

  private org.eclipse.jgit.treewalk.AbstractTreeIterator parentTreeIterator(
      Repository repository, RevWalk revWalk, RevCommit commit) throws IOException {
    if (commit.getParentCount() == 0) {
      return new EmptyTreeIterator();
    }
    RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
    return treeIterator(repository, parent);
  }
}
