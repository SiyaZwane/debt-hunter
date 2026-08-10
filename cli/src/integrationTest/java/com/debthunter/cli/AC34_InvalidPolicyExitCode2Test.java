package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.scan.ExitCode;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-34: a policy bundle whose YAML is invalid fails the scan with exit code 2, before analysis
 * runs.
 */
@Tag("integration")
class AC34_InvalidPolicyExitCode2Test {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac34_malformedPolicyYamlFailsWithExitCodeTwo(@TempDir Path outputDir, @TempDir Path workDir)
      throws Exception {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    Path policyPath = workDir.resolve("policy.yml");
    Files.writeString(policyPath, "version: [unterminated");

    ScanUseCase scanUseCase =
        new ScanUseCase(
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
    ScanCommand command =
        new ScanCommand(fixture.path(), outputDir, policyPath, null, scanUseCase, List.of());

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(ExitCode.CONFIGURATION_ERROR.code());
    assertThat(outputDir.resolve(JsonReporter.FILE_NAME)).doesNotExist();
  }
}
