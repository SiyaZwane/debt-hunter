package com.debthunter.application.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BC-04: every engine fails, but the scan still completes and reports it, rather than crashing. */
class BC04_AllEnginesFailTest {

  @Test
  void bc04_allEnginesFailingStillProducesACompletedDegradedScan(
      @TempDir Path repo, @TempDir Path outputDir) {
    RepositoryHistoryProvider historyProvider = mock(RepositoryHistoryProvider.class);
    when(historyProvider.inspect(repo))
        .thenReturn(new RepositoryInfo(true, false, false, 1, "abc123", "main"));

    AnalysisEngine engineA = failingEngine("engine-a");
    AnalysisEngine engineB = failingEngine("engine-b");

    ScanUseCase scanUseCase =
        new ScanUseCase(
            historyProvider,
            new JsonReporter(),
            new MarkdownReporter(),
            new MetricsReporter(),
            "0.1.0-test");

    ScanRequest request =
        new ScanRequest(
            repo,
            outputDir,
            AnalysisMode.FULL,
            null,
            null,
            List.of(engineA, engineB),
            null,
            false,
            null);

    ScanOutcome outcome = scanUseCase.execute(request);

    assertThat(outcome.scanResult()).isNotNull();
    assertThat(outcome.scanResult().isDegraded()).isTrue();
    assertThat(outcome.scanResult().findings()).isEmpty();
    assertThat(outcome.scanResult().run().engines()).hasSize(2);
    assertThat(outcome.scanResult().run().engines())
        .allSatisfy(
            status ->
                assertThat(status.status()).isEqualTo(com.debthunter.domain.EngineHealth.FAILED));
    assertThat(outcome.exitCode()).isZero();
  }

  private AnalysisEngine failingEngine(String id) {
    AnalysisEngine engine = mock(AnalysisEngine.class);
    when(engine.descriptor()).thenReturn(new EngineDescriptor(id, "1.0", List.of(), CostClass.LOW));
    when(engine.supports(any(RepositoryContext.class))).thenReturn(true);
    when(engine.analyse(any(), any())).thenReturn(EngineResult.failed("simulated failure", 3));
    return engine;
  }
}
