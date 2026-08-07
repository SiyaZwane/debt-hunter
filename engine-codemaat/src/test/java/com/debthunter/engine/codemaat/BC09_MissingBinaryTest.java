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

/** BC-09: a missing Code Maat executable fails cleanly, with a diagnosable reason. */
class BC09_MissingBinaryTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void bc09_missingBinaryProducesFailedResultWithClearReason() {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");
    Path missingExecutable = Path.of("/definitely/does/not/exist/code-maat");

    CodeMaatEngine engine = new CodeMaatEngine(missingExecutable);
    AnalysisRequest request =
        new AnalysisRequest(
            fixture.path(), null, AnalysisMode.FULL, null, Map.of(), Duration.ofSeconds(5), 0);

    EngineResult result = engine.analyse(request, ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.FAILED);
    assertThat(result.reason())
        .contains("engine binary not found")
        .contains(missingExecutable.toString());
    assertThat(result.findings()).isEmpty();
  }
}
