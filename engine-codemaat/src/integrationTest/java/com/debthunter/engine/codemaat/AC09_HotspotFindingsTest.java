package com.debthunter.engine.codemaat;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.EngineHealth;
import com.debthunter.domain.Finding;
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
 * AC-09: a file that's genuinely a known hotspot (touched far more than any other file in the
 * repository) produces a correct hotspot finding, and a barely-touched file does not. Exercises the
 * real {@code CodeMaatLogWriter} (JGit-based, no native git) feeding a fake executable that
 * computes revision counts from that real log, rather than replaying canned CSV — the closest this
 * suite gets to a genuine end-to-end run without a real Code Maat installation.
 */
@Tag("integration")
class AC09_HotspotFindingsTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac09_frequentlyTouchedFileProducesHotspotFindingWhileRarelyTouchedFileDoesNot(
      @TempDir Path scriptsDir) {
    fixture =
        FixtureRepoBuilder.init()
            .hotspot("Foo.java", 12)
            .commitFile("Bar.java", "class Bar {}", "add Bar");

    Path fakeExecutable = CodeMaatTestSupport.revisionCountingFakeCodeMaat(scriptsDir);
    CodeMaatEngine engine = new CodeMaatEngine(fakeExecutable);
    AnalysisRequest request =
        new AnalysisRequest(
            fixture.path(), null, AnalysisMode.FULL, null, Map.of(), Duration.ofSeconds(10), 0);

    EngineResult result = engine.analyse(request, ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.OK);
    assertThat(result.findings())
        .filteredOn(f -> f.category() == Category.HOTSPOT)
        .singleElement()
        .satisfies(finding -> assertThat(finding.path()).isEqualTo("Foo.java"));
    assertThat(result.findings())
        .extracting(Finding::path)
        .as("Bar.java was only committed once, well below the churn threshold")
        .doesNotContain("Bar.java");
  }
}
