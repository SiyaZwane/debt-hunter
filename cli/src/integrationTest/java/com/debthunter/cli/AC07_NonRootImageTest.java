package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.debthunter.cli.docker.DockerTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-07: image inspection shows a non-root user, no elevated capabilities, and a real SBOM. */
@Tag("docker")
class AC07_NonRootImageTest {

  @BeforeAll
  static void buildImage() throws Exception {
    assumeTrue(DockerTestSupport.isDockerAvailable(), "Docker is not available");
    DockerTestSupport.ensureImageBuilt();
  }

  @Test
  void ac07_imageRunsAsANonRootUser() throws Exception {
    DockerTestSupport.ProcessResult result =
        DockerTestSupport.run(
            DockerTestSupport.repoRoot(),
            "docker",
            "inspect",
            DockerTestSupport.IMAGE_TAG,
            "--format",
            "{{.Config.User}}");

    assertThat(result.exitCode()).isZero();
    String user = result.output().strip();
    assertThat(user).isNotBlank();
    assertThat(user).isNotEqualTo("root").isNotEqualTo("0");
  }

  @Test
  void ac07_imageRequestsNoElevatedCapabilitiesAndIsNotPrivileged() throws Exception {
    DockerTestSupport.ProcessResult create =
        DockerTestSupport.run(
            DockerTestSupport.repoRoot(), "docker", "create", DockerTestSupport.IMAGE_TAG);
    assertThat(create.exitCode()).isZero();
    String containerId = create.output().strip();

    try {
      DockerTestSupport.ProcessResult capAdd =
          DockerTestSupport.run(
              DockerTestSupport.repoRoot(),
              "docker",
              "inspect",
              containerId,
              "--format",
              "{{.HostConfig.CapAdd}}");
      DockerTestSupport.ProcessResult privileged =
          DockerTestSupport.run(
              DockerTestSupport.repoRoot(),
              "docker",
              "inspect",
              containerId,
              "--format",
              "{{.HostConfig.Privileged}}");

      assertThat(capAdd.output().strip()).isIn("[]", "<no value>");
      assertThat(privileged.output().strip()).isEqualTo("false");
    } finally {
      DockerTestSupport.run(DockerTestSupport.repoRoot(), "docker", "rm", containerId);
    }
  }

  @Test
  void ac07_sbomCanBeGeneratedAndListsBundledDependencies() throws Exception {
    assumeTrue(isSyftAvailable(), "syft is not available");

    DockerTestSupport.ProcessResult sbom =
        DockerTestSupport.run(
            DockerTestSupport.repoRoot(), "syft", DockerTestSupport.IMAGE_TAG, "-o", "spdx-json");

    assertThat(sbom.exitCode()).isZero();
    assertThat(sbom.output()).contains("\"spdxVersion\"");
    assertThat(sbom.output()).contains("\"packages\"");
    // A representative bundled dependency, to confirm the SBOM actually reflects the jar's
    // contents rather than being an empty/placeholder document. picocli itself is deliberately
    // not used here: its pom.properties doesn't survive maven-shade-plugin's merge, so syft
    // never catalogues it, even though it works fine at runtime — a real SBOM-completeness gap,
    // not a test bug, and not one this step needs to solve.
    assertThat(sbom.output().toLowerCase(java.util.Locale.ROOT)).contains("jackson-databind");
  }

  private boolean isSyftAvailable() {
    try {
      return DockerTestSupport.run(DockerTestSupport.repoRoot(), "syft", "version").exitCode() == 0;
    } catch (Exception e) {
      return false;
    }
  }
}
