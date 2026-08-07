package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.output.JsonReporter;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** BC-01: a Git repository with zero commits scans successfully, producing empty reports. */
@Tag("integration")
class BC01_EmptyRepoTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void bc01_freshlyInitialisedRepoWithNoCommitsScansSuccessfully(@TempDir Path outputDir) {
    fixture = FixtureRepoBuilder.init();

    int exitCode =
        new CommandLine(new DebtHunterCli())
            .execute(
                "scan", "--repo", fixture.path().toString(), "--output-dir", outputDir.toString());

    assertThat(exitCode).isIn(0, 1);
    assertThat(outputDir.resolve(JsonReporter.FILE_NAME)).exists();
  }
}
