package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.debthunter.cli.docker.DockerTestSupport;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * BC-08: an unwritable output directory fails cleanly with the internal-error exit code inside the
 * actual container too, not only when running the JVM directly on the host (see {@code
 * BC03_ReadOnlyOutputDirTest}) — proving the non-root container user (uid 10001) doesn't somehow
 * bypass host-enforced permission bits on a bind-mounted directory.
 */
@Tag("docker")
class BC08_ReadOnlyOutputDirContainerTest {

  private static final int EXIT_INTERNAL_ERROR = 10;

  private static Path workDir;

  @BeforeAll
  static void buildImage() throws Exception {
    assumeTrue(DockerTestSupport.isDockerAvailable(), "Docker is not available");
    DockerTestSupport.ensureImageBuilt();
    workDir = DockerTestSupport.createHomeTempDir("debt-hunter-bc08-");
  }

  @AfterAll
  static void cleanup() throws Exception {
    if (workDir != null) {
      Path outputDir = workDir.resolve("output");
      if (Files.exists(outputDir)) {
        outputDir.toFile().setWritable(true);
      }
      DockerTestSupport.deleteRecursively(workDir);
    }
  }

  @Test
  void bc08_readOnlyOutputDirectoryInsideAContainerFailsWithInternalError() throws Exception {
    Path repoDir = workDir.resolve("repo");
    try (FixtureRepoBuilder fixture = FixtureRepoBuilder.initAt(repoDir)) {
      fixture.commitFile("Foo.java", "class Foo {}", "add Foo");
    }
    Path outputDir = workDir.resolve("output");
    Files.createDirectories(outputDir);
    assertThat(outputDir.toFile().setWritable(false)).isTrue();

    DockerTestSupport.ProcessResult result =
        DockerTestSupport.run(
            DockerTestSupport.repoRoot(),
            "docker",
            "run",
            "--rm",
            "--network",
            "none",
            "-v",
            repoDir + ":/workspace/repo:ro",
            "-v",
            outputDir + ":/output",
            DockerTestSupport.IMAGE_TAG,
            "scan",
            "--repo",
            "/workspace/repo",
            "--output-dir",
            "/output");

    assertThat(result.exitCode()).as(result.output()).isEqualTo(EXIT_INTERNAL_ERROR);
  }
}
