package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.debthunter.application.history.HistoryDepthEnforcer;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-33: a new finding that violates a configured policy rule fails the scan with exit code 1, when
 * a baseline exists (so this is real enforcement, not observe mode).
 */
@Tag("integration")
class AC33_PolicyViolationExitCode1Test {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac33_aViolatedRuleWithABaselineFailsWithExitCodeOne(
      @TempDir Path outputDir, @TempDir Path workDir) throws Exception {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    Path policyPath = workDir.resolve("policy.yml");
    Files.writeString(
        policyPath,
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-new-critical
                severity: CRITICAL
                maxCount: 0
        """);
    Path baselinePath = writeEmptyBaseline(workDir);

    AnalysisEngine engine = mock(AnalysisEngine.class);
    when(engine.descriptor())
        .thenReturn(new EngineDescriptor("fake", "1.0", List.of(Category.HOTSPOT), CostClass.LOW));
    when(engine.supports(any(RepositoryContext.class))).thenReturn(true);
    when(engine.analyse(any(), any()))
        .thenReturn(
            EngineResult.ok(
                List.of(
                    Finding.builder()
                        .id("f-1")
                        .ruleId("hotspot.rule")
                        .category(Category.HOTSPOT)
                        .severity(Severity.CRITICAL)
                        .path("Foo.java")
                        .message("Foo.java is critically overdue")
                        .fingerprint("fp-f-1")
                        .build()),
                List.of(),
                5));

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
        new ScanCommand(
            fixture.path(), outputDir, policyPath, baselinePath, scanUseCase, List.of(engine));

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(ExitCode.POLICY_VIOLATED.code());
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
}
