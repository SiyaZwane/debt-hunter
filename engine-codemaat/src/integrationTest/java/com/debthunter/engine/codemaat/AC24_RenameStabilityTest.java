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
 * AC-24: renaming a file must not change its findings' fingerprints — the finding under the new
 * path must carry the same fingerprint the original path would have, resolved via {@code git log
 * --follow}.
 */
@Tag("integration")
class AC24_RenameStabilityTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac24_renamingAFileDoesNotChangeItsChurnFindingFingerprint(@TempDir Path scriptsDir) {
    fixture =
        FixtureRepoBuilder.init()
            .hotspot("Foo.java", 5)
            .renameFile("Foo.java", "Bar.java", "rename Foo to Bar")
            .hotspot("Bar.java", 3);

    Path fakeExecutable = CodeMaatTestSupport.revisionCountingFakeCodeMaat(scriptsDir);
    CodeMaatEngine engine = new CodeMaatEngine(fakeExecutable);
    AnalysisRequest request =
        new AnalysisRequest(
            fixture.path(), null, AnalysisMode.FULL, null, Map.of(), Duration.ofSeconds(10), 0);

    EngineResult result = engine.analyse(request, ProgressSink.NO_OP);

    Finding churn = churnFindingFor(result, "Bar.java");
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
