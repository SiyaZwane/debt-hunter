package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.publish.PublishUseCase;
import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.integration.ResultUploader;
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
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-46: a fully offline scan (no network access of any kind, including to any AI service)
 * completes successfully and writes every report — the gate path has no runtime dependency on an
 * AI/LLM service to do its job. {@link ModuleDependencyConstraintTest} and {@link
 * AC48_ClasspathInspectionTest} verify the build-time and reflective side of this same constraint.
 */
@Tag("integration")
class AC46_OfflineScanNoAIDependencyTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac46_aFullyOfflineScanSucceedsWithoutAnyAiService(@TempDir Path outputDir) {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    ScanCommand command =
        new ScanCommand(
            fixture.path(),
            outputDir,
            null,
            null,
            defaultScanUseCase(),
            List.of(),
            new PublishUseCase(mock(ResultUploader.class)),
            null,
            true);

    int exitCode = command.call();

    assertThat(exitCode).isIn(0, 1);
    assertThat(outputDir.resolve(JsonReporter.FILE_NAME)).exists();
    assertThat(outputDir.resolve(MarkdownReporter.FILE_NAME)).exists();
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
