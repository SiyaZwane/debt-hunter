package com.debthunter.engine.codemaat;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.testkit.FixtureRepoBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeMaatLogWriterTest {

  private final CodeMaatLogWriter logWriter = new CodeMaatLogWriter();
  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void withNoSinceEveryCommitIsIncluded(@TempDir Path outputDir) throws IOException {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo {}", "add Foo")
            .commitFile("Bar.java", "class Bar {}", "add Bar");

    Path logFile = logWriter.writeLog(fixture.path(), outputDir.resolve("log.txt"));

    String log = Files.readString(logFile, StandardCharsets.UTF_8);
    assertThat(log).contains("Foo.java").contains("Bar.java");
  }

  @Test
  void aSinceFarInTheFutureExcludesEveryCommit(@TempDir Path outputDir) throws IOException {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    Path logFile =
        logWriter.writeLog(
            fixture.path(), outputDir.resolve("log.txt"), Instant.parse("2999-01-01T00:00:00Z"));

    String log = Files.readString(logFile, StandardCharsets.UTF_8);
    assertThat(log).isBlank();
  }

  @Test
  void aSinceFarInThePastIncludesEveryCommit(@TempDir Path outputDir) throws IOException {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo {}", "add Foo")
            .commitFile("Bar.java", "class Bar {}", "add Bar");

    Path logFile =
        logWriter.writeLog(
            fixture.path(), outputDir.resolve("log.txt"), Instant.parse("2000-01-01T00:00:00Z"));

    String log = Files.readString(logFile, StandardCharsets.UTF_8);
    assertThat(log).contains("Foo.java").contains("Bar.java");
  }

  @Test
  void aSinceBetweenTwoCommitsExcludesOnlyTheOlderOne(@TempDir Path outputDir)
      throws IOException, InterruptedException {
    fixture = FixtureRepoBuilder.init().commitFile("Old.java", "class Old {}", "add Old");
    // Git commit timestamps only have second-level resolution, so each gap must clear a full
    // second to guarantee "Old", the cutoff, and "New" land in three genuinely distinct seconds.
    Thread.sleep(1100);
    Instant cutoff = Instant.now();
    Thread.sleep(1100);
    fixture.commitFile("New.java", "class New {}", "add New");

    Path logFile = logWriter.writeLog(fixture.path(), outputDir.resolve("log.txt"), cutoff);

    String log = Files.readString(logFile, StandardCharsets.UTF_8);
    assertThat(log).contains("New.java").doesNotContain("Old.java");
  }
}
