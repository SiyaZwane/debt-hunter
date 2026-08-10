package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.publish.PublishUseCase;
import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.domain.ScanResult;
import com.debthunter.integration.PublishConfig;
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
 * AC-45: {@code --offline} skips publication entirely, even when a publish endpoint is configured —
 * the uploader is never invoked, and no publish warning is added.
 */
@Tag("integration")
class AC45_OfflineEnforcementTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac45_offlineSkipsPublicationDespiteAConfiguredEndpoint(@TempDir Path outputDir)
      throws IOException {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    ResultUploader uploader = mock(ResultUploader.class);
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
            true);

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(0);
    verify(uploader, never()).publish(any(ScanResult.class), any(PublishConfig.class));
    Path summary = outputDir.resolve(MarkdownReporter.FILE_NAME);
    assertThat(Files.readString(summary)).doesNotContain("failed to publish");
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
