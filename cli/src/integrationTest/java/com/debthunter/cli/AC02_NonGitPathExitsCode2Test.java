package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** AC-02: a non-Git path exits 2, prints a diagnostic on stderr, and writes no output files. */
@Tag("integration")
class AC02_NonGitPathExitsCode2Test {

  @Test
  void ac02_nonGitPathExitsCode2WithDiagnosticAndNoOutputFiles(
      @TempDir Path plainDirectory, @TempDir Path outputDir) {
    PrintStream originalErr = System.err;
    ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
    System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));

    int exitCode;
    try {
      exitCode =
          new CommandLine(new DebtHunterCli())
              .execute(
                  "scan",
                  "--repo",
                  plainDirectory.toString(),
                  "--output-dir",
                  outputDir.toString());
    } finally {
      System.setErr(originalErr);
    }

    assertThat(exitCode).isEqualTo(2);
    assertThat(capturedErr.toString(StandardCharsets.UTF_8)).isNotBlank();
    assertThat(outputDir.toFile().list()).isEmpty();
  }
}
