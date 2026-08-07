package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.output.JsonReporter;
import com.debthunter.testkit.FixtureRepoBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** AC-04: identical inputs on two runs produce identical JSON once volatile fields are excluded. */
@Tag("integration")
class AC04_DeterministicOutputTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac04_twoRunsOfTheSameInputsProduceEquivalentJsonExcludingVolatileFields(
      @TempDir Path outputRoot) throws IOException {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo {}", "add Foo")
            .commitFile("Bar.java", "class Bar {}", "add Bar");

    Path outputDirA = outputRoot.resolve("run-a");
    Path outputDirB = outputRoot.resolve("run-b");

    int exitCodeA =
        new CommandLine(new DebtHunterCli())
            .execute(
                "scan", "--repo", fixture.path().toString(), "--output-dir", outputDirA.toString());
    int exitCodeB =
        new CommandLine(new DebtHunterCli())
            .execute(
                "scan", "--repo", fixture.path().toString(), "--output-dir", outputDirB.toString());

    assertThat(exitCodeA).isEqualTo(exitCodeB);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode jsonA = mapper.readTree(outputDirA.resolve(JsonReporter.FILE_NAME).toFile());
    JsonNode jsonB = mapper.readTree(outputDirB.resolve(JsonReporter.FILE_NAME).toFile());

    stripVolatileRunFields(jsonA);
    stripVolatileRunFields(jsonB);

    assertThat(jsonA).isEqualTo(jsonB);
  }

  private void stripVolatileRunFields(JsonNode root) {
    ObjectNode run = (ObjectNode) root.get("run");
    run.remove("id");
    run.remove("timestamp");
  }
}
