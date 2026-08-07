package com.debthunter.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.EngineHealth;
import com.debthunter.domain.HistoryDepth;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisEngineContractTest {

  private static final class FakeEngine implements AnalysisEngine {
    @Override
    public EngineDescriptor descriptor() {
      return new EngineDescriptor("fake", "1.0", List.of(Category.CUSTOM), CostClass.LOW);
    }

    @Override
    public boolean supports(RepositoryContext context) {
      return context.vcsType() == VcsType.GIT;
    }

    @Override
    public EngineResult analyse(AnalysisRequest request, ProgressSink sink) {
      sink.report("done", 1.0);
      return EngineResult.ok(List.of(), List.of(), 5);
    }
  }

  @Test
  void ac01_descriptorIsNeverNull() {
    AnalysisEngine engine = new FakeEngine();

    assertThat(engine.descriptor()).isNotNull();
    assertThat(engine.descriptor().id()).isEqualTo("fake");
  }

  @Test
  void supportsReflectsRepositoryContext() {
    AnalysisEngine engine = new FakeEngine();
    RepositoryContext gitContext =
        new RepositoryContext(Path.of("."), List.of(), VcsType.GIT, HistoryDepth.FULL);
    RepositoryContext noneContext =
        new RepositoryContext(Path.of("."), List.of(), VcsType.NONE, HistoryDepth.FULL);

    assertThat(engine.supports(gitContext)).isTrue();
    assertThat(engine.supports(noneContext)).isFalse();
  }

  @Test
  void analyseNeverReturnsNull() {
    AnalysisEngine engine = new FakeEngine();
    AnalysisRequest request =
        new AnalysisRequest(
            Path.of("."), null, AnalysisMode.FULL, null, null, Duration.ofMinutes(1), 0);

    EngineResult result = engine.analyse(request, ProgressSink.NO_OP);

    assertThat(result).isNotNull();
  }

  @Test
  void okResultHasNullReasonAndEmptyIsNotEnforcedButDefaultsAreImmutable() {
    EngineResult result = EngineResult.ok(null, null, 10);

    assertThat(result.status()).isEqualTo(EngineHealth.OK);
    assertThat(result.reason()).isNull();
    assertThat(result.findings()).isEmpty();
    assertThat(result.metrics()).isEmpty();
  }

  @Test
  void failedResultHasReasonAndNoFindingsOrMetrics() {
    EngineResult result = EngineResult.failed("binary not found", 3);

    assertThat(result.status()).isEqualTo(EngineHealth.FAILED);
    assertThat(result.reason()).isEqualTo("binary not found");
    assertThat(result.findings()).isEmpty();
    assertThat(result.metrics()).isEmpty();
  }

  @Test
  void degradedResultCanCarryPartialFindingsAndAReason() {
    EngineResult result = EngineResult.degraded(List.of(), List.of(), "partial parse", 7);

    assertThat(result.status()).isEqualTo(EngineHealth.DEGRADED);
    assertThat(result.reason()).isEqualTo("partial parse");
  }

  @Test
  void noOpProgressSinkAcceptsReportsWithoutThrowing() {
    ProgressSink.NO_OP.report("anything", 0.5);
  }
}
