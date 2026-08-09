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
 * AC-25: moving a method out of one file and into a brand-new one is a genuine identity change, not
 * a rename — unlike {@link AC24_RenameStabilityTest}'s single-file rename, the old file is deleted
 * in one commit and the new file is added in a later, separate commit, so {@code git log --follow}
 * has no single diff in which to detect a content-similarity rename. The new file's finding must
 * therefore get a fresh fingerprint, not the deleted file's.
 */
@Tag("integration")
class AC25_MethodMoveChangesTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac25_movingContentIntoANewFileChangesTheFingerprint(@TempDir Path scriptsDir) {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo { void a() {} }", "add Foo")
            .commitFile("Foo.java", "class Foo { void a() {} void b() {} }", "touch Foo")
            .commitFile("Foo.java", "class Foo { void a() {} }", "remove b() from Foo")
            .deleteFile("Foo.java", "remove Foo entirely")
            .commitFile("Bar.java", "class Bar { void b() {} }", "add Bar with moved method")
            .commitFile("Bar.java", "class Bar { void b() {} void c() {} }", "touch Bar")
            .commitFile("Bar.java", "class Bar { void b() {} }", "touch Bar again");

    Path fakeExecutable = CodeMaatTestSupport.revisionCountingFakeCodeMaat(scriptsDir);
    CodeMaatEngine engine = new CodeMaatEngine(fakeExecutable);
    AnalysisRequest request =
        new AnalysisRequest(
            fixture.path(), null, AnalysisMode.FULL, null, Map.of(), Duration.ofSeconds(10), 0);

    EngineResult result = engine.analyse(request, ProgressSink.NO_OP);

    Finding barChurn = churnFindingFor(result, "Bar.java");
    String fooFingerprintWouldHaveBeen =
        new Fingerprinter().fingerprint("codemaat.churn", "Foo.java", "", "");

    assertThat(barChurn.fingerprint()).isNotEqualTo(fooFingerprintWouldHaveBeen);
  }

  private Finding churnFindingFor(EngineResult result, String path) {
    return result.findings().stream()
        .filter(f -> f.category() == Category.CHURN && f.path().equals(path))
        .findFirst()
        .orElseThrow();
  }
}
