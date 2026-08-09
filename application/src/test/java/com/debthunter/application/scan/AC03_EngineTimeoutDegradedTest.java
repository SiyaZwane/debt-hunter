package com.debthunter.application.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.debthunter.domain.EngineHealth;
import com.debthunter.engine.spi.AnalysisEngine;
import com.debthunter.engine.spi.AnalysisMode;
import com.debthunter.engine.spi.CostClass;
import com.debthunter.engine.spi.EngineDescriptor;
import com.debthunter.engine.spi.EngineResult;
import com.debthunter.engine.spi.RepositoryContext;
import com.debthunter.output.JsonReporter;
import com.debthunter.output.MarkdownReporter;
import com.debthunter.output.MetricsReporter;
import com.debthunter.output.SarifReporter;
import com.debthunter.policy.BaselineComparator;
import com.debthunter.policy.BaselineResolver;
import com.debthunter.repository.RepositoryHistoryProvider;
import com.debthunter.repository.RepositoryInfo;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AC-03: one engine timing out degrades the run but lets the scan complete. */
class AC03_EngineTimeoutDegradedTest {

  @Test
  void ac03_engineTimeoutDegradedRunCompletesWithOtherFindingsPresent(
      @TempDir java.nio.file.Path repo, @TempDir java.nio.file.Path outputDir) {
    RepositoryHistoryProvider historyProvider = mock(RepositoryHistoryProvider.class);
    when(historyProvider.inspect(repo))
        .thenReturn(new RepositoryInfo(true, false, false, 1, "abc123", "main"));

    AnalysisEngine timingOutEngine = mock(AnalysisEngine.class);
    when(timingOutEngine.descriptor())
        .thenReturn(new EngineDescriptor("slow", "1.0", List.of(), CostClass.HIGH));
    when(timingOutEngine.supports(any(RepositoryContext.class))).thenReturn(true);
    when(timingOutEngine.analyse(any(), any()))
        .thenAnswer(
            invocation -> {
              Thread.sleep(2000);
              return EngineResult.ok(List.of(), List.of(), 2000);
            });

    AnalysisEngine fastEngine = mock(AnalysisEngine.class);
    when(fastEngine.descriptor())
        .thenReturn(new EngineDescriptor("fast", "1.0", List.of(), CostClass.LOW));
    when(fastEngine.supports(any(RepositoryContext.class))).thenReturn(true);
    when(fastEngine.analyse(any(), any()))
        .thenReturn(
            EngineResult.ok(
                List.of(
                    com.debthunter.domain.Finding.builder()
                        .id("f-1")
                        .ruleId("rule")
                        .category(com.debthunter.domain.Category.HOTSPOT)
                        .severity(com.debthunter.domain.Severity.LOW)
                        .path("Foo.java")
                        .message("msg")
                        .fingerprint("fp")
                        .build()),
                List.of(),
                5));

    ScanUseCase scanUseCase =
        new ScanUseCase(
            historyProvider,
            new JsonReporter(),
            new MarkdownReporter(),
            new MetricsReporter(),
            new SarifReporter(),
            new BaselineResolver(),
            new BaselineComparator(),
            "0.1.0-test",
            Duration.ofMillis(150),
            Clock.systemUTC());

    ScanRequest request =
        new ScanRequest(
            repo,
            outputDir,
            AnalysisMode.FULL,
            null,
            null,
            List.of(timingOutEngine, fastEngine),
            null,
            false,
            null,
            null);

    ScanOutcome outcome = scanUseCase.execute(request);

    assertThat(outcome.scanResult()).isNotNull();
    assertThat(outcome.scanResult().isDegraded()).isTrue();
    assertThat(outcome.scanResult().findings()).hasSize(1);
    assertThat(
            outcome.scanResult().run().engines().stream()
                .filter(status -> status.id().equals("slow"))
                .findFirst()
                .orElseThrow()
                .status())
        .isEqualTo(EngineHealth.FAILED);
  }
}
