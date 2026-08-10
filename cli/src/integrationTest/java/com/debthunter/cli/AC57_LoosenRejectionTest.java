package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.scan.ExitCode;
import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.domain.Category;
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
import com.debthunter.policy.PolicyComposer;
import com.debthunter.policy.PolicyEvaluator;
import com.debthunter.repository.GitHistoryProvider;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-57: a repo-local {@code .debt-hunter.yml} that tries to loosen a central policy threshold
 * fails the scan with exit code 2 (configuration error) — never a silently-accepted, weaker policy,
 * and never treated as a policy violation itself.
 */
@Tag("integration")
class AC57_LoosenRejectionTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac57_aLoosenedLocalOverrideFailsWithConfigurationErrorExitCode(
      @TempDir Path outputDir, @TempDir Path workDir) throws IOException {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    Path centralPolicyPath = workDir.resolve("policy.yml");
    Files.writeString(
        centralPolicyPath,
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-critical
                severity: CRITICAL
                maxCount: 0
        """);
    Files.writeString(
        fixture.path().resolve(PolicyComposer.LOCAL_POLICY_FILE_NAME),
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-critical
                severity: CRITICAL
                maxCount: 5
        """);

    AnalysisEngine engine = mock(AnalysisEngine.class);
    when(engine.descriptor())
        .thenReturn(new EngineDescriptor("fake", "1.0", List.of(Category.HOTSPOT), CostClass.LOW));
    when(engine.supports(any(RepositoryContext.class))).thenReturn(true);
    when(engine.analyse(any(), any())).thenReturn(EngineResult.ok(List.of(), List.of(), 5));

    ScanCommand command =
        new ScanCommand(
            fixture.path(),
            outputDir,
            centralPolicyPath,
            null,
            defaultScanUseCase(),
            List.of(engine));

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(ExitCode.CONFIGURATION_ERROR.code());
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
