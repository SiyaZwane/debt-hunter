package com.debthunter.application.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.debthunter.output.JsonReporter;
import com.debthunter.output.MarkdownReporter;
import com.debthunter.output.MetricsReporter;
import com.debthunter.repository.RepositoryHistoryProvider;
import com.debthunter.repository.RepositoryInfo;
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
        "0.1.0-test",
        timeout,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  private ScanRequest requestFor(Path repo, Path outputDir, List<AnalysisEngine> engines) {
    return new ScanRequest(
        repo, outputDir, AnalysisMode.FULL, null, null, engines, null, false, null);
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
    assertThat(outcome.scanResult().findings()).containsExactly(finding);
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

  private Finding finding(String id) {
    return Finding.builder()
        .id(id)
        .ruleId("rule")
        .category(Category.HOTSPOT)
        .severity(Severity.LOW)
        .path("Foo.java")
        .message("msg")
        .fingerprint("fp-" + id)
        .build();
  }
}
