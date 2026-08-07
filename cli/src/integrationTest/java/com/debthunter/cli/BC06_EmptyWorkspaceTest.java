package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.debthunter.cli.docker.DockerTestSupport;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** BC-06: an empty mounted workspace (no repository at all) exits 2, matching a non-git path. */
@Tag("docker")
class BC06_EmptyWorkspaceTest {

  private static Path workDir;

  @BeforeAll
  static void buildImage() throws Exception {
    assumeTrue(DockerTestSupport.isDockerAvailable(), "Docker is not available");
    DockerTestSupport.ensureImageBuilt();
    workDir = DockerTestSupport.createHomeTempDir("debt-hunter-bc06-");
  }

  @AfterAll
  static void cleanup() throws Exception {
    if (workDir != null) {
      DockerTestSupport.deleteRecursively(workDir);
    }
  }

  @Test
  void bc06_emptyMountedWorkspaceExitsCode2() throws Exception {
    Path emptyDir = workDir.resolve("empty");
    java.nio.file.Files.createDirectories(emptyDir);
    Path outputDir = workDir.resolve("output");
    java.nio.file.Files.createDirectories(outputDir);

    DockerTestSupport.ProcessResult result =
        DockerTestSupport.run(
            DockerTestSupport.repoRoot(),
            "docker",
            "run",
            "--rm",
            "--network",
            "none",
            "-v",
            emptyDir + ":/workspace/repo:ro",
            "-v",
            outputDir + ":/output",
            DockerTestSupport.IMAGE_TAG,
            "scan",
            "--repo",
            "/workspace/repo",
            "--output-dir",
            "/output");

    assertThat(result.exitCode()).isEqualTo(2);
    assertThat(outputDir.toFile().list()).isEmpty();
  }
}
