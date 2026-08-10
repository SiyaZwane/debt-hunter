package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.application.scan.ExitCode;
import com.debthunter.repository.GitHistoryProvider;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code debt-hunter doctor} reports history depth, shallow/graft status, and recommendations. */
@Tag("integration")
class DoctorCommandTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void reportsNoIssuesForAFullHistoryRepository() {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    int exitCode =
        new DoctorCommand(fixture.path(), new GitHistoryProvider(), new PrintStream(buffer)).call();

    assertThat(exitCode).isEqualTo(ExitCode.POLICY_SATISFIED.code());
    String output = buffer.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("History depth: full").contains("No issues found");
  }

  @Test
  void reportsRecommendationsForAShallowRepository() throws Exception {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");
    Files.createFile(fixture.path().resolve(".git").resolve("shallow"));
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    int exitCode =
        new DoctorCommand(fixture.path(), new GitHistoryProvider(), new PrintStream(buffer)).call();

    assertThat(exitCode).isEqualTo(ExitCode.POLICY_SATISFIED.code());
    String output = buffer.toString(StandardCharsets.UTF_8);
    assertThat(output)
        .contains("Shallow:      true")
        .contains("History depth: shallow")
        .contains("Recommendations:")
        .contains("git fetch --unshallow");
  }

  @Test
  void reportsAConfigurationErrorForANonGitPath(@TempDir Path notARepo) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    int exitCode =
        new DoctorCommand(notARepo, new GitHistoryProvider(), new PrintStream(buffer)).call();

    assertThat(exitCode).isEqualTo(ExitCode.CONFIGURATION_ERROR.code());
    assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("Not a Git repository");
  }
}
