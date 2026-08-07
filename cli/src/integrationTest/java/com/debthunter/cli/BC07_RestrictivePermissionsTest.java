package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.debthunter.cli.docker.DockerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * BC-07: a repository whose {@code .git} directory exists but is unreadable exits 10 (internal
 * error), not 2 (not-a-repo) — see {@code GitHistoryProvider.failIfGitDirExistsButIsUnreadable}.
 *
 * <p>The fixture is built and its permissions restricted inside a throwaway container (baked into
 * an image layer via {@code docker commit}), not via a host bind mount: bind-mounted permission
 * bits don't survive translation through a Docker-on-a-VM setup (e.g. Colima) reliably.
 */
@Tag("docker")
class BC07_RestrictivePermissionsTest {

  private static final int EXIT_INTERNAL_ERROR = 10;
  private static final String FIXTURE_IMAGE_TAG = "debt-hunter:junit-docker-test-bc07-fixture";

  private String setupContainerId;

  @BeforeAll
  static void buildImage() throws Exception {
    assumeTrue(DockerTestSupport.isDockerAvailable(), "Docker is not available");
    DockerTestSupport.ensureImageBuilt();
  }

  @AfterEach
  void cleanup() throws Exception {
    if (setupContainerId != null) {
      DockerTestSupport.run(DockerTestSupport.repoRoot(), "docker", "rm", "-f", setupContainerId);
    }
    DockerTestSupport.run(DockerTestSupport.repoRoot(), "docker", "rmi", "-f", FIXTURE_IMAGE_TAG);
  }

  @Test
  void bc07_unreadableGitDirectoryExitsWithInternalErrorNotConfigurationError() throws Exception {
    // Defensive: a previous run killed mid-test (e.g. a timeout) could have left this name
    // behind, which would otherwise make `docker run --name` below fail with "already in use".
    DockerTestSupport.run(
        DockerTestSupport.repoRoot(), "docker", "rm", "-f", "debt-hunter-junit-bc07-setup");

    String setupScript =
        "set -e\n"
            + "mkdir -p /opt/fixtures/repo/.git/refs /opt/fixtures/repo/.git/objects\n"
            + "printf 'ref: refs/heads/main\\n' > /opt/fixtures/repo/.git/HEAD\n"
            + "printf '[core]\\n' > /opt/fixtures/repo/.git/config\n"
            + "echo 'class Foo {}' > /opt/fixtures/repo/Foo.java\n"
            + "chown -R debt-hunter:debt-hunter /opt/fixtures\n"
            + "chmod 000 /opt/fixtures/repo/.git\n";

    DockerTestSupport.ProcessResult setup =
        DockerTestSupport.run(
            DockerTestSupport.repoRoot(),
            "docker",
            "run",
            "--name",
            "debt-hunter-junit-bc07-setup",
            "--user",
            "root",
            "--entrypoint",
            "/bin/sh",
            DockerTestSupport.IMAGE_TAG,
            "-c",
            setupScript);
    assertThat(setup.exitCode()).as("fixture setup: %s", setup.output()).isZero();
    setupContainerId = "debt-hunter-junit-bc07-setup";

    DockerTestSupport.ProcessResult commit =
        DockerTestSupport.run(
            DockerTestSupport.repoRoot(),
            "docker",
            "commit",
            "--change=ENTRYPOINT [\"debt-hunter\"]",
            "--change=USER debt-hunter",
            setupContainerId,
            FIXTURE_IMAGE_TAG);
    assertThat(commit.exitCode()).isZero();

    DockerTestSupport.ProcessResult scan =
        DockerTestSupport.run(
            DockerTestSupport.repoRoot(),
            "docker",
            "run",
            "--rm",
            FIXTURE_IMAGE_TAG,
            "scan",
            "--repo",
            "/opt/fixtures/repo",
            "--output-dir",
            "/output");

    assertThat(scan.exitCode()).isEqualTo(EXIT_INTERNAL_ERROR);
  }
}
