package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.debthunter.cli.docker.DockerTestSupport;
import com.debthunter.output.JsonReporter;
import com.debthunter.output.MarkdownReporter;
import com.debthunter.output.MetricsReporter;
import com.debthunter.output.SarifReporter;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-05: the built image runs a full scan offline ({@code --network none}) against a mounted
 * workspace and produces every report file. Requires a working Docker daemon; run manually via
 * {@code mvn -pl cli verify -Dfailsafe.groups=docker}.
 */
@Tag("docker")
class AC05_OfflineContainerScanTest {

  private static Path workDir;

  @BeforeAll
  static void buildImage() throws Exception {
    assumeTrue(DockerTestSupport.isDockerAvailable(), "Docker is not available");
    DockerTestSupport.ensureImageBuilt();
    workDir = DockerTestSupport.createHomeTempDir("debt-hunter-ac05-");
  }

  @AfterAll
  static void cleanup() throws Exception {
    if (workDir != null) {
      DockerTestSupport.deleteRecursively(workDir);
    }
  }

  @Test
  void ac05_offlineContainerScanProducesAllReports() throws Exception {
    Path fixtureDir = workDir.resolve("repo");
    Path outputDir = workDir.resolve("output");
    try (FixtureRepoBuilder fixture = FixtureRepoBuilder.initAt(fixtureDir)) {
      fixture.commitFile("Foo.java", "class Foo {}", "add Foo");
    }
    DockerTestSupport.createWritableOutputDir(outputDir);

    DockerTestSupport.ProcessResult result =
        DockerTestSupport.run(
            DockerTestSupport.repoRoot(),
            "docker",
            "run",
            "--rm",
            "--network",
            "none",
            "-v",
            fixtureDir + ":/workspace/repo:ro",
            "-v",
            outputDir + ":/output",
            DockerTestSupport.IMAGE_TAG,
            "scan",
            "--repo",
            "/workspace/repo",
            "--output-dir",
            "/output");

    assertThat(result.exitCode()).isIn(0, 1);
    assertThat(outputDir.resolve(JsonReporter.FILE_NAME)).exists();
    assertThat(outputDir.resolve(MarkdownReporter.FILE_NAME)).exists();
    assertThat(outputDir.resolve(MetricsReporter.FILE_NAME)).exists();
    assertThat(outputDir.resolve(SarifReporter.FILE_NAME)).exists();
  }
}
