package com.debthunter.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Entry point for the Debt Hunter command-line tool.
 *
 * <p>This is the only class in the codebase permitted to call {@link System#exit(int)}; every other
 * component reports its outcome as a return value.
 */
@Command(
    name = "debt-hunter",
    mixinStandardHelpOptions = true,
    version = "debt-hunter 0.1.0-SNAPSHOT",
    description = "Deterministic, containerised command-line technical-debt analyser.",
    subcommands = {
      ScanCommand.class,
      DoctorCommand.class,
      PublishCommand.class,
      ExplainCommand.class,
      PolicyCommand.class
    })
public final class DebtHunterCli implements Runnable {

  /**
   * Runs the root command. With no subcommand and no {@code --version}/{@code --help}, prints
   * usage.
   */
  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  /**
   * Builds the picocli command tree and executes it against the given arguments.
   *
   * @param args raw command-line arguments
   * @return the exit code the process should terminate with
   */
  static int execute(String[] args) {
    return new CommandLine(new DebtHunterCli()).execute(args);
  }

  /**
   * Process entry point: forces a deterministic time zone and locale, delegates to {@link
   * #execute(String[])}, and translates the result into a process exit code.
   *
   * <p>Enforcement happens here rather than in {@link #execute(String[])} deliberately: {@link
   * #execute(String[])} is what tests call directly, and AC-14's cross-platform determinism test
   * relies on being able to set the JVM's default time zone and locale itself before invoking it.
   *
   * @param args raw command-line arguments
   */
  public static void main(String[] args) {
    DeterminismEnforcer.enforce();
    System.exit(execute(args));
  }
}
