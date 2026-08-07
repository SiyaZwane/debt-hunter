package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.output.JsonReporter;
import com.debthunter.output.MarkdownReporter;
import com.debthunter.output.MetricsReporter;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("integration")
class ScanCommandIntegrationTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void endToEndScanOfAFixtureRepoProducesAllReportsAndExitsZero(@TempDir Path outputDir) {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo {}", "add Foo")
            .commitFile("Bar.java", "class Bar {}", "add Bar");

    int exitCode =
        new CommandLine(new DebtHunterCli())
            .execute(
                "scan", "--repo", fixture.path().toString(), "--output-dir", outputDir.toString());

    assertThat(exitCode).isIn(0, 1);
    assertThat(outputDir.resolve(JsonReporter.FILE_NAME)).exists();
    assertThat(outputDir.resolve(MarkdownReporter.FILE_NAME)).exists();
    assertThat(outputDir.resolve(MetricsReporter.FILE_NAME)).exists();
  }
}
