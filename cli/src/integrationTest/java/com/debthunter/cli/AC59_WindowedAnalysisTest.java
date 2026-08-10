package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.domain.Category;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.engine.spi.AnalysisEngine;
import com.debthunter.engine.spi.AnalysisRequest;
import com.debthunter.engine.spi.CostClass;
import com.debthunter.engine.spi.EngineDescriptor;
import com.debthunter.engine.spi.EngineResult;
import com.debthunter.engine.spi.RepositoryContext;
import com.debthunter.output.JsonReporter;
import com.debthunter.output.MarkdownReporter;
import com.debthunter.output.MetricsReporter;
import com.debthunter.output.SarifReporter;
import com.debthunter.policy.BaselineComparator;
import com.debthunter.policy.BaselineResolver;
import com.debthunter.policy.PolicyBundleParser;
import com.debthunter.policy.PolicyEvaluator;
import com.debthunter.repository.GitHistoryProvider;
import com.debthunter.testkit.FixtureRepoBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * AC-59: {@code --history-window-since} bounds the history engines actually see (passed through via
 * {@link AnalysisRequest#historyWindowSince()}), and is recorded as {@link HistoryDepth#PARTIAL} —
 * a deliberate, self-imposed restriction, distinct from an environmental one like a shallow clone,
 * but with the same downstream effect on confidence and gating.
 */
@Tag("integration")
class AC59_WindowedAnalysisTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac59_theWindowIsPassedToEnginesAndRecordedAsPartialHistoryDepth(@TempDir Path outputDir)
      throws Exception {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    AnalysisEngine engine = mock(AnalysisEngine.class);
    when(engine.descriptor())
        .thenReturn(new EngineDescriptor("fake", "1.0", List.of(Category.HOTSPOT), CostClass.LOW));
    when(engine.supports(any(RepositoryContext.class))).thenReturn(true);
    when(engine.analyse(any(), any())).thenReturn(EngineResult.ok(List.of(), List.of(), 5));

    Instant since = Instant.parse("2020-01-01T00:00:00Z");
    ScanCommand command =
        new ScanCommand(
            fixture.path(),
            outputDir,
            null,
            null,
            defaultScanUseCase(),
            List.of(engine),
            new com.debthunter.application.publish.PublishUseCase(
                mock(com.debthunter.integration.ResultUploader.class)),
            null,
            false,
            java.util.Map.of(),
            null,
            since);

    int exitCode = command.call();

    assertThat(exitCode).isIn(0, 1);

    ArgumentCaptor<AnalysisRequest> requestCaptor = ArgumentCaptor.forClass(AnalysisRequest.class);
    verify(engine).analyse(requestCaptor.capture(), any());
    assertThat(requestCaptor.getValue().historyWindowSince()).isEqualTo(since);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode json = mapper.readTree(outputDir.resolve(JsonReporter.FILE_NAME).toFile());
    assertThat(json.get("run").get("historyDepth").asText()).isEqualTo("PARTIAL");
  }

  private ScanUseCase defaultScanUseCase() {
    return new ScanUseCase(
        new GitHistoryProvider(),
        new JsonReporter(),
        new MarkdownReporter(),
        new MetricsReporter(),
        new SarifReporter(),
        new BaselineResolver(),
        new BaselineComparator(),
        new PolicyBundleParser(),
        new PolicyEvaluator(),
        new HistoryDepthEnforcer(),
        "0.1.0-test");
  }
}
