package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.scan.ExitCode;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-37: with no baseline configured, a rule that would otherwise fail the scan instead reports
 * {@code would_fail} and exits 0 — a first-ever scan observes rather than blocks.
 */
@Tag("integration")
class AC37_ObserveModeTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac37_aViolatedRuleWithNoBaselineObservesInsteadOfFailing(
      @TempDir Path outputDir, @TempDir Path workDir) throws Exception {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    Path policyPath = workDir.resolve("policy.yml");
    Files.writeString(
        policyPath,
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-new-critical
                severity: CRITICAL
                maxCount: 0
        """);

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
                        .severity(Severity.CRITICAL)
                        .path("Foo.java")
                        .message("Foo.java is critically overdue")
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
        new ScanCommand(fixture.path(), outputDir, policyPath, null, scanUseCase, List.of(engine));

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(ExitCode.POLICY_SATISFIED.code());
    JsonNode root = new ObjectMapper().readTree(outputDir.resolve(JsonReporter.FILE_NAME).toFile());
    assertThat(root.get("policy").get("status").asText()).isEqualTo("WOULD_FAIL");
    assertThat(root.get("policy").get("reasons")).hasSize(1);
    assertThat(root.get("run").get("baselineProvenance").asText()).isEqualTo("NONE");
  }
}
