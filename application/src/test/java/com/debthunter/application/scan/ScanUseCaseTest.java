package com.debthunter.application.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.domain.Category;
import com.debthunter.domain.DebtMetric;
import com.debthunter.domain.EngineHealth;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import com.debthunter.engine.spi.AnalysisEngine;
import com.debthunter.engine.spi.AnalysisMode;
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
import com.debthunter.repository.RepositoryHistoryProvider;
import com.debthunter.repository.RepositoryInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScanUseCaseTest {

  private static final RepositoryInfo GIT_REPO_INFO =
      new RepositoryInfo(true, false, false, 3, "abc123", "main");

  private ScanUseCase newUseCase(RepositoryHistoryProvider historyProvider, Duration timeout) {
    return new ScanUseCase(
        historyProvider,
        new JsonReporter(),
        new MarkdownReporter(),
        new MetricsReporter(),
        new SarifReporter(),
        new BaselineResolver(),
        new BaselineComparator(),
        new PolicyBundleParser(),
        new PolicyEvaluator(),
        new HistoryDepthEnforcer(),
        "0.1.0-test",
        timeout,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  private ScanRequest requestFor(Path repo, Path outputDir, List<AnalysisEngine> engines) {
    return new ScanRequest(
        repo, outputDir, AnalysisMode.FULL, null, null, engines, null, false, null, null);
  }

  private ScanRequest requestFor(
      Path repo, Path outputDir, List<AnalysisEngine> engines, Path policyPath, Path baselinePath) {
    return new ScanRequest(
        repo,
        outputDir,
        AnalysisMode.FULL,
        null,
        policyPath,
        engines,
        null,
        false,
        null,
        baselinePath);
  }

  private AnalysisEngine engineReturning(EngineResult result) {
    AnalysisEngine engine = mock(AnalysisEngine.class);
    when(engine.descriptor())
        .thenReturn(new EngineDescriptor("fake", "1.0", List.of(Category.CUSTOM), CostClass.LOW));
    when(engine.supports(any(RepositoryContext.class))).thenReturn(true);
    when(engine.analyse(any(), any())).thenReturn(result);
    return engine;
  }

  @Test
  void engineOrchestrationCollectsFindingsAndMetricsFromEachSupportingEngine(
      @TempDir Path repo, @TempDir Path outputDir) {
    RepositoryHistoryProvider historyProvider = mock(RepositoryHistoryProvider.class);
    when(historyProvider.inspect(repo)).thenReturn(GIT_REPO_INFO);

    Finding finding = finding("f-1");
    DebtMetric metric = new DebtMetric("churn", 3.0, "repo");
    AnalysisEngine engine = engineReturning(EngineResult.ok(List.of(finding), List.of(metric), 10));

    ScanOutcome outcome =
        newUseCase(historyProvider, Duration.ofSeconds(5))
            .execute(requestFor(repo, outputDir, List.of(engine)));

    assertThat(outcome.exitCode()).isZero();
    // With no baseline configured, every finding compares as NEW.
    assertThat(outcome.scanResult().findings()).containsExactly(finding.withIsNew(true));
    assertThat(outcome.scanResult().metrics()).containsEntry("churn", metric);
    assertThat(outcome.scanResult().run().engines()).hasSize(1);
    assertThat(outcome.scanResult().run().engines().get(0).status()).isEqualTo(EngineHealth.OK);
  }

  @Test
  void enginesThatDoNotSupportTheRepositoryAreSkipped(@TempDir Path repo, @TempDir Path outputDir) {
    RepositoryHistoryProvider historyProvider = mock(RepositoryHistoryProvider.class);
    when(historyProvider.inspect(repo)).thenReturn(GIT_REPO_INFO);

    AnalysisEngine unsupported = mock(AnalysisEngine.class);
    when(unsupported.supports(any(RepositoryContext.class))).thenReturn(false);

    ScanOutcome outcome =
        newUseCase(historyProvider, Duration.ofSeconds(5))
            .execute(requestFor(repo, outputDir, List.of(unsupported)));

    assertThat(outcome.scanResult().findings()).isEmpty();
    assertThat(outcome.scanResult().run().engines()).isEmpty();
    verify(unsupported, never()).analyse(any(), any());
  }

  @Test
  void timeoutHandlingRecordsFailedEngineStatusAndContinues(
      @TempDir Path repo, @TempDir Path outputDir) {
    RepositoryHistoryProvider historyProvider = mock(RepositoryHistoryProvider.class);
    when(historyProvider.inspect(repo)).thenReturn(GIT_REPO_INFO);

    AnalysisEngine slowEngine = mock(AnalysisEngine.class);
    when(slowEngine.descriptor())
        .thenReturn(new EngineDescriptor("slow", "1.0", List.of(), CostClass.HIGH));
    when(slowEngine.supports(any(RepositoryContext.class))).thenReturn(true);
    when(slowEngine.analyse(any(), any()))
        .thenAnswer(
            invocation -> {
              Thread.sleep(2000);
              return EngineResult.ok(List.of(), List.of(), 2000);
            });

    ScanOutcome outcome =
        newUseCase(historyProvider, Duration.ofMillis(100))
            .execute(requestFor(repo, outputDir, List.of(slowEngine)));

    assertThat(outcome.exitCode()).isZero();
    assertThat(outcome.scanResult().isDegraded()).isTrue();
    assertThat(outcome.scanResult().run().engines()).hasSize(1);
    assertThat(outcome.scanResult().run().engines().get(0).status()).isEqualTo(EngineHealth.FAILED);
    assertThat(outcome.scanResult().run().engines().get(0).reason()).contains("timed out");
  }

  @Test
  void degradedFlagPropagatesToScanResultWhenAnyEngineFails(
      @TempDir Path repo, @TempDir Path outputDir) {
    RepositoryHistoryProvider historyProvider = mock(RepositoryHistoryProvider.class);
    when(historyProvider.inspect(repo)).thenReturn(GIT_REPO_INFO);

    AnalysisEngine okEngine =
        engineReturning(EngineResult.ok(List.of(finding("ok")), List.of(), 5));
    AnalysisEngine failingEngine = engineReturning(EngineResult.failed("boom", 5));

    ScanOutcome outcome =
        newUseCase(historyProvider, Duration.ofSeconds(5))
            .execute(requestFor(repo, outputDir, List.of(okEngine, failingEngine)));

    assertThat(outcome.scanResult().isDegraded()).isTrue();
    assertThat(outcome.scanResult().findings()).hasSize(1);
  }

  @Test
  void aMalformedPolicyFileFailsTheScanWithAConfigurationError(
      @TempDir Path repo, @TempDir Path outputDir) throws IOException {
    RepositoryHistoryProvider historyProvider = mock(RepositoryHistoryProvider.class);
    when(historyProvider.inspect(repo)).thenReturn(GIT_REPO_INFO);
    Path policyPath = outputDir.resolve("policy.yml");
    Files.writeString(policyPath, "version: [unterminated");

    ScanOutcome outcome =
        newUseCase(historyProvider, Duration.ofSeconds(5))
            .execute(requestFor(repo, outputDir, List.of(), policyPath, null));

    assertThat(outcome.exitCode()).isEqualTo(ExitCode.CONFIGURATION_ERROR.code());
    assertThat(outcome.scanResult()).isNull();
    assertThat(outcome.diagnosticMessage()).contains("Invalid policy");
  }

  @Test
  void aRepositoryShallowerThanThePolicysMinimumFailsWithInsufficientHistory(
      @TempDir Path repo, @TempDir Path outputDir) throws IOException {
    RepositoryHistoryProvider historyProvider = mock(RepositoryHistoryProvider.class);
    when(historyProvider.inspect(repo))
        .thenReturn(new RepositoryInfo(true, true, false, 1, "abc123", "main"));
    Path policyPath = outputDir.resolve("policy.yml");
    Files.writeString(policyPath, "version: \"1.0\"\nanalysis:\n  minimumHistoryDepth: FULL\n");

    ScanOutcome outcome =
        newUseCase(historyProvider, Duration.ofSeconds(5))
            .execute(requestFor(repo, outputDir, List.of(), policyPath, null));

    assertThat(outcome.exitCode()).isEqualTo(ExitCode.INSUFFICIENT_HISTORY.code());
    assertThat(outcome.scanResult()).isNull();
  }

  @Test
  void aViolatedRuleFailsTheScanWhenABaselineExists(@TempDir Path repo, @TempDir Path outputDir)
      throws IOException {
    RepositoryHistoryProvider historyProvider = mock(RepositoryHistoryProvider.class);
    when(historyProvider.inspect(repo)).thenReturn(GIT_REPO_INFO);
    Path policyPath = outputDir.resolve("policy.yml");
    Files.writeString(
        policyPath,
        "version: \"1.0\"\npolicy:\n  main:\n    rules:\n      - id: no-critical\n        severity:"
            + " CRITICAL\n        maxCount: 0\n");
    Path baselinePath = writeEmptyBaseline(outputDir);
    AnalysisEngine engine =
        engineReturning(EngineResult.ok(List.of(finding("f-1", Severity.CRITICAL)), List.of(), 5));

    ScanOutcome outcome =
        newUseCase(historyProvider, Duration.ofSeconds(5))
            .execute(requestFor(repo, outputDir, List.of(engine), policyPath, baselinePath));

    assertThat(outcome.exitCode()).isEqualTo(ExitCode.POLICY_VIOLATED.code());
    assertThat(outcome.scanResult().policy().status())
        .isEqualTo(com.debthunter.domain.PolicyStatus.FAILED);
    assertThat(outcome.scanResult().policy().reasons()).hasSize(1);
  }

  @Test
  void aViolatedRuleWithNoBaselineObservesRatherThanFails(
      @TempDir Path repo, @TempDir Path outputDir) throws IOException {
    RepositoryHistoryProvider historyProvider = mock(RepositoryHistoryProvider.class);
    when(historyProvider.inspect(repo)).thenReturn(GIT_REPO_INFO);
    Path policyPath = outputDir.resolve("policy.yml");
    Files.writeString(
        policyPath,
        "version: \"1.0\"\npolicy:\n  main:\n    rules:\n      - id: no-critical\n        severity:"
            + " CRITICAL\n        maxCount: 0\n");
    AnalysisEngine engine =
        engineReturning(EngineResult.ok(List.of(finding("f-1", Severity.CRITICAL)), List.of(), 5));

    ScanOutcome outcome =
        newUseCase(historyProvider, Duration.ofSeconds(5))
            .execute(requestFor(repo, outputDir, List.of(engine), policyPath, null));

    assertThat(outcome.exitCode()).isEqualTo(ExitCode.POLICY_SATISFIED.code());
    assertThat(outcome.scanResult().policy().status())
        .isEqualTo(com.debthunter.domain.PolicyStatus.WOULD_FAIL);
    assertThat(outcome.scanResult().policy().reasons()).hasSize(1);
    assertThat(outcome.scanResult().run().baselineProvenance()).isEqualTo("NONE");
  }

  private Path writeEmptyBaseline(Path dir) {
    com.debthunter.domain.AnalysisRun run =
        com.debthunter.domain.AnalysisRun.builder()
            .id("baseline-run")
            .toolVersion("0.1.0-test")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("repo")
            .commit("abc123")
            .historyDepth(com.debthunter.domain.HistoryDepth.FULL)
            .build();
    com.debthunter.domain.ScanResult baseline =
        new com.debthunter.domain.ScanResult(
            run,
            List.of(),
            java.util.Map.of(),
            com.debthunter.domain.PolicyResult.passed("unversioned"));
    return new BaselineWriter().write(baseline, dir);
  }

  private Finding finding(String id) {
    return finding(id, Severity.LOW);
  }

  private Finding finding(String id, Severity severity) {
    return Finding.builder()
        .id(id)
        .ruleId("rule")
        .category(Category.HOTSPOT)
        .severity(severity)
        .path("Foo.java")
        .message("msg")
        .fingerprint("fp-" + id)
        .build();
  }
}
