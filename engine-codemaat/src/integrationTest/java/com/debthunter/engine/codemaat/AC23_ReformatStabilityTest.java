package com.debthunter.engine.codemaat;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Fingerprinter;
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
 * AC-23: reformatting a file (a whitespace-only content change, same path) must not change its
 * findings' fingerprints.
 */
@Tag("integration")
class AC23_ReformatStabilityTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac23_reformattingAFileDoesNotChangeItsChurnFindingFingerprint(@TempDir Path scriptsDir) {
    fixture =
        FixtureRepoBuilder.init()
            .hotspot("Foo.java", 5)
            .commitFile("Foo.java", "class Foo {\n\n}\n", "reformat Foo");

    Path fakeExecutable = CodeMaatTestSupport.revisionCountingFakeCodeMaat(scriptsDir);
    CodeMaatEngine engine = new CodeMaatEngine(fakeExecutable);
    AnalysisRequest request =
        new AnalysisRequest(
            fixture.path(), null, AnalysisMode.FULL, null, Map.of(), Duration.ofSeconds(10), 0);

    EngineResult result = engine.analyse(request, ProgressSink.NO_OP);

    Finding churn = churnFindingFor(result, "Foo.java");
    assertThat(churn.fingerprint())
        .isEqualTo(new Fingerprinter().fingerprint("codemaat.churn", "Foo.java", "", ""));
  }

  private Finding churnFindingFor(EngineResult result, String path) {
    return result.findings().stream()
        .filter(f -> f.category() == Category.CHURN && f.path().equals(path))
        .findFirst()
        .orElseThrow();
  }
}
