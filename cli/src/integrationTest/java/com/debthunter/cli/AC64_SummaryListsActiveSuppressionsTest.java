package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.output.JsonReporter;
import com.debthunter.output.MarkdownReporter;
import com.debthunter.output.MetricsReporter;
import com.debthunter.output.SarifReporter;
import com.debthunter.policy.BaselineComparator;
import com.debthunter.policy.BaselineResolver;
import com.debthunter.policy.PolicyBundleParser;
import com.debthunter.policy.PolicyEvaluator;
import com.debthunter.policy.SuppressionRegistry;
import com.debthunter.repository.GitHistoryProvider;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-64: {@code summary.md} lists every currently-active suppression — fingerprint, owner, expiry,
 * and reason — so a reviewer can see what's being excused from gating without having to go read the
 * suppressions file directly.
 */
@Tag("integration")
class AC64_SummaryListsActiveSuppressionsTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac64_summaryListsTheActiveSuppressionWithItsDetails(@TempDir Path outputDir)
      throws IOException {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    LocalDate expiresFarInTheFuture = LocalDate.now(ZoneOffset.UTC).plusDays(30);
    Files.writeString(
        fixture.path().resolve(SuppressionRegistry.SUPPRESSIONS_FILE_NAME),
        """
        suppressions:
          - fingerprint: fp-f-1
            owner: alice
            reason: "Tracked in JIRA-123"
            expires: "%s"
        """
            .formatted(expiresFarInTheFuture));

    ScanCommand command =
        new ScanCommand(fixture.path(), outputDir, defaultScanUseCase(), List.of());

    int exitCode = command.call();

    assertThat(exitCode).isIn(0, 1);
    String summary = Files.readString(outputDir.resolve(MarkdownReporter.FILE_NAME));
    assertThat(summary).contains("## Active suppressions (1)");
    assertThat(summary)
        .contains("fp-f-1")
        .contains("alice")
        .contains(expiresFarInTheFuture.toString())
        .contains("Tracked in JIRA-123");
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
