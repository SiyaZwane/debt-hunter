package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.output.JsonReporter;
import com.debthunter.testkit.FixtureRepoBuilder;
import com.debthunter.testkit.VolatileFieldMasker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * AC-14: the report is identical regardless of the host's default time zone and locale. Real
 * multi-OS/multi-arch execution is covered by AC-08's container test; this is the practical proxy
 * for "cross-platform" a single JVM can exercise — a naive {@code String.format}, {@code
 * SimpleDateFormat}, or default-locale number formatter anywhere in the pipeline would leak the
 * host environment into the output, and this catches it directly.
 */
@Tag("integration")
class AC14_CrossPlatformDeterminismTest {

  private FixtureRepoBuilder fixture;
  private TimeZone originalTimeZone;
  private Locale originalLocale;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
    if (originalTimeZone != null) {
      TimeZone.setDefault(originalTimeZone);
    }
    if (originalLocale != null) {
      Locale.setDefault(originalLocale);
    }
  }

  @Test
  void ac14_outputIsIdenticalRegardlessOfJvmDefaultTimeZoneAndLocale(@TempDir Path outputRoot)
      throws IOException {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo {}", "add Foo")
            .commitFile("Bar.java", "class Bar {}", "add Bar");

    originalTimeZone = TimeZone.getDefault();
    originalLocale = Locale.getDefault();

    Path outputDirA = outputRoot.resolve("utc-us");
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Locale.setDefault(Locale.US);
    int exitA = runScan(outputDirA);

    Path outputDirB = outputRoot.resolve("kolkata-germany");
    // Asia/Kolkata: UTC+5:30, far from UTC. Germany: comma decimal separator, a classic trap
    // for locale-sensitive number formatting.
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    Locale.setDefault(Locale.GERMANY);
    int exitB = runScan(outputDirB);

    assertThat(exitA).isEqualTo(exitB);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode jsonA = mapper.readTree(outputDirA.resolve(JsonReporter.FILE_NAME).toFile());
    JsonNode jsonB = mapper.readTree(outputDirB.resolve(JsonReporter.FILE_NAME).toFile());
    VolatileFieldMasker.mask(jsonA);
    VolatileFieldMasker.mask(jsonB);

    assertThat(jsonA).isEqualTo(jsonB);
  }

  private int runScan(Path outputDir) {
    return new CommandLine(new DebtHunterCli())
        .execute("scan", "--repo", fixture.path().toString(), "--output-dir", outputDir.toString());
  }
}
