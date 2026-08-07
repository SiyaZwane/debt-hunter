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
import com.debthunter.repository.GitHistoryProvider;
import com.debthunter.testkit.FixtureRepoBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AC-01: a valid repo produces all outputs; every finding has every required field. */
@Tag("integration")
class AC01_ValidRepoProducesAllOutputsTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac01_validRepoProducesAllOutputs(@TempDir Path outputDir) throws IOException {
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
                        .confidence(0.8)
                        .path("Foo.java")
                        .startLine(1)
                        .message("Foo.java changes often")
                        .score(5.0)
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
            "0.1.0-test");
    ScanCommand command = new ScanCommand(fixture.path(), outputDir, scanUseCase, List.of(engine));

    int exitCode = command.call();

    assertThat(exitCode).isIn(0, 1);
    assertThat(outputDir.resolve(JsonReporter.FILE_NAME)).exists();
    assertThat(outputDir.resolve(MarkdownReporter.FILE_NAME)).exists();
    assertThat(outputDir.resolve(MetricsReporter.FILE_NAME)).exists();

    JsonNode root = new ObjectMapper().readTree(outputDir.resolve(JsonReporter.FILE_NAME).toFile());
    JsonNode findings = root.get("findings");
    assertThat(findings).hasSize(1);
    JsonNode finding = findings.get(0);
    for (String requiredField :
        List.of(
            "id",
            "ruleId",
            "category",
            "severity",
            "confidence",
            "path",
            "startLine",
            "message",
            "evidence",
            "score",
            "isNew",
            "fingerprint")) {
      assertThat(finding.has(requiredField))
          .as("finding should have field '%s'", requiredField)
          .isTrue();
      assertThat(finding.get(requiredField).isNull())
          .as("finding field '%s' should not be null", requiredField)
          .isFalse();
    }
  }
}
