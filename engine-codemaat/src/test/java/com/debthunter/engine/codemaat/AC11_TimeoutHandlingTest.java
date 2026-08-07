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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** AC-11: a hanging Code Maat process is forcefully terminated and the scan run still completes. */
class AC11_TimeoutHandlingTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  @Timeout(30)
  void ac11_hangingProcessIsTerminatedAndEngineReturnsFailedPromptly(@TempDir Path scriptsDir) {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");
    Path hangingExecutable = CodeMaatTestSupport.hangingCodeMaat(scriptsDir);

    CodeMaatEngine engine = new CodeMaatEngine(hangingExecutable);
    AnalysisRequest request =
        new AnalysisRequest(
            fixture.path(), null, AnalysisMode.FULL, null, Map.of(), Duration.ofMillis(300), 0);

    long start = System.currentTimeMillis();
    EngineResult result = engine.analyse(request, ProgressSink.NO_OP);
    long elapsed = System.currentTimeMillis() - start;

    // The whole run — all four analysis invocations, each hanging and each killed after the
    // 300ms timeout — must complete in well under the 30s test timeout, proving the engine
    // doesn't just wait for a hung process to exit on its own.
    assertThat(elapsed).isLessThan(20_000);
    assertThat(result.status()).isEqualTo(EngineHealth.FAILED);
    assertThat(result.reason()).contains("timed out");
  }
}
