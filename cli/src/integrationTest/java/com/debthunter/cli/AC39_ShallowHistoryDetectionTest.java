package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import com.debthunter.engine.spi.AnalysisEngine;
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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-39: a repository that's a genuine {@code git clone --depth 1} (not a synthesised marker) is
 * detected and reported as shallow in the scan's run metadata, end to end through the real CLI
 * path.
 */
@Tag("integration")
class AC39_ShallowHistoryDetectionTest {

  private FixtureRepoBuilder source;
  private FixtureRepoBuilder clone;

  @AfterEach
  void cleanup() {
    if (clone != null) {
      clone.close();
    }
    if (source != null) {
      source.close();
    }
  }

  @Test
  void ac39_aRealShallowCloneIsReportedAsShallowInTheScanOutput(
      @TempDir Path outputDir, @TempDir Path cloneDir) throws Exception {
    source =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo {}", "add Foo")
            .commitFile("Foo.java", "class Foo { void a() {} }", "touch Foo");
    clone = source.cloneShallow(cloneDir.resolve("clone"), 1);

    AnalysisEngine engine = mock(AnalysisEngine.class);
    when(engine.descriptor())
        .thenReturn(new EngineDescriptor("fake", "1.0", List.of(Category.HOTSPOT), CostClass.LOW));
    when(engine.supports(any(RepositoryContext.class))).thenReturn(true);
    when(engine.analyse(any(), any()))
        .thenReturn(
            EngineResult.ok(
                List.of(
                    Finding.builder()
                        .id("f-1")
                        .ruleId("hotspot.rule")
                        .category(Category.HOTSPOT)
                        .severity(Severity.HIGH)
                        .confidence(0.8)
                        .path("Foo.java")
                        .message("Foo.java changes often")
                        .fingerprint("fp-f-1")
                        .build()),
                List.of(),
                5));

    ScanUseCase scanUseCase =
        new ScanUseCase(
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
    ScanCommand command =
        new ScanCommand(clone.path(), outputDir, null, null, scanUseCase, List.of(engine));

    int exitCode = command.call();

    assertThat(exitCode).isIn(0, 1);
    JsonNode root = new ObjectMapper().readTree(outputDir.resolve(JsonReporter.FILE_NAME).toFile());
    assertThat(root.get("run").get("historyDepth").asText()).isEqualTo("SHALLOW");
    // Confidence is reduced (0.8 * 0.5) because the history-dependent finding was produced from an
    // incomplete history.
    assertThat(root.get("findings").get(0).get("confidence").asDouble()).isEqualTo(0.4);
  }
}
