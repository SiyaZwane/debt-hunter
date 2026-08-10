package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.policy.PolicyBundleParser;
import com.debthunter.policy.PolicyComposer;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-58: {@code policy} shows the effective, composed policy for a repository — the central
 * bundle's rules plus any repo-local tightening, each labelled with where it came from — without
 * needing to actually run a scan.
 */
@Tag("integration")
class AC58_ShowEffectiveTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac58_showsCentralAndTightenedLocalRulesWithProvenance(@TempDir Path workDir)
      throws IOException {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    Path centralPolicyPath = workDir.resolve("policy.yml");
    Files.writeString(
        centralPolicyPath,
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-critical
                severity: CRITICAL
                maxCount: 5
        """);
    Files.writeString(
        fixture.path().resolve(PolicyComposer.LOCAL_POLICY_FILE_NAME),
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-critical
                severity: CRITICAL
                maxCount: 1
        """);

    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PolicyCommand command =
        new PolicyCommand(
            fixture.path(),
            centralPolicyPath,
            new PolicyBundleParser(),
            new PrintStream(captured, false, StandardCharsets.UTF_8));

    int exitCode = command.call();

    assertThat(exitCode).isZero();
    String output = captured.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("policy.main.rules[no-critical]");
    assertThat(output).contains("local (tightened)");
    assertThat(output).contains("maxCount=1");
    assertThat(output).contains("was severity=CRITICAL, maxCount=5");
  }
}
