package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.output.BaselineWriter;
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
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-29: a baseline written by an incompatible major tool version fails the scan with a distinct
 * exit code, rather than being silently ignored or crashing.
 */
@Tag("integration")
class AC29_IncompatibleBaselineTest {

  private static final int EXIT_BASELINE_INCOMPATIBLE = 5;

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac29_incompatibleMajorToolVersionFailsWithADistinctExitCode(
      @TempDir Path outputDir, @TempDir Path baselineDir) {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    AnalysisRun incompatibleRun =
        AnalysisRun.builder()
            .id("baseline-run")
            .toolVersion("9.0.0")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    ScanResult incompatibleBaseline =
        new ScanResult(incompatibleRun, List.of(), Map.of(), PolicyResult.passed("unversioned"));
    Path baselinePath = new BaselineWriter().write(incompatibleBaseline, baselineDir);

    ScanUseCase scanUseCase =
        new ScanUseCase(
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
    ScanCommand command =
        new ScanCommand(fixture.path(), outputDir, baselinePath, scanUseCase, List.of());

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(EXIT_BASELINE_INCOMPATIBLE);
    assertThat(outputDir.resolve(JsonReporter.FILE_NAME)).doesNotExist();
  }
}
