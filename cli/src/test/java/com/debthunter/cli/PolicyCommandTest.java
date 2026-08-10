package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.policy.PolicyBundleParser;
import com.debthunter.policy.PolicyComposer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolicyCommandTest {

  @Test
  void withNoPolicyConfiguredEveryFieldIsReportedAsCentral(@TempDir Path repoRoot) {
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PolicyCommand command =
        new PolicyCommand(
            repoRoot,
            null,
            new PolicyBundleParser(),
            new PrintStream(captured, false, StandardCharsets.UTF_8));

    int exitCode = command.call();

    assertThat(exitCode).isZero();
    String output = captured.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("Effective policy for " + repoRoot);
    assertThat(output).contains("[central]");
  }

  @Test
  void aTightenedLocalOverrideIsReportedInTheOutput(@TempDir Path repoRoot) throws IOException {
    Files.writeString(
        repoRoot.resolve(PolicyComposer.LOCAL_POLICY_FILE_NAME),
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-new-debt
                severity: MEDIUM
                maxCount: 1
        """);
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PolicyCommand command =
        new PolicyCommand(
            repoRoot,
            null,
            new PolicyBundleParser(),
            new PrintStream(captured, false, StandardCharsets.UTF_8));

    int exitCode = command.call();

    assertThat(exitCode).isZero();
    String output = captured.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("policy.main.rules[no-new-debt]");
    assertThat(output).contains("local (new)");
  }

  @Test
  void anUnreadableCentralPolicyFileReturnsTheConfigurationErrorExitCode(@TempDir Path repoRoot) {
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PolicyCommand command =
        new PolicyCommand(
            repoRoot,
            repoRoot.resolve("missing.yml"),
            new PolicyBundleParser(),
            new PrintStream(captured, false, StandardCharsets.UTF_8));

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void aLoosenedLocalOverrideReturnsTheConfigurationErrorExitCode(
      @TempDir Path repoRoot, @TempDir Path workDir) throws IOException {
    Path centralPolicy = workDir.resolve("central.yml");
    Files.writeString(
        centralPolicy,
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-critical
                severity: CRITICAL
                maxCount: 0
        """);
    Files.writeString(
        repoRoot.resolve(PolicyComposer.LOCAL_POLICY_FILE_NAME),
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-critical
                severity: CRITICAL
                maxCount: 5
        """);
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PolicyCommand command =
        new PolicyCommand(
            repoRoot,
            centralPolicy,
            new PolicyBundleParser(),
            new PrintStream(captured, false, StandardCharsets.UTF_8));

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(2);
  }
}
