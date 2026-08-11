package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.debthunter.cli.docker.DockerTestSupport;
import com.debthunter.output.JsonReporter;
import com.debthunter.testkit.FixtureRepoBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-06: invoking the image by its mutable tag and by its immutable content digest produce
 * equivalent output. Uses a throwaway local registry container so a real digest reference exists to
 * test against — a locally built image alone has no {@code RepoDigest}.
 */
@Tag("docker")
class AC06_DigestDeterminismTest {

  private static final String REGISTRY_CONTAINER = "debt-hunter-junit-test-registry";
  private static final String REGISTRY_REPO = "localhost:5081/debt-hunter";

  private static Path workDir;

  @BeforeAll
  static void setUp() throws Exception {
    assumeTrue(DockerTestSupport.isDockerAvailable(), "Docker is not available");
    DockerTestSupport.ensureImageBuilt();
    workDir = DockerTestSupport.createHomeTempDir("debt-hunter-ac06-");

    DockerTestSupport.run(DockerTestSupport.repoRoot(), "docker", "rm", "-f", REGISTRY_CONTAINER);
    DockerTestSupport.ProcessResult registryStart =
        DockerTestSupport.run(
            DockerTestSupport.repoRoot(),
            "docker",
            "run",
            "-d",
            "--rm",
            "--name",
            REGISTRY_CONTAINER,
            "-p",
            "5081:5000",
            "registry:2");
    assumeTrue(registryStart.exitCode() == 0, "Could not start local test registry");

    DockerTestSupport.run(
        DockerTestSupport.repoRoot(),
        "docker",
        "tag",
        DockerTestSupport.IMAGE_TAG,
        REGISTRY_REPO + ":test");
  }

  @AfterAll
  static void tearDown() throws Exception {
    DockerTestSupport.run(DockerTestSupport.repoRoot(), "docker", "rm", "-f", REGISTRY_CONTAINER);
    if (workDir != null) {
      DockerTestSupport.deleteRecursively(workDir);
    }
  }

  @Test
  void ac06_tagAndDigestInvocationProduceEquivalentOutput() throws Exception {
    DockerTestSupport.ProcessResult push =
        DockerTestSupport.run(
            DockerTestSupport.repoRoot(), "docker", "push", REGISTRY_REPO + ":test");
    assertThat(push.exitCode()).isZero();
    String digest = extractDigest(push.output());
    String digestReference = REGISTRY_REPO + "@" + digest;

    Path fixtureDir = workDir.resolve("repo");
    try (FixtureRepoBuilder fixture = FixtureRepoBuilder.initAt(fixtureDir)) {
      fixture.commitFile("Foo.java", "class Foo {}", "add Foo");
    }

    JsonNode byTag = scanWith(REGISTRY_REPO + ":test", fixtureDir, workDir.resolve("by-tag"));
    JsonNode byDigest = scanWith(digestReference, fixtureDir, workDir.resolve("by-digest"));

    stripVolatileRunFields(byTag);
    stripVolatileRunFields(byDigest);
    assertThat(byTag).isEqualTo(byDigest);
  }

  private JsonNode scanWith(String imageReference, Path fixtureDir, Path outputDir)
      throws Exception {
    DockerTestSupport.createWritableOutputDir(outputDir);
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
            imageReference,
            "scan",
            "--repo",
            "/workspace/repo",
            "--output-dir",
            "/output");
    assertThat(result.exitCode()).isIn(0, 1);
    return new ObjectMapper().readTree(outputDir.resolve(JsonReporter.FILE_NAME).toFile());
  }

  private void stripVolatileRunFields(JsonNode root) {
    ObjectNode run = (ObjectNode) root.get("run");
    run.remove("id");
    run.remove("timestamp");
  }

  private String extractDigest(String pushOutput) {
    Matcher matcher = Pattern.compile("digest:\\s*(sha256:[0-9a-f]+)").matcher(pushOutput);
    if (!matcher.find()) {
      throw new IllegalStateException("Could not find digest in push output:\n" + pushOutput);
    }
    return matcher.group(1);
  }
}
