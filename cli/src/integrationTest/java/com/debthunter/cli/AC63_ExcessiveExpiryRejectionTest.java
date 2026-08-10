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
import com.debthunter.policy.SuppressionRegistry;
import com.debthunter.repository.GitHistoryProvider;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-63: a suppression whose expiry exceeds the policy's {@code suppressions.maxExpiryDays} fails
 * the scan with exit code 2 (configuration error) — a suppression that outlives what the policy
 * allows is a configuration problem, not a policy violation, and it is rejected outright rather
 * than silently truncated.
 */
@Tag("integration")
class AC63_ExcessiveExpiryRejectionTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac63_aSuppressionExceedingMaxExpiryDaysFailsWithConfigurationErrorExitCode(
      @TempDir Path outputDir, @TempDir Path workDir) throws IOException {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    Path policyPath = workDir.resolve("policy.yml");
    Files.writeString(
        policyPath,
        """
        version: "1.0"
        suppressions:
          maxExpiryDays: 30
        """);

    LocalDate wayBeyondTheCeiling = LocalDate.now(ZoneOffset.UTC).plusDays(365);
    Files.writeString(
        fixture.path().resolve(SuppressionRegistry.SUPPRESSIONS_FILE_NAME),
        """
        suppressions:
          - fingerprint: fp-f-1
            owner: alice
            reason: "Tracked in JIRA-123"
            expires: "%s"
        """
            .formatted(wayBeyondTheCeiling));

    ScanCommand command =
        new ScanCommand(
            fixture.path(), outputDir, policyPath, null, defaultScanUseCase(), List.of());

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(ExitCode.CONFIGURATION_ERROR.code());
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
