package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** Smoke test for the walking skeleton: the CLI must at least parse {@code --version}. */
class DebtHunterCliTest {

  @Test
  void versionFlagExitsZero() {
    int exitCode = new CommandLine(new DebtHunterCli()).execute("--version");

    assertThat(exitCode).isZero();
  }

  @Test
  void executeDelegatesToPicocliAndReturnsExitCode() {
    int exitCode = DebtHunterCli.execute(new String[] {"--version"});

    assertThat(exitCode).isZero();
  }

  @Test
  void runWithNoArgsPrintsUsageWithoutThrowing() {
    assertThatCode(() -> new DebtHunterCli().run()).doesNotThrowAnyException();
  }
}
