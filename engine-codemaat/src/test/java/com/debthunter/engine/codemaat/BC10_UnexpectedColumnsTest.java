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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * BC-10: a Code Maat CSV whose columns no longer match what this version's parser expects (e.g. a
 * future Code Maat release changing its output shape) degrades that analysis instead of silently
 * misreading it or crashing the whole engine.
 */
class BC10_UnexpectedColumnsTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void bc10_unexpectedColumnsDegradeThatAnalysisWithAParseErrorReason(@TempDir Path scriptsDir) {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");
    Path fakeExecutable =
        CodeMaatTestSupport.fakeCodeMaat(
            scriptsDir,
            Map.of(
                // revisions header changed shape, e.g. a hypothetical future Code Maat version.
                "revisions", "path,revision-count\nFoo.java,15\n",
                "coupling", "entity,coupled,degree,average-revs\n",
                "age", "entity,age-months\nFoo.java,1\n",
                "authors", "entity,n-authors,n-revs\nFoo.java,1,15\n"));

    CodeMaatEngine engine = new CodeMaatEngine(fakeExecutable);
    AnalysisRequest request =
        new AnalysisRequest(
            fixture.path(), null, AnalysisMode.FULL, null, Map.of(), Duration.ofSeconds(5), 0);

    EngineResult result = engine.analyse(request, ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.DEGRADED);
    assertThat(result.reason()).contains("revisions");
    // authors ran fine and still contributed a finding despite revisions degrading.
    assertThat(result.findings()).isNotEmpty();
  }
}
