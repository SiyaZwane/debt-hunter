package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.publish.PublishUseCase;
import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.domain.ScanResult;
import com.debthunter.integration.PublishConfig;
import com.debthunter.integration.PublishResult;
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
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-44: a configured publish that fails is surfaced only as a warning appended to {@code
 * summary.md} — the scan's own exit code is untouched.
 */
@Tag("integration")
class AC44_PubFailureWarningOnlyTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac44_aFailedPublishOnlyAddsAWarningAndNeverAltersTheExitCode(@TempDir Path outputDir)
      throws IOException {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    ResultUploader uploader = mock(ResultUploader.class);
    when(uploader.publish(any(ScanResult.class), any(PublishConfig.class)))
        .thenReturn(PublishResult.ofFailure("connection refused"));
    ScanUseCase scanUseCase = defaultScanUseCase();
    ScanCommand command =
        new ScanCommand(
            fixture.path(),
            outputDir,
            null,
            null,
            scanUseCase,
            List.of(),
            new PublishUseCase(uploader),
            URI.create("https://example.invalid/results"),
            false);

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(0);
    Path summary = outputDir.resolve(MarkdownReporter.FILE_NAME);
    assertThat(Files.readString(summary))
        .contains("failed to publish scan result: connection refused");
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
