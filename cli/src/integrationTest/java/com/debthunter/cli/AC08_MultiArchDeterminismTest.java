package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.debthunter.cli.docker.DockerTestSupport;
import com.debthunter.output.JsonReporter;
import com.debthunter.testkit.FixtureRepoBuilder;
import com.debthunter.testkit.VolatileFieldMasker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-08: the same scan produces equivalent output whether the image was built for amd64 or arm64.
 * Skips gracefully if buildx/cross-platform emulation isn't available. Emulated builds are slow (a
 * full Maven build under QEMU) — this is intentionally excluded from routine CI.
 */
@Tag("docker")
class AC08_MultiArchDeterminismTest {

  private static final String AMD64_TAG = "debt-hunter:junit-docker-test-amd64";
  private static final String ARM64_TAG = "debt-hunter:junit-docker-test-arm64";

  private static Path workDir;
  private static boolean multiArchAvailable;

  @BeforeAll
  static void buildBothPlatforms() throws Exception {
    assumeTrue(DockerTestSupport.isDockerAvailable(), "Docker is not available");
    multiArchAvailable =
        buildForPlatform("linux/amd64", AMD64_TAG) && buildForPlatform("linux/arm64", ARM64_TAG);
    assumeTrue(
        multiArchAvailable, "Multi-arch emulated builds are not available in this environment");
    workDir = DockerTestSupport.createHomeTempDir("debt-hunter-ac08-");
  }

  @AfterAll
  static void cleanup() throws Exception {
    if (workDir != null) {
      DockerTestSupport.deleteRecursively(workDir);
    }
  }

  private static boolean buildForPlatform(String platform, String tag) {
    try {
      DockerTestSupport.ProcessResult result =
          DockerTestSupport.run(
              DockerTestSupport.repoRoot(),
              java.util.List.of(
                  "docker", "buildx", "build", "--platform", platform, "-t", tag, "--load", "."),
              15,
              TimeUnit.MINUTES);
      return result.exitCode() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  @Test
  void ac08_amd64AndArm64ImagesProduceEquivalentOutput() throws Exception {
    Path fixtureDir = workDir.resolve("repo");
    try (FixtureRepoBuilder fixture = FixtureRepoBuilder.initAt(fixtureDir)) {
      fixture.commitFile("Foo.java", "class Foo {}", "add Foo");
    }

    JsonNode amd64Result = scanWith(AMD64_TAG, fixtureDir, workDir.resolve("amd64-output"));
    JsonNode arm64Result = scanWith(ARM64_TAG, fixtureDir, workDir.resolve("arm64-output"));

    VolatileFieldMasker.mask(amd64Result);
    VolatileFieldMasker.mask(arm64Result);
    assertThat(amd64Result).isEqualTo(arm64Result);
  }

  private JsonNode scanWith(String imageTag, Path fixtureDir, Path outputDir) throws Exception {
    DockerTestSupport.ProcessResult result =
        DockerTestSupport.run(
            DockerTestSupport.repoRoot(),
            "docker",
            "run",
            "--rm",
            "-v",
            fixtureDir + ":/workspace/repo:ro",
            "-v",
            outputDir + ":/output",
            imageTag,
            "scan",
            "--repo",
            "/workspace/repo",
            "--output-dir",
            "/output");
    assertThat(result.exitCode()).isIn(0, 1);
    return new ObjectMapper().readTree(outputDir.resolve(JsonReporter.FILE_NAME).toFile());
  }
}
