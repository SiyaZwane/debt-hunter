package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.debthunter.cli.docker.DockerTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-12: the built image sets {@code TZ=UTC} and {@code LC_ALL=C}, so every process in the
 * container — not just the JVM, which {@link DeterminismEnforcer} covers independently — inherits a
 * fixed time zone and locale.
 */
@Tag("docker")
class ContainerDeterminismEnvironmentTest {

  @BeforeAll
  static void buildImage() throws Exception {
    assumeTrue(DockerTestSupport.isDockerAvailable(), "Docker is not available");
    DockerTestSupport.ensureImageBuilt();
  }

  @Test
  void theImageSetsTzAndLcAllForDeterministicOutputEverywhereInTheContainer() throws Exception {
    DockerTestSupport.ProcessResult result =
        DockerTestSupport.run(
            DockerTestSupport.repoRoot(),
            "docker",
            "inspect",
            DockerTestSupport.IMAGE_TAG,
            "--format",
            "{{.Config.Env}}");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).contains("TZ=UTC").contains("LC_ALL=C");
  }
}
