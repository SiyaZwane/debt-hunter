package com.debthunter.engine.codemaat;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.EngineHealth;
import com.debthunter.engine.spi.AnalysisMode;
import com.debthunter.engine.spi.AnalysisRequest;
import com.debthunter.engine.spi.EngineResult;
import com.debthunter.engine.spi.ProgressSink;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-12: the full golden-file suite, exercised end-to-end through {@link CodeMaatEngine} (not just
 * the parser/mapper layer that {@link CodeMaatGoldenFileTest} covers), using a fake executable that
 * replays the same golden CSVs Code Maat would have produced.
 */
class AC12_GoldenFileRegressionTest {

  private static final Path GOLDEN_DIR = Path.of("src/test/resources/golden");

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac12_engineEndToEndReproducesGoldenFindingsAndMetrics(@TempDir Path scriptsDir)
      throws Exception {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    Path fakeExecutable =
        CodeMaatTestSupport.fakeCodeMaat(
            scriptsDir,
            Map.of(
                "revisions", readGolden("revisions.csv"),
                "coupling", readGolden("coupling.csv"),
                "age", readGolden("age.csv"),
                "authors", readGolden("authors.csv")));

    CodeMaatEngine engine = new CodeMaatEngine(fakeExecutable);
    AnalysisRequest request =
        new AnalysisRequest(
            fixture.path(), null, AnalysisMode.FULL, null, Map.of(), Duration.ofSeconds(10), 0);

    EngineResult result = engine.analyse(request, ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.OK);
    // revisions.csv: PaymentProcessor.java (churn + hotspot) + Utils.java (churn) = 3
    // coupling.csv: PaymentProcessor<->PaymentValidator = 1
    // authors.csv: PaymentProcessor.java = 1
    assertThat(result.findings()).hasSize(5);
    assertThat(result.findings()).filteredOn(f -> f.category() == Category.HOTSPOT).hasSize(1);
    assertThat(result.findings()).filteredOn(f -> f.category() == Category.CHURN).hasSize(2);
    assertThat(result.findings())
        .filteredOn(f -> f.category() == Category.TEMPORAL_COUPLING)
        .hasSize(1);
    assertThat(result.findings())
        .filteredOn(f -> f.category() == Category.KNOWLEDGE_CONCENTRATION)
        .hasSize(1);
    // age.csv: 3 files, each contributing one metric.
    assertThat(result.metrics()).hasSize(3);
  }

  private String readGolden(String fileName) throws Exception {
    return Files.readString(GOLDEN_DIR.resolve(fileName));
  }
}
