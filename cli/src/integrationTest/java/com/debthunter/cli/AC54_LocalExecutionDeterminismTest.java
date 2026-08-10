package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.output.JsonReporter;
import com.debthunter.output.MarkdownReporter;
import com.debthunter.output.MetricsReporter;
import com.debthunter.output.SarifReporter;
import com.debthunter.policy.BaselineComparator;
import com.debthunter.policy.BaselineResolver;
import com.debthunter.policy.PolicyBundleParser;
import com.debthunter.policy.PolicyEvaluator;
import com.debthunter.repository.GitHistoryProvider;
import com.debthunter.testkit.FixtureRepoBuilder;
import com.debthunter.testkit.VolatileFieldMasker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-54: the documented contract works the same way for a developer running the tool directly on
 * their own machine as it does in CI or a container — nothing here depends on a CI-only environment
 * variable, a container-only filesystem layout, or any other hidden assumption. This is primarily a
 * verification step: {@code scan} and {@code doctor} are exercised exactly as a developer would
 * invoke them locally, against a real, local Git repository.
 */
@Tag("integration")
class AC54_LocalExecutionDeterminismTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac54_doctorReportsFullHistoryForALocalRepository() {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");
    ByteArrayOutputStream captured = new ByteArrayOutputStream();

    DoctorCommand doctor =
        new DoctorCommand(
            fixture.path(),
            new GitHistoryProvider(),
            new PrintStream(captured, false, StandardCharsets.UTF_8));

    int exitCode = doctor.call();

    assertThat(exitCode).isZero();
    String output = captured.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("History depth: full");
    assertThat(output).contains("No issues found: full history is available.");
  }

  @Test
  void ac54_aLocalScanIsDeterministicAcrossTwoSeparateRuns(@TempDir Path outputRoot)
      throws IOException {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo {}", "add Foo")
            .commitFile("Bar.java", "class Bar {}", "add Bar");

    Path firstRunOutput = outputRoot.resolve("first");
    Path secondRunOutput = outputRoot.resolve("second");

    int firstExitCode = runScan(firstRunOutput);
    int secondExitCode = runScan(secondRunOutput);

    assertThat(firstExitCode).isEqualTo(secondExitCode);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode first = mapper.readTree(firstRunOutput.resolve(JsonReporter.FILE_NAME).toFile());
    JsonNode second = mapper.readTree(secondRunOutput.resolve(JsonReporter.FILE_NAME).toFile());
    VolatileFieldMasker.mask(first);
    VolatileFieldMasker.mask(second);

    assertThat(first).isEqualTo(second);
  }

  private int runScan(Path outputDir) {
    ScanCommand command =
        new ScanCommand(fixture.path(), outputDir, defaultScanUseCase(), List.of());
    return command.call();
  }

  private ScanUseCase defaultScanUseCase() {
    return new ScanUseCase(
        new GitHistoryProvider(),
        new JsonReporter(),
        new MarkdownReporter(),
        new MetricsReporter(),
        new SarifReporter(),
        new BaselineResolver(),
        new BaselineComparator(),
        new PolicyBundleParser(),
        new PolicyEvaluator(),
        new HistoryDepthEnforcer(),
        "0.1.0-test");
  }
}
