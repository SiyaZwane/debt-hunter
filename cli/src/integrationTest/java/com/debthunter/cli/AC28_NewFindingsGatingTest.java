package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.debthunter.engine.spi.AnalysisEngine;
import com.debthunter.engine.spi.CostClass;
import com.debthunter.engine.spi.EngineDescriptor;
import com.debthunter.engine.spi.EngineResult;
import com.debthunter.engine.spi.RepositoryContext;
import com.debthunter.output.BaselineWriter;
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
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-28: a scan compared against a baseline marks findings that already existed there as {@code
 * isNew: false}, and findings absent from the baseline as {@code isNew: true} — the flag a future
 * policy evaluator will gate on.
 */
@Tag("integration")
class AC28_NewFindingsGatingTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac28_findingsAreMarkedNewOrExistingRelativeToTheBaseline(
      @TempDir Path outputDir, @TempDir Path baselineDir) throws Exception {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    Finding existingFinding = finding("f-existing", "fp-existing");
    Finding newFinding = finding("f-new", "fp-new");

    Path baselinePath = writeBaseline(baselineDir, existingFinding);

    AnalysisEngine engine = mock(AnalysisEngine.class);
    when(engine.descriptor())
        .thenReturn(new EngineDescriptor("fake", "1.0", List.of(Category.HOTSPOT), CostClass.LOW));
    when(engine.supports(any(RepositoryContext.class))).thenReturn(true);
    when(engine.analyse(any(), any()))
        .thenReturn(EngineResult.ok(List.of(existingFinding, newFinding), List.of(), 5));

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
            "0.1.0-test");
    ScanCommand command =
        new ScanCommand(fixture.path(), outputDir, baselinePath, scanUseCase, List.of(engine));

    int exitCode = command.call();

    assertThat(exitCode).isIn(0, 1);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(outputDir.resolve(JsonReporter.FILE_NAME).toFile());
    assertThat(root.get("run").get("baselineProvenance").asText()).isEqualTo("EXPLICIT");

    Map<String, Boolean> isNewById = new java.util.HashMap<>();
    for (JsonNode findingNode : root.get("findings")) {
      isNewById.put(findingNode.get("id").asText(), findingNode.get("isNew").asBoolean());
    }
    assertThat(isNewById).containsEntry("f-existing", false).containsEntry("f-new", true);
  }

  private Path writeBaseline(Path baselineDir, Finding... findings) {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("baseline-run")
            .toolVersion("0.1.0-test")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    ScanResult baseline =
        new ScanResult(run, List.of(findings), Map.of(), PolicyResult.passed("unversioned"));
    return new BaselineWriter().write(baseline, baselineDir);
  }

  private Finding finding(String id, String fingerprint) {
    return Finding.builder()
        .id(id)
        .ruleId("hotspot.rule")
        .category(Category.HOTSPOT)
        .severity(Severity.HIGH)
        .path("Foo.java")
        .message("Foo.java changes often")
        .fingerprint(fingerprint)
        .build();
  }
}
