package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.testkit.FixtureRepoBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** BC-03: an unwritable output directory fails cleanly instead of crashing. */
@Tag("integration")
@DisabledOnOs(OS.WINDOWS)
class BC03_ReadOnlyOutputDirTest {

  private static final int EXIT_INTERNAL_ERROR = 10;

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void bc03_readOnlyOutputDirectoryFailsWithInternalErrorInsteadOfCrashing(
      @TempDir Path outputDir) {
    Assumptions.assumeTrue(
        !"root".equals(System.getProperty("user.name")), "permission bits are meaningless as root");

    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    assertThat(outputDir.toFile().setWritable(false)).isTrue();
    try {
      int exitCode =
          new CommandLine(new DebtHunterCli())
              .execute(
                  "scan",
                  "--repo",
                  fixture.path().toString(),
                  "--output-dir",
                  outputDir.toString());

      assertThat(exitCode).isEqualTo(EXIT_INTERNAL_ERROR);
    } finally {
      outputDir.toFile().setWritable(true);
    }
  }
}
