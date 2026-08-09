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
 * AC-26: ten fingerprint-identity scenarios, run end-to-end through the real {@link CodeMaatEngine}
 * against real fixture repositories — reformats, a simple rename, a rename chain, a rename with a
 * directory move, a rename combined with a reformat, a line addition, a line deletion, a method
 * move into a new file, a file split, and distinct rules at one location.
 */
@Tag("integration")
class AC26_FixtureSuiteTest {

  private final Fingerprinter fingerprinter = new Fingerprinter();
  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void scenario1_reformatIsStable(@TempDir Path scriptsDir) {
    fixture =
        FixtureRepoBuilder.init()
            .hotspot("Foo.java", 5)
            .commitFile("Foo.java", "class Foo {\n\n}\n", "reformat");

    assertStable(scriptsDir, "Foo.java", "Foo.java");
  }

  @Test
  void scenario2_simpleRenameIsStable(@TempDir Path scriptsDir) {
    fixture =
        FixtureRepoBuilder.init()
            .hotspot("Foo.java", 5)
            .renameFile("Foo.java", "Bar.java", "rename")
            .hotspot("Bar.java", 3);

    assertStable(scriptsDir, "Bar.java", "Foo.java");
  }

  @Test
  void scenario3_renameChainIsStable(@TempDir Path scriptsDir) {
    fixture =
        FixtureRepoBuilder.init()
            .hotspot("Foo.java", 5)
            .renameFile("Foo.java", "Bar.java", "rename 1")
            .renameFile("Bar.java", "Baz.java", "rename 2")
            .hotspot("Baz.java", 3);

    assertStable(scriptsDir, "Baz.java", "Foo.java");
  }

  @Test
  void scenario4_renameWithDirectoryMoveIsStable(@TempDir Path scriptsDir) {
    fixture =
        FixtureRepoBuilder.init()
            .hotspot("Foo.java", 5)
            .renameFile("Foo.java", "src/main/Foo.java", "move into src/main")
            .hotspot("src/main/Foo.java", 3);

    assertStable(scriptsDir, "src/main/Foo.java", "Foo.java");
  }

  @Test
  void scenario5_lineAdditionIsStable(@TempDir Path scriptsDir) {
    fixture =
        FixtureRepoBuilder.init()
            .hotspot("Foo.java", 5)
            .commitFile("Foo.java", "class Foo {}\n// a new trailing comment\n", "add a line");

    assertStable(scriptsDir, "Foo.java", "Foo.java");
  }

  @Test
  void scenario6_lineDeletionIsStable(@TempDir Path scriptsDir) {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo {}\n// line one\n// line two\n", "add Foo")
            .hotspot("Foo.java", 4)
            .commitFile("Foo.java", "class Foo {}\n", "remove comment lines");

    assertStable(scriptsDir, "Foo.java", "Foo.java");
  }

  @Test
  void scenario7_renameCombinedWithReformatIsStable(@TempDir Path scriptsDir) {
    fixture =
        FixtureRepoBuilder.init()
            .hotspot("Foo.java", 5)
            .renameFile("Foo.java", "Bar.java", "rename")
            .commitFile("Bar.java", "class Bar {\n\n}\n", "reformat after rename")
            .hotspot("Bar.java", 2);

    assertStable(scriptsDir, "Bar.java", "Foo.java");
  }

  @Test
  void scenario8_methodMoveIntoANewFileChangesIdentity(@TempDir Path scriptsDir) {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo { void a() {} }", "add Foo")
            .commitFile("Foo.java", "class Foo { void a() {} void b() {} }", "touch Foo")
            .commitFile("Foo.java", "class Foo { void a() {} }", "remove b()")
            .deleteFile("Foo.java", "remove Foo")
            .commitFile("Bar.java", "class Bar { void b() {} }", "add Bar with moved method")
            .commitFile("Bar.java", "class Bar { void b() {} void c() {} }", "touch Bar")
            .commitFile("Bar.java", "class Bar { void b() {} }", "touch Bar again");

    assertUnstable(scriptsDir, "Bar.java", "Foo.java");
  }

