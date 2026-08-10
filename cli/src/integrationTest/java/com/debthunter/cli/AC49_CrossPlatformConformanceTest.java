package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.output.JsonReporter;
import com.debthunter.testkit.ConformanceRunner;
import com.debthunter.testkit.ConformanceSuite;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * AC-49: every fixture in {@link ConformanceSuite} conforms — produces equal, masked output —
 * whether the host JVM's default time zone/locale is UTC/US (the reference) or a deliberately
 * far-flung pairing (the candidate). This generalises AC-14's single ad hoc repo into the whole
 * conformance suite, through the shared {@link ConformanceRunner} rather than duplicated logic.
 */
@Tag("integration")
class AC49_CrossPlatformConformanceTest {

  @Test
  void ac49_everySuiteFixtureConformsAcrossTimeZonesAndLocales(@TempDir Path workDir)
      throws Exception {
    ConformanceRunner runner =
        new ConformanceRunner(
            scanUnder(TimeZone.getTimeZone("UTC"), Locale.US),
            // Asia/Kolkata: UTC+5:30, far from UTC. Germany: comma decimal separator, a classic
            // trap for locale-sensitive number formatting.
            scanUnder(TimeZone.getTimeZone("Asia/Kolkata"), Locale.GERMANY));

    var results = runner.runAll(ConformanceSuite.fixtures(), workDir);

    assertThat(results)
        .allSatisfy(result -> assertThat(result.matches()).as(result.describe()).isTrue());
  }

  private com.debthunter.testkit.ScanInvoker scanUnder(TimeZone timeZone, Locale locale) {
    return (repoPath, outputDir) -> {
      TimeZone originalTimeZone = TimeZone.getDefault();
      Locale originalLocale = Locale.getDefault();
      try {
        TimeZone.setDefault(timeZone);
        Locale.setDefault(locale);
        new CommandLine(new DebtHunterCli())
            .execute("scan", "--repo", repoPath.toString(), "--output-dir", outputDir.toString());
        return readReport(outputDir);
      } finally {
        TimeZone.setDefault(originalTimeZone);
        Locale.setDefault(originalLocale);
      }
    };
  }

  private JsonNode readReport(Path outputDir) throws Exception {
    return new ObjectMapper().readTree(outputDir.resolve(JsonReporter.FILE_NAME).toFile());
  }
}
