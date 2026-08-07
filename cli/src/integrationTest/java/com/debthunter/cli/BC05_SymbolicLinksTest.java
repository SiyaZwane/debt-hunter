package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.output.JsonReporter;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * BC-05: a repository path reached through a symbolic link scans exactly as the real path would.
 */
@Tag("integration")
@DisabledOnOs(OS.WINDOWS)
class BC05_SymbolicLinksTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void bc05_symlinkedRepoPathScansSuccessfully(@TempDir Path linkParent, @TempDir Path outputDir)
      throws IOException {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    Path symlink = linkParent.resolve("repo-link");
    Files.createSymbolicLink(symlink, fixture.path());

    int exitCode =
        new CommandLine(new DebtHunterCli())
            .execute("scan", "--repo", symlink.toString(), "--output-dir", outputDir.toString());

    assertThat(exitCode).isIn(0, 1);
    assertThat(outputDir.resolve(JsonReporter.FILE_NAME)).exists();
  }
}
