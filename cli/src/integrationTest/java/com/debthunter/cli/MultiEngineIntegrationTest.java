package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.application.scan.ScanUseCase;
import com.debthunter.domain.Category;
import com.debthunter.domain.EngineStatus;
import com.debthunter.domain.Finding;
import com.debthunter.domain.ScanResult;
import com.debthunter.engine.architecture.ArchitectureRulesEngine;
import com.debthunter.engine.codemaat.CodeMaatEngine;
import com.debthunter.engine.spi.AnalysisEngine;
import com.debthunter.engine.staticanalysis.StaticAnalysisEngine;
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
 * Runs Code Maat, architecture rules, and the static-analysis adapter together in a single scan,
 * verifying that {@code ScanUseCase} invokes every configured engine and aggregates their findings
 * into one {@link ScanResult}, regardless of any individual engine's own health.
 */
@Tag("integration")
class MultiEngineIntegrationTest {

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void allThreeEnginesRunAndContributeToOneScanResult(@TempDir Path outputDir) {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile(
                ".debt-hunter-arch.yml",
                "rules:\n"
                    + "  - id: domain-no-application-dependency\n"
                    + "    appliesTo: \"**/domain/**/*.java\"\n"
                    + "    deniedImports:\n"
                    + "      - \"com.acme.application.**\"\n",
                "add arch rules")
            .commitFile(
                "src/main/java/com/acme/domain/Order.java",
                "package com.acme.domain;\n"
                    + "import com.acme.application.OrderService;\n"
                    + "class Order {}\n",
                "add Order")
            .commitFile(
                "sonar-report.json",
                "{\"issues\":[{\"key\":\"AXy1\",\"rule\":\"java:S1192\",\"severity\":\"MAJOR\","
                    + "\"component\":\"p:src/main/java/com/acme/domain/Order.java\",\"line\":1,"
                    + "\"message\":\"Sonar issue\"}]}",
                "add sonar report");

    List<AnalysisEngine> engines =
        List.of(new CodeMaatEngine(), new ArchitectureRulesEngine(), new StaticAnalysisEngine());
    ScanCommand command = new ScanCommand(fixture.path(), outputDir, defaultScanUseCase(), engines);

    int exitCode = command.call();

    assertThat(exitCode).isIn(0, 1);
    ScanResult scanResult = new JsonReporter().read(outputDir.resolve(JsonReporter.FILE_NAME));

    List<String> engineIds = scanResult.run().engines().stream().map(EngineStatus::id).toList();
    assertThat(engineIds)
        .containsExactlyInAnyOrder("code-maat", "architecture-rules", "static-analysis");

    List<Finding> findings = scanResult.findings();
    assertThat(findings.stream().map(Finding::category))
        .contains(Category.ARCHITECTURE, Category.STATIC_ANALYSIS);
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
