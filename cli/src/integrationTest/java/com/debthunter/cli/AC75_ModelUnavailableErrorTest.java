package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.ai.ProvenanceLabeller;
import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.debthunter.output.JsonReporter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * AC-75: when the AI model endpoint is unreachable, {@code explain} clearly signals the failure on
 * stderr and prints no fabricated, unlabelled explanation — while still returning exit code 0, per
 * AC-47 (an AI outage must never look like a blocking failure to callers).
 */
@Tag("integration")
class AC75_ModelUnavailableErrorTest {

  @Test
  void ac75_anUnreachableEndpointSignalsFailureWithoutBreakingTheExitCode(@TempDir Path reportDir) {
    Path reportFile = writeReport(reportDir);

    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;
    ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
    int exitCode;
    try {
      System.setOut(new PrintStream(outBuffer, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(errBuffer, true, StandardCharsets.UTF_8));
      exitCode =
          new CommandLine(new DebtHunterCli())
              .execute(
                  "explain",
                  "--report",
                  reportFile.toString(),
                  "--finding-id",
                  "f-1",
                  "--explain-endpoint",
                  "http://127.0.0.1:1",
                  "--explain-timeout",
                  "PT1S");
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }

    assertThat(exitCode).isZero();
    String out = outBuffer.toString(StandardCharsets.UTF_8);
    String err = errBuffer.toString(StandardCharsets.UTF_8);
    assertThat(err).contains("Explanation unavailable");
    assertThat(out).doesNotContain(ProvenanceLabeller.LABEL);
  }

  private Path writeReport(Path reportDir) {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0-test")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    Finding finding =
        Finding.builder()
            .id("f-1")
            .ruleId("hotspot.rule")
            .category(Category.HOTSPOT)
            .severity(Severity.HIGH)
            .path("Foo.java")
            .message("Foo.java changes often")
            .fingerprint("fp-1")
            .build();
    ScanResult scanResult =
        new ScanResult(run, List.of(finding), Map.of(), PolicyResult.passed("unversioned"));
    return new JsonReporter().write(scanResult, reportDir);
  }
}
