package com.debthunter.cli.docker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Shared plumbing for the docker-tagged CLI integration tests: running the CLI, not testing it. */
public final class DockerTestSupport {

  /** The tag every docker-tagged test builds and runs against. */
  public static final String IMAGE_TAG = "debt-hunter:junit-docker-test";

  private static volatile boolean imageBuilt;

  private DockerTestSupport() {}

  /** The repository root, assuming tests run with the module directory as the working directory. */
  public static Path repoRoot() {
    return Path.of("").toAbsolutePath().getParent();
  }

  /**
   * Whether a Docker daemon is reachable right now.
   *
   * @return {@code true} if {@code docker version} succeeds
   */
  public static boolean isDockerAvailable() {
    try {
      return run(repoRoot(), "docker", "version").exitCode() == 0;
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }

  /** Builds {@link #IMAGE_TAG} once per JVM, reusing it across every docker-tagged test class. */
  public static synchronized void ensureImageBuilt() throws IOException, InterruptedException {
    if (imageBuilt) {
      return;
    }
    ProcessResult result = run(repoRoot(), "docker", "build", "-t", IMAGE_TAG, ".");
    if (result.exitCode() != 0) {
      throw new IllegalStateException("docker build failed:\n" + result.output());
    }
    imageBuilt = true;
  }

  /**
   * Creates a fresh temporary directory under the user's home directory. Colima (and similar
   * Docker-on-a-VM setups) only bind-mount the home directory by default, so fixtures that will be
   * volume-mounted into a container must live there, not under the system temp directory.
   *
   * @param prefix a label for the directory name
   * @return the created directory
   */
  public static Path createHomeTempDir(String prefix) throws IOException {
    Path home = Path.of(System.getProperty("user.home"));
    return Files.createTempDirectory(home, prefix);
  }

  /**
   * Creates a directory intended to be bind-mounted as a container's writable output directory. A
   * bind mount exposes the host filesystem's real numeric uid/gid, unaffected by the container's
   * own user database — so a directory merely created (and thus owned) by the host user, at its
   * default permissions, is not writable by the image's baked-in non-root user (uid 10001) on a
   * real Linux Docker daemon, even though it may appear to work under a macOS Docker Desktop/Colima
   * VM's more permissive file-sharing layer. Making it world-writable sidesteps the uid mismatch
   * without needing to know or match the container's specific uid.
   *
   * @param directory the directory to create
   */
  public static void createWritableOutputDir(Path directory) throws IOException {
    Files.createDirectories(directory);
    Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxrwxrwx"));
  }

  /**
   * Recursively deletes a directory created by {@link #createHomeTempDir(String)}.
   *
   * @param directory the directory to delete
   */
  public static void deleteRecursively(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }
    try (var paths = Files.walk(directory)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException ignored) {
                  // best-effort cleanup of a temporary test fixture
                }
              });
    }
  }

  /**
   * Runs a command, waiting up to five minutes, with stdout and stderr merged.
   *
   * @param workingDirectory the process's working directory
   * @param command the command and its arguments
   * @return the exit code and combined output
   */
  public static ProcessResult run(Path workingDirectory, String... command)
      throws IOException, InterruptedException {
    return run(workingDirectory, List.of(command), 5, TimeUnit.MINUTES);
  }

  /**
   * Runs a command, waiting up to five minutes, with stdout and stderr merged.
   *
   * @param workingDirectory the process's working directory
   * @param command the command and its arguments
   * @return the exit code and combined output
   */
  public static ProcessResult run(Path workingDirectory, List<String> command)
      throws IOException, InterruptedException {
    return run(workingDirectory, command, 5, TimeUnit.MINUTES);
  }

  /**
   * Runs a command with stdout and stderr merged, for use cases (e.g. emulated cross-architecture
   * builds) that need longer than the default five-minute timeout.
   *
   * @param workingDirectory the process's working directory
   * @param command the command and its arguments
   * @param timeout how long to wait before killing the process
   * @param unit the unit of {@code timeout}
   * @return the exit code and combined output
   */
  public static ProcessResult run(
      Path workingDirectory, List<String> command, long timeout, TimeUnit unit)
      throws IOException, InterruptedException {
    Process process =
        new ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    boolean finished = process.waitFor(timeout, unit);
    if (!finished) {
      process.destroyForcibly();
      throw new IllegalStateException("Command timed out: " + command);
    }
    return new ProcessResult(process.exitValue(), output);
  }

  /** The outcome of running an external command. */
  public record ProcessResult(int exitCode, String output) {}
}
