package com.debthunter.engine.codemaat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.debthunter.domain.Category;
import com.debthunter.domain.EngineHealth;
import com.debthunter.engine.spi.AnalysisMode;
import com.debthunter.engine.spi.AnalysisRequest;
import com.debthunter.engine.spi.EngineResult;
import com.debthunter.engine.spi.ProgressSink;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link CodeMaatEngine} against a real Code Maat installation, not the fake stand-ins
 * every other test in this module uses. Skips gracefully wherever no real installation is present
 * (routine CI included) — this is a "did the real thing still work last time someone checked" smoke
 * test, not a required correctness gate.
 *
 * <p>Not run by default. To run it:
 *
 * <pre>{@code
 * # Download the real jar once, and a wrapper script matching the -l/-c/-a contract this
 * # engine invokes (java -jar itself doesn't fit that contract directly):
 * mkdir -p ~/.local/opt/code-maat ~/.local/bin
 * curl -sL -o ~/.local/opt/code-maat/code-maat-1.0.4-standalone.jar \
 *   https://github.com/adamtornhill/code-maat/releases/download/v1.0.4/code-maat-1.0.4-standalone.jar
 * cat > ~/.local/bin/code-maat <<'SCRIPT'
 * #!/bin/sh
 * exec java -jar "$HOME/.local/opt/code-maat/code-maat-1.0.4-standalone.jar" "$@"
 * SCRIPT
 * chmod +x ~/.local/bin/code-maat
 *
 * mvn -pl engine-codemaat -am verify -Dfailsafe.groups=codemaat-real
 * }</pre>
 */
@Tag("codemaat-real")
class RealCodeMaatSmokeTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void realCodeMaatProducesHotspotChurnAndKnowledgeConcentrationFindings() {
    Path executable = resolveExecutable();
    assumeTrue(
        Files.isExecutable(executable),
        "No real Code Maat executable at "
            + executable
            + " — set CODEMAAT_EXECUTABLE or see this class's Javadoc to install one");

    fixture =
        FixtureRepoBuilder.init()
            .commitFile("PaymentProcessor.java", "v1", "initial")
            .hotspot("PaymentProcessor.java", 12)
            .commitFile("Utils.java", "v1", "add utils");

    CodeMaatEngine engine = new CodeMaatEngine(executable);
    AnalysisRequest request =
        new AnalysisRequest(
            fixture.path(), null, AnalysisMode.FULL, null, Map.of(), Duration.ofSeconds(30), 0);

    EngineResult result = engine.analyse(request, ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.OK);
    assertThat(result.findings())
        .anySatisfy(
            f -> {
              assertThat(f.category()).isEqualTo(Category.HOTSPOT);
              assertThat(f.path()).isEqualTo("PaymentProcessor.java");
            });
    assertThat(result.findings())
        .anySatisfy(f -> assertThat(f.category()).isEqualTo(Category.KNOWLEDGE_CONCENTRATION));
    assertThat(result.findings()).noneMatch(f -> f.path().equals("Utils.java"));
  }

  private Path resolveExecutable() {
    String configured = System.getenv("CODEMAAT_EXECUTABLE");
    if (configured != null) {
      return Path.of(configured);
    }
    return Path.of(System.getProperty("user.home"), ".local/bin/code-maat");
  }
}
