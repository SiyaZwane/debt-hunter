package com.debthunter.repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Resolves a file's canonical path identity across its rename history, so that renaming a file
 * alone does not change a finding's fingerprint. Uses native {@code git log --follow}: resolving
 * renames across a file's full history (not just one commit) is exactly what {@code --follow} does,
 * and JGit has no equivalent — see the technology stack's note on using {@code ProcessBuilder} for
 * native git where JGit falls short.
 *
 * <p>Falls back to returning the input path unchanged whenever history cannot be read (no {@code
 * git} binary on the {@code PATH}, not a repository, or no history yet) rather than failing the
 * scan: an unresolved rename degrades fingerprint stability, it does not make the scan wrong.
 */
public final class RenameTracker {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

  private final Duration timeout;

  public RenameTracker() {
    this(DEFAULT_TIMEOUT);
  }

  public RenameTracker(Duration timeout) {
    this.timeout = timeout;
  }

  /**
   * Resolves {@code currentPath}'s canonical path identity: the oldest name in its rename history,
   * as reported by {@code git log --follow}.
   *
   * @param repoPath the repository's working-tree path
   * @param currentPath the file's current path, relative to {@code repoPath}
   * @return the oldest known name for this file, or {@code currentPath} itself if its history
   *     cannot be read
   */
  public String canonicalPath(Path repoPath, String currentPath) {
    List<String> command =
        List.of(
            "git",
            "-C",
            repoPath.toString(),
            "log",
            "--follow",
            "--name-only",
            "--format=",
            "--",
            currentPath);
    List<String> names = runAndCollectNames(command);
    return names.isEmpty() ? currentPath : names.get(names.size() - 1);
  }

  private List<String> runAndCollectNames(List<String> command) {
    Process process;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(false).start();
    } catch (IOException e) {
      return List.of();
    }

    // stdout and stderr must be drained concurrently, not sequentially: if either pipe's buffer
    // fills while we're blocked reading only the other one, the child blocks on its write and we
    // deadlock waiting for it to exit.
    ExecutorService streamReaders = Executors.newFixedThreadPool(2);
    try {
      Future<String> stdoutFuture = streamReaders.submit(() -> readAll(process.getInputStream()));
      Future<String> stderrFuture = streamReaders.submit(() -> readAll(process.getErrorStream()));
      boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        return List.of();
      }
      String stdout;
      try {
        stdout = stdoutFuture.get();
        stderrFuture.get();
      } catch (ExecutionException e) {
        return List.of();
      }
      if (process.exitValue() != 0) {
        return List.of();
      }
      return stdout.lines().filter(line -> !line.isBlank()).toList();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      return List.of();
    } finally {
      streamReaders.shutdownNow();
    }
  }

  private String readAll(InputStream stream) throws IOException {
    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
  }
}