  @Test
  void scenario9_fileSplitGivesEachHalfAFreshAndDistinctIdentity(@TempDir Path scriptsDir) {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile(
                "Combined.java", "class Combined { void a() {} void b() {} }", "add Combined")
            .commitFile(
                "Combined.java", "class Combined { void a() {} void b() {} void c() {} }", "touch")
            .commitFile(
                "Combined.java", "class Combined { void a() {} void b() {} }", "touch again")
            .deleteFile("Combined.java", "split Combined")
            .commitFile("First.java", "class First { void a() {} }", "add First")
            .commitFile("First.java", "class First { void a() {} void x() {} }", "touch First")
            .commitFile("First.java", "class First { void a() {} }", "touch First again")
            .commitFile("Second.java", "class Second { void b() {} }", "add Second")
            .commitFile("Second.java", "class Second { void b() {} void y() {} }", "touch Second")
            .commitFile("Second.java", "class Second { void b() {} }", "touch Second again");

    Path fakeExecutable = CodeMaatTestSupport.revisionCountingFakeCodeMaat(scriptsDir);
    EngineResult result = analyse(new CodeMaatEngine(fakeExecutable));

    Finding first = churnFindingFor(result, "First.java");
    Finding second = churnFindingFor(result, "Second.java");
    String combinedFingerprintWouldHaveBeen =
        fingerprinter.fingerprint("codemaat.churn", "Combined.java", "", "");

    assertThat(first.fingerprint()).isNotEqualTo(combinedFingerprintWouldHaveBeen);
    assertThat(second.fingerprint()).isNotEqualTo(combinedFingerprintWouldHaveBeen);
    assertThat(first.fingerprint()).isNotEqualTo(second.fingerprint());
  }

  @Test
  void scenario10_distinctRulesAtTheSamePathProduceDistinctFingerprints(@TempDir Path scriptsDir) {
    fixture =
        FixtureRepoBuilder.init().hotspot("Foo.java", CodeMaatFindingMapper.HOTSPOT_MIN_REVISIONS);

    Path fakeExecutable = CodeMaatTestSupport.revisionCountingFakeCodeMaat(scriptsDir);
    EngineResult result = analyse(new CodeMaatEngine(fakeExecutable));

    Finding churn = churnFindingFor(result, "Foo.java");
    Finding hotspot =
        result.findings().stream()
            .filter(f -> f.category() == Category.HOTSPOT)
            .findFirst()
            .orElseThrow();

    assertThat(churn.fingerprint()).isNotEqualTo(hotspot.fingerprint());
  }

  private void assertStable(Path scriptsDir, String currentPath, String originalPath) {
    Path fakeExecutable = CodeMaatTestSupport.revisionCountingFakeCodeMaat(scriptsDir);
    EngineResult result = analyse(new CodeMaatEngine(fakeExecutable));

    Finding churn = churnFindingFor(result, currentPath);
    String expected = fingerprinter.fingerprint("codemaat.churn", originalPath, "", "");
    assertThat(churn.fingerprint()).isEqualTo(expected);
  }

  private void assertUnstable(Path scriptsDir, String currentPath, String originalPath) {
    Path fakeExecutable = CodeMaatTestSupport.revisionCountingFakeCodeMaat(scriptsDir);
    EngineResult result = analyse(new CodeMaatEngine(fakeExecutable));

    Finding churn = churnFindingFor(result, currentPath);
    String wouldHaveBeen = fingerprinter.fingerprint("codemaat.churn", originalPath, "", "");
    assertThat(churn.fingerprint()).isNotEqualTo(wouldHaveBeen);
  }

  private EngineResult analyse(CodeMaatEngine engine) {
    AnalysisRequest request =
        new AnalysisRequest(
            fixture.path(), null, AnalysisMode.FULL, null, Map.of(), Duration.ofSeconds(10), 0);
    return engine.analyse(request, ProgressSink.NO_OP);
  }

  private Finding churnFindingFor(EngineResult result, String path) {
    return result.findings().stream()
        .filter(f -> f.category() == Category.CHURN && f.path().equals(path))
        .findFirst()
        .orElseThrow();
  }
}
