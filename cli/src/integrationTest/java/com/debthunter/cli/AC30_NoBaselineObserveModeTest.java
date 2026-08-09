package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
 * AC-30: with no explicit baseline and nothing at the pipeline-cache path, a scan still completes
 * normally — {@code baselineProvenance} records {@code NONE}, and every finding compares as new,
 * since there is nothing to compare against.
 */
@Tag("integration")
class AC30_NoBaselineObserveModeTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac30_noBaselineAnywhereCompletesNormallyWithEveryFindingMarkedNew(@TempDir Path outputDir)
      throws Exception {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

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
            "0.1.0-test");
    ScanCommand command =
        new ScanCommand(fixture.path(), outputDir, null, scanUseCase, List.of(engine));

    int exitCode = command.call();

    assertThat(exitCode).isIn(0, 1);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(outputDir.resolve(JsonReporter.FILE_NAME).toFile());
    assertThat(root.get("run").get("baselineProvenance").asText()).isEqualTo("NONE");
    assertThat(root.get("findings")).hasSize(1);
    assertThat(root.get("findings").get(0).get("isNew").asBoolean()).isTrue();
  }
}
