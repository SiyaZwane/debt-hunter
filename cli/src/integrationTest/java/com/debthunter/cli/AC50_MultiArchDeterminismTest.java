package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.debthunter.cli.docker.DockerTestSupport;
import com.debthunter.output.JsonReporter;
import com.debthunter.testkit.ConformanceRunner;
import com.debthunter.testkit.ConformanceSuite;
import com.debthunter.testkit.ScanInvoker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-50: every fixture in {@link ConformanceSuite} conforms whether the image was built for amd64
 * or arm64. This generalises AC-08's single ad hoc repo into the whole conformance suite, through
 * the shared {@link ConformanceRunner} rather than duplicated logic. Skips gracefully if
 * buildx/cross-platform emulation isn't available; emulated builds are slow, so this is
 * intentionally excluded from routine CI, exactly as AC-08 already is.
 */
@Tag("docker")
class AC50_MultiArchDeterminismTest {

  private static final String AMD64_TAG = "debt-hunter:junit-docker-test-ac50-amd64";
  private static final String ARM64_TAG = "debt-hunter:junit-docker-test-ac50-arm64";

  private static Path workDir;
  private static boolean multiArchAvailable;

  @BeforeAll
  static void buildBothPlatforms() throws Exception {
    assumeTrue(DockerTestSupport.isDockerAvailable(), "Docker is not available");
    multiArchAvailable =
        buildForPlatform("linux/amd64", AMD64_TAG) && buildForPlatform("linux/arm64", ARM64_TAG);
    assumeTrue(
        multiArchAvailable, "Multi-arch emulated builds are not available in this environment");
    workDir = DockerTestSupport.createHomeTempDir("debt-hunter-ac50-");
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
  void ac50_everySuiteFixtureConformsBetweenAmd64AndArm64Images() throws Exception {
    ConformanceRunner runner =
        new ConformanceRunner(scanWithImage(AMD64_TAG), scanWithImage(ARM64_TAG));

    var results = runner.runAll(ConformanceSuite.fixtures(), workDir);

    assertThat(results)
        .allSatisfy(result -> assertThat(result.matches()).as(result.describe()).isTrue());
  }

  private ScanInvoker scanWithImage(String imageTag) {
    return (repoPath, outputDir) -> {
      DockerTestSupport.ProcessResult result =
          DockerTestSupport.run(
              DockerTestSupport.repoRoot(),
              "docker",
              "run",
              "--rm",
              "-v",
              repoPath + ":/workspace/repo:ro",
              "-v",
              outputDir + ":/output",
              imageTag,
              "scan",
              "--repo",
              "/workspace/repo",
              "--output-dir",
              "/output");
      assertThat(result.exitCode()).isIn(0, 1);
      return readReport(outputDir);
    };
  }

  private JsonNode readReport(Path outputDir) throws Exception {
    return new ObjectMapper().readTree(outputDir.resolve(JsonReporter.FILE_NAME).toFile());
  }
}
