package com.debthunter.testkit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * Programmatically builds a throwaway Git repository for tests: commits, renames, and repeated
 * touches to simulate hotspots. Every instance owns a temporary directory that {@link #close()}
 * deletes.
 */
public final class FixtureRepoBuilder implements AutoCloseable {

  private static final String AUTHOR_NAME = "Fixture Author";
  private static final String AUTHOR_EMAIL = "fixture@example.com";

  private final Path root;
  private final Git git;

  private FixtureRepoBuilder(Path root, Git git) {
    this.root = root;
    this.git = git;
  }

  /**
   * Creates a new, empty Git repository (no commits) in a fresh temporary directory.
   *
   * @return a builder for the new repository
   */
  public static FixtureRepoBuilder init() {
    try {
      Path dir = Files.createTempDirectory("debt-hunter-fixture-");
      Git git = Git.init().setDirectory(dir.toFile()).setInitialBranch("main").call();
      return new FixtureRepoBuilder(dir, git);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (GitAPIException e) {
      throw new IllegalStateException("Failed to initialise fixture repository", e);
    }
  }

  /**
   * Writes (or overwrites) a file and commits it.
   *
   * @param relativePath path of the file relative to the repository root
   * @param content the file's full content
   * @param message the commit message
   * @return this builder, for chaining
   */
  public FixtureRepoBuilder commitFile(String relativePath, String content, String message) {
    try {
      Path file = root.resolve(relativePath);
      createParentDirectories(file);
      Files.writeString(file, content);
      git.add().addFilepattern(relativePath).call();
      git.commit()
          .setMessage(message)
          .setAuthor(AUTHOR_NAME, AUTHOR_EMAIL)
          .setCommitter(AUTHOR_NAME, AUTHOR_EMAIL)
          .call();
      return this;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (GitAPIException e) {
      throw new IllegalStateException("Failed to commit " + relativePath, e);
    }
  }

  /**
   * Renames a tracked file and commits the rename.
   *
   * @param fromPath the file's current path, relative to the repository root
   * @param toPath the file's new path, relative to the repository root
   * @param message the commit message
   * @return this builder, for chaining
   */
  public FixtureRepoBuilder renameFile(String fromPath, String toPath, String message) {
    try {
      Path source = root.resolve(fromPath);
      Path destination = root.resolve(toPath);
      createParentDirectories(destination);
      Files.move(source, destination);
      git.add().addFilepattern(toPath).call();
      git.rm().addFilepattern(fromPath).call();
      git.commit()
          .setMessage(message)
          .setAuthor(AUTHOR_NAME, AUTHOR_EMAIL)
          .setCommitter(AUTHOR_NAME, AUTHOR_EMAIL)
          .call();
      return this;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (GitAPIException e) {
      throw new IllegalStateException("Failed to rename " + fromPath + " to " + toPath, e);
    }
  }

  /**
   * Simulates a change hotspot by committing the same file repeatedly with different content.
   *
   * @param relativePath path of the file relative to the repository root
   * @param touchCount how many separate commits to make
   * @return this builder, for chaining
   */
  public FixtureRepoBuilder hotspot(String relativePath, int touchCount) {
    for (int i = 0; i < touchCount; i++) {
      commitFile(
          relativePath,
          "revision " + i + System.lineSeparator(),
          "touch " + relativePath + " #" + i);
    }
    return this;
  }

  /**
   * This repository's working-tree path.
   *
   * @return the path, valid until {@link #close()} is called
   */
  public Path path() {
    return root;
  }

  /**
   * The underlying JGit handle, for assertions that need direct repository access.
   *
   * @return the open {@link Git} instance
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "test utility deliberately exposes the live handle for direct assertions")
  public Git git() {
    return git;
  }

  /** Closes the underlying JGit repository and deletes its temporary directory. */
  @Override
  public void close() {
    git.close();
    try (var paths = Files.walk(root)) {
      paths.sorted(Comparator.reverseOrder()).forEach(FixtureRepoBuilder::deleteQuietly);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void createParentDirectories(Path file) throws IOException {
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // best-effort cleanup of a temporary test fixture
    }
  }
}
