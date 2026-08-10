package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.scan.ProjectSlicer;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-53: a finding whose path matches none of the configured {@code --project} patterns is
 * attributed to {@link ProjectSlicer#DEFAULT_PROJECT} rather than dropped or misfiled.
 */
@Tag("integration")
class AC53_DefaultProjectTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac53_anUnmatchedFindingIsAttributedToTheDefaultProject(@TempDir Path outputDir)
      throws Exception {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("frontend/App.java", "class App {}", "add App")
            .commitFile("shared/Common.java", "class Common {}", "add Common");

    Finding frontendFinding = finding("f-frontend", "frontend/App.java");
    Finding sharedFinding = finding("f-shared", "shared/Common.java");

    AnalysisEngine engine = mock(AnalysisEngine.class);
    when(engine.descriptor())
        .thenReturn(
            new EngineDescriptor("fake", "1.0", List.of(Category.STATIC_ANALYSIS), CostClass.LOW));
    when(engine.supports(any(RepositoryContext.class))).thenReturn(true);
    when(engine.analyse(any(), any()))
        .thenReturn(EngineResult.ok(List.of(frontendFinding, sharedFinding), List.of(), 5));

    Map<String, String> projects = new LinkedHashMap<>();
    projects.put("frontend", "frontend");

    ScanCommand command =
        new ScanCommand(
            fixture.path(),
            outputDir,
            null,
            null,
            defaultScanUseCase(),
            List.of(engine),
            defaultPublishUseCase(),
            null,
            false,
            projects);

    int exitCode = command.call();

    assertThat(exitCode).isIn(0, 1);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode sarif = mapper.readTree(outputDir.resolve(SarifReporter.FILE_NAME).toFile());
    assertThat(sarif.get("runs")).hasSize(2);

    Map<String, JsonNode> runsByAutomationId = new LinkedHashMap<>();
    for (JsonNode run : sarif.get("runs")) {
      runsByAutomationId.put(run.get("automationDetails").get("id").asText(), run);
    }
    assertThat(runsByAutomationId)
        .containsKeys(
            "debt-hunter/frontend/", "debt-hunter/" + ProjectSlicer.DEFAULT_PROJECT + "/");
    assertThat(runsByAutomationId.get("debt-hunter/frontend/").get("results")).hasSize(1);
    assertThat(
            runsByAutomationId
                .get("debt-hunter/" + ProjectSlicer.DEFAULT_PROJECT + "/")
                .get("results"))
        .hasSize(1);
  }

  private com.debthunter.application.publish.PublishUseCase defaultPublishUseCase() {
    return new com.debthunter.application.publish.PublishUseCase(
        mock(com.debthunter.integration.ResultUploader.class));
  }

  private Finding finding(String id, String path) {
    return Finding.builder()
        .id(id)
        .ruleId("static.rule")
        .category(Category.STATIC_ANALYSIS)
        .severity(Severity.MEDIUM)
        .path(path)
        .message(path + " has an issue")
        .fingerprint("fp-" + id)
        .build();
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
