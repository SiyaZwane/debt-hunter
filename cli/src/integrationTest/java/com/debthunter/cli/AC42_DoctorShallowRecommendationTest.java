package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.testkit.FixtureRepoBuilder;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * AC-42: {@code debt-hunter doctor --repo <shallow>} prints recommendations, exercised through the
 * real CLI entry point (subcommand registration and option parsing), not the test constructor.
 */
@Tag("integration")
class AC42_DoctorShallowRecommendationTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac42_doctorPrintsRecommendationsForAShallowRepository() throws Exception {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");
    Files.createFile(fixture.path().resolve(".git").resolve("shallow"));

    PrintStream originalOut = System.out;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    int exitCode;
    try {
      System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
      exitCode =
          new CommandLine(new DebtHunterCli())
              .execute("doctor", "--repo", fixture.path().toString());
    } finally {
      System.setOut(originalOut);
    }

    assertThat(exitCode).isZero();
    String output = buffer.toString(StandardCharsets.UTF_8);
    assertThat(output)
        .contains("Debt Hunter Doctor")
        .contains("History depth: shallow")
        .contains("Recommendations:")
        .contains("git fetch --unshallow")
        .contains("fetch-depth: 0");
  }
}
