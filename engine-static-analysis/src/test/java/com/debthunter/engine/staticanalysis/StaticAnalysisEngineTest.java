package com.debthunter.engine.staticanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.EngineHealth;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.engine.spi.AnalysisMode;
import com.debthunter.engine.spi.AnalysisRequest;
import com.debthunter.engine.spi.EngineResult;
import com.debthunter.engine.spi.ProgressSink;
import com.debthunter.engine.spi.RepositoryContext;
import com.debthunter.engine.spi.VcsType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticAnalysisEngineTest {

  private final StaticAnalysisEngine engine = new StaticAnalysisEngine();

  @Test
  void descriptorAdvertisesTheStaticAnalysisCategory() {
    var descriptor = engine.descriptor();

    assertThat(descriptor.id()).isEqualTo("static-analysis");
    assertThat(descriptor.categories()).containsExactly(Category.STATIC_ANALYSIS);
  }

  @Test
  void supportsIsTrueRegardlessOfRepositoryContext(@TempDir Path repoPath) {
    assertThat(
            engine.supports(
                new RepositoryContext(repoPath, List.of(), VcsType.NONE, HistoryDepth.SHALLOW)))
        .isTrue();
  }

  @Test
  void aRepositoryWithNoReportFileProducesNoFindings(@TempDir Path repoPath) {
    EngineResult result = engine.analyse(request(repoPath), ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.OK);
    assertThat(result.findings()).isEmpty();
  }

  @Test
  void aPresentReportFileIsAdaptedIntoFindings(@TempDir Path repoPath) throws IOException {
    writeReport(
        repoPath,
        "{\"issues\":[{\"key\":\"AXy1\",\"rule\":\"java:S1192\",\"severity\":\"MAJOR\","
            + "\"component\":\"p:Foo.java\",\"line\":10,\"message\":\"m\"}]}");

    EngineResult result = engine.analyse(request(repoPath), ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.OK);
    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().get(0).path()).isEqualTo("Foo.java");
  }

  @Test
  void malformedReportJsonFailsTheEngineRun(@TempDir Path repoPath) throws IOException {
    writeReport(repoPath, "{not valid json");

    EngineResult result = engine.analyse(request(repoPath), ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.FAILED);
    assertThat(result.reason()).contains(StaticAnalysisEngine.REPORT_FILE_NAME);
  }

  private void writeReport(Path repoPath, String json) throws IOException {
    Files.writeString(
        repoPath.resolve(StaticAnalysisEngine.REPORT_FILE_NAME), json, StandardCharsets.UTF_8);
  }

  private AnalysisRequest request(Path repoPath) {
    return new AnalysisRequest(
        repoPath, null, AnalysisMode.FULL, null, Map.of(), Duration.ofSeconds(30), 0);
  }
}
