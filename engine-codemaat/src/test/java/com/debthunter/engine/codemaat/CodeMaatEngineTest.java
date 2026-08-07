package com.debthunter.engine.codemaat;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.EngineHealth;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.engine.spi.AnalysisMode;
import com.debthunter.engine.spi.AnalysisRequest;
import com.debthunter.engine.spi.EngineResult;
import com.debthunter.engine.spi.ProgressSink;
import com.debthunter.engine.spi.RepositoryContext;
import com.debthunter.engine.spi.VcsType;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeMaatEngineTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  private AnalysisRequest requestFor(Path repoPath) {
    return new AnalysisRequest(
        repoPath, null, AnalysisMode.FULL, null, Map.of(), Duration.ofSeconds(10), 0);
  }

  @Test
  void descriptorAdvertisesAllFourCategories() {
    CodeMaatEngine engine = new CodeMaatEngine(Path.of("/nonexistent"));

    assertThat(engine.descriptor().id()).isEqualTo("code-maat");
    assertThat(engine.descriptor().categories())
        .containsExactlyInAnyOrder(
            Category.HOTSPOT,
            Category.TEMPORAL_COUPLING,
            Category.CHURN,
            Category.KNOWLEDGE_CONCENTRATION);
  }

  @Test
  void supportsGitRepositoriesWithNonShallowHistory() {
    CodeMaatEngine engine = new CodeMaatEngine(Path.of("/nonexistent"));

    assertThat(engine.supports(context(VcsType.GIT, HistoryDepth.FULL))).isTrue();
    assertThat(engine.supports(context(VcsType.GIT, HistoryDepth.SHALLOW))).isFalse();
    assertThat(engine.supports(context(VcsType.NONE, HistoryDepth.FULL))).isFalse();
  }

  private RepositoryContext context(VcsType vcsType, HistoryDepth historyDepth) {
    return new RepositoryContext(Path.of("."), List.of(), vcsType, historyDepth);
  }

  @Test
  void successfulSubprocessInvocationProducesOkResultWithFindings(@TempDir Path scriptsDir) {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");
    Path fakeExecutable =
        CodeMaatTestSupport.fakeCodeMaat(
            scriptsDir,
            Map.of(
                "revisions", "entity,n-revs\nFoo.java,15\n",
                "coupling", "entity,coupled,degree,average-revs\n",
                "age", "entity,age-months\nFoo.java,1\n",
                "authors", "entity,n-authors,n-revs\nFoo.java,1,15\n"));

    CodeMaatEngine engine = new CodeMaatEngine(fakeExecutable);
    EngineResult result = engine.analyse(requestFor(fixture.path()), ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.OK);
    assertThat(result.findings()).isNotEmpty();
    assertThat(result.findings())
        .anySatisfy(f -> assertThat(f.category()).isEqualTo(Category.HOTSPOT));
    assertThat(result.findings())
        .anySatisfy(f -> assertThat(f.category()).isEqualTo(Category.KNOWLEDGE_CONCENTRATION));
    assertThat(result.metrics()).isNotEmpty();
    assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void allAnalysesFailingProducesFailedResult(@TempDir Path scriptsDir) {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");
    Path failingExecutable = CodeMaatTestSupport.failingCodeMaat(scriptsDir, 1);

    CodeMaatEngine engine = new CodeMaatEngine(failingExecutable);
    EngineResult result = engine.analyse(requestFor(fixture.path()), ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.FAILED);
    assertThat(result.reason()).contains("exited with code 1");
    assertThat(result.findings()).isEmpty();
  }

  @Test
  void oneAnalysisWithUnparseableOutputProducesDegradedResult(@TempDir Path scriptsDir) {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");
    Path fakeExecutable =
        CodeMaatTestSupport.fakeCodeMaat(
            scriptsDir,
            Map.of(
                "revisions", "entity,n-revs\nFoo.java,15\n",
                "coupling", "unexpected,header,shape\n",
                "age", "entity,age-months\nFoo.java,1\n",
                "authors", "entity,n-authors,n-revs\nFoo.java,1,15\n"));

    CodeMaatEngine engine = new CodeMaatEngine(fakeExecutable);
    EngineResult result = engine.analyse(requestFor(fixture.path()), ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.DEGRADED);
    assertThat(result.reason()).contains("coupling");
    // The other three analyses still succeeded, so their findings/metrics are still present.
    assertThat(result.findings()).isNotEmpty();
  }
}
