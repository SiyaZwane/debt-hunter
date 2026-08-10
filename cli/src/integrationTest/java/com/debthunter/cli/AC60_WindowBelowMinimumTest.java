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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-60: a {@code --history-window-since} scan is recorded as {@link
 * com.debthunter.domain.HistoryDepth#PARTIAL}, which fails a policy that requires {@code FULL}
 * history — exactly the same way a shallow clone would, since a self-imposed history restriction
 * has the same effect on confidence and gating as an environmental one.
 */
@Tag("integration")
class AC60_WindowBelowMinimumTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac60_aWindowedScanFailsAPolicyRequiringFullHistory(
      @TempDir Path outputDir, @TempDir Path workDir) throws IOException {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    Path policyPath = workDir.resolve("policy.yml");
    Files.writeString(
        policyPath,
        """
        version: "1.0"
        analysis:
          minimumHistoryDepth: FULL
        """);

    ScanCommand command =
        new ScanCommand(
            fixture.path(),
            outputDir,
            policyPath,
            null,
            defaultScanUseCase(),
            List.of(),
            new com.debthunter.application.publish.PublishUseCase(
                org.mockito.Mockito.mock(com.debthunter.integration.ResultUploader.class)),
            null,
            false,
            java.util.Map.of(),
            null,
            Instant.parse("2020-01-01T00:00:00Z"));

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(ExitCode.INSUFFICIENT_HISTORY.code());
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
