package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.ai.HttpExplainer;
import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
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
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-47: an unreachable AI endpoint affects only {@code explain} itself — a scan run in the same
 * process is completely unaffected, because the two commands share no collaborator and the scan
 * path never calls into the {@code ai} module at all.
 */
@Tag("integration")
class AC47_AIEndpointUnavailableNoImpactTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac47_anUnreachableExplainEndpointDoesNotAffectAScan(
      @TempDir Path outputDir, @TempDir Path reportDir) {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    Path reportFile = writeReport(reportDir);
    ExplainCommand explainCommand =
        new ExplainCommand(
            reportFile,
            "f-1",
            URI.create("http://127.0.0.1:1"),
            new JsonReporter(),
            new HttpExplainer());

    int explainExitCode = explainCommand.call();

    assertThat(explainExitCode).isZero();

    ScanCommand scanCommand =
        new ScanCommand(fixture.path(), outputDir, defaultScanUseCase(), List.of());

    int scanExitCode = scanCommand.call();

    assertThat(scanExitCode).isIn(0, 1);
    assertThat(outputDir.resolve(JsonReporter.FILE_NAME)).exists();
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
