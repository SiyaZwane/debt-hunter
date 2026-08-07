package com.debthunter.engine.codemaat;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.EngineHealth;
import com.debthunter.engine.spi.AnalysisMode;
import com.debthunter.engine.spi.AnalysisRequest;
import com.debthunter.engine.spi.EngineResult;
import com.debthunter.engine.spi.ProgressSink;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * BC-11: a repository with a single commit has no meaningful churn, coupling, or knowledge-
 * concentration signal yet — every file has exactly one revision by one author, and no pair of
 * files has ever changed together — so the engine must complete with status OK and zero findings,
 * not treat the thin history as a failure.
 */
@Tag("integration")
class BC11_SingleCommitRepoTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void bc11_singleCommitRepoProducesOkResultWithNoFindings(@TempDir Path scriptsDir) {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    Path fakeExecutable =
        CodeMaatTestSupport.fakeCodeMaat(
            scriptsDir,
            Map.of(
                "revisions", "entity,n-revs\nFoo.java,1\n",
                "coupling", "entity,coupled,degree,average-revs\n",
                "age", "entity,age-months\nFoo.java,0\n",
                "authors", "entity,n-authors,n-revs\nFoo.java,1,1\n"));

    CodeMaatEngine engine = new CodeMaatEngine(fakeExecutable);
    AnalysisRequest request =
        new AnalysisRequest(
            fixture.path(), null, AnalysisMode.FULL, null, Map.of(), Duration.ofSeconds(5), 0);

    EngineResult result = engine.analyse(request, ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.OK);
    assertThat(result.findings()).isEmpty();
    assertThat(result.metrics()).hasSize(1);
  }
}
