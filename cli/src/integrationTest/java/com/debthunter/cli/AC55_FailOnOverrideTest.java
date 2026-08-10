package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.publish.PublishUseCase;
import com.debthunter.application.scan.ExitCode;
import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.debthunter.engine.spi.AnalysisEngine;
import com.debthunter.engine.spi.CostClass;
import com.debthunter.engine.spi.EngineDescriptor;
import com.debthunter.engine.spi.EngineResult;
import com.debthunter.engine.spi.RepositoryContext;
import com.debthunter.integration.ResultUploader;
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
 * AC-55: {@code --fail-on} gates on a severity threshold even with no policy bundle configured —
 * useful for a local developer run without first authoring a policy file — and only takes effect
 * when actually given; the exact same scan without it still passes. A baseline is configured so
 * this is real enforcement, not observe mode (see AC-33).
 */
@Tag("integration")
class AC55_FailOnOverrideTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac55_failOnFailsAScanThatWouldOtherwisePassWithNoPolicyConfigured(
      @TempDir Path outputDir, @TempDir Path workDir) {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    int exitCode = runScan(outputDir, workDir, Severity.HIGH);

    assertThat(exitCode).isEqualTo(ExitCode.POLICY_VIOLATED.code());
  }

  @Test
  void ac55_withoutFailOnTheSameScanPasses(@TempDir Path outputDir, @TempDir Path workDir) {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    int exitCode = runScan(outputDir, workDir, null);

    assertThat(exitCode).isEqualTo(ExitCode.POLICY_SATISFIED.code());
  }

  private int runScan(Path outputDir, Path workDir, Severity failOn) {
    Path baselinePath = writeEmptyBaseline(workDir);

    Finding highFinding =
        Finding.builder()
            .id("f-1")
            .ruleId("static.rule")
            .category(Category.STATIC_ANALYSIS)
            .severity(Severity.HIGH)
            .path("Foo.java")
            .message("Foo.java has an issue")
            .fingerprint("fp-1")
            .build();

    AnalysisEngine engine = mock(AnalysisEngine.class);
    when(engine.descriptor())
        .thenReturn(
            new EngineDescriptor("fake", "1.0", List.of(Category.STATIC_ANALYSIS), CostClass.LOW));
    when(engine.supports(any(RepositoryContext.class))).thenReturn(true);
    when(engine.analyse(any(), any()))
        .thenReturn(EngineResult.ok(List.of(highFinding), List.of(), 5));

    ScanCommand command =
        new ScanCommand(
            fixture.path(),
            outputDir,
            null,
            baselinePath,
            defaultScanUseCase(),
            List.of(engine),
            new PublishUseCase(mock(ResultUploader.class)),
            null,
            false,
            Map.of(),
            failOn);

    return command.call();
  }

  private Path writeEmptyBaseline(Path dir) {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("baseline-run")
            .toolVersion("0.1.0-test")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    ScanResult baseline =
        new ScanResult(run, List.of(), Map.of(), PolicyResult.passed("unversioned"));
    return new BaselineWriter().write(baseline, dir);
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
