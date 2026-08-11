package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.debthunter.ai.Explainer;
import com.debthunter.ai.Explanation;
import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.debthunter.output.JsonReporter;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExplainCommandTest {

  private static final URI ENDPOINT = URI.create("https://example.invalid/explain");

  @Test
  void anAvailableExplanationReturnsThePolicySatisfiedExitCode(@TempDir Path outputDir) {
    Path reportFile = writeReport(outputDir);
    Explainer explainer = mock(Explainer.class);
    when(explainer.explain(any(), any(), any()))
        .thenReturn(Explanation.ofAvailable("f-1", "it's fine"));
    ExplainCommand command =
        new ExplainCommand(reportFile, "f-1", ENDPOINT, new JsonReporter(), explainer);

    int exitCode = command.call();

    assertThat(exitCode).isZero();
  }

  @Test
  void anUnavailableExplanationStillReturnsThePolicySatisfiedExitCode(@TempDir Path outputDir) {
    Path reportFile = writeReport(outputDir);
    Explainer explainer = mock(Explainer.class);
    when(explainer.explain(any(), any(), any()))
        .thenReturn(Explanation.ofUnavailable("f-1", "connection refused"));
    ExplainCommand command =
        new ExplainCommand(reportFile, "f-1", ENDPOINT, new JsonReporter(), explainer);

    int exitCode = command.call();

    assertThat(exitCode).isZero();
  }

  @Test
  void aMissingReportFileReturnsTheConfigurationErrorExitCode(@TempDir Path outputDir) {
    Explainer explainer = mock(Explainer.class);
    ExplainCommand command =
        new ExplainCommand(
            outputDir.resolve("missing.json"), "f-1", ENDPOINT, new JsonReporter(), explainer);

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void anUnknownFindingIdReturnsTheConfigurationErrorExitCode(@TempDir Path outputDir) {
    Path reportFile = writeReport(outputDir);
    Explainer explainer = mock(Explainer.class);
    ExplainCommand command =
        new ExplainCommand(reportFile, "no-such-finding", ENDPOINT, new JsonReporter(), explainer);

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(2);
  }

  private Path writeReport(Path outputDir) {
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
    return new JsonReporter().write(scanResult, outputDir);
  }
}
