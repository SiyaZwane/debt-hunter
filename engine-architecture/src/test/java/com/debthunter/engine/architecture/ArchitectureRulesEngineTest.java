package com.debthunter.engine.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.EngineHealth;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import com.debthunter.engine.spi.AnalysisMode;
import com.debthunter.engine.spi.AnalysisRequest;
import com.debthunter.engine.spi.EngineResult;
import com.debthunter.engine.spi.ProgressSink;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ArchitectureRulesEngineTest {

  private final ArchitectureRulesEngine engine = new ArchitectureRulesEngine();
  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void descriptorAdvertisesTheArchitectureCategory() {
    var descriptor = engine.descriptor();

    assertThat(descriptor.id()).isEqualTo("architecture-rules");
    assertThat(descriptor.categories()).containsExactly(Category.ARCHITECTURE);
  }

  @Test
  void aRepositoryWithNoRulesFileProducesNoFindings() {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    EngineResult result = engine.analyse(request(fixture.path()), ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.OK);
    assertThat(result.findings()).isEmpty();
  }

  @Test
  void aDeniedImportProducesAnArchitectureFinding() {
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
                "add Order");

    EngineResult result = engine.analyse(request(fixture.path()), ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.OK);
    assertThat(result.findings()).hasSize(1);
    Finding finding = result.findings().get(0);
    assertThat(finding.category()).isEqualTo(Category.ARCHITECTURE);
    assertThat(finding.ruleId()).isEqualTo("architecture.domain-no-application-dependency");
    assertThat(finding.path()).isEqualTo("src/main/java/com/acme/domain/Order.java");
    assertThat(finding.message())
        .contains("com.acme.application.OrderService")
        .contains("domain-no-application-dependency");
  }

  @Test
  void anImportNotInTheAllowedListProducesAnArchitectureFinding() {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile(
                ".debt-hunter-arch.yml",
                "rules:\n"
                    + "  - id: domain-stdlib-only\n"
                    + "    appliesTo: \"**/domain/**/*.java\"\n"
                    + "    allowedImports:\n"
                    + "      - \"java.util.**\"\n"
                    + "    severity: MEDIUM\n",
                "add arch rules")
            .commitFile(
                "src/main/java/com/acme/domain/Order.java",
                "package com.acme.domain;\n"
                    + "import java.util.List;\n"
                    + "import java.io.File;\n"
                    + "class Order {}\n",
                "add Order");

    EngineResult result = engine.analyse(request(fixture.path()), ProgressSink.NO_OP);

    assertThat(result.findings()).hasSize(1);
    Finding finding = result.findings().get(0);
    assertThat(finding.severity()).isEqualTo(Severity.MEDIUM);
    assertThat(finding.message()).contains("java.io.File");
  }

  @Test
  void aCompliantFileProducesNoFindings() {
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
                "package com.acme.domain;\nimport java.util.List;\nclass Order {}\n",
                "add Order");

    EngineResult result = engine.analyse(request(fixture.path()), ProgressSink.NO_OP);

    assertThat(result.findings()).isEmpty();
  }

  @Test
  void aFileOutsideTheRuleScopeIsNotChecked() {
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
                "src/main/java/com/acme/application/OrderService.java",
                "package com.acme.application;\nclass OrderService {}\n",
                "add OrderService");

    EngineResult result = engine.analyse(request(fixture.path()), ProgressSink.NO_OP);

    assertThat(result.findings()).isEmpty();
  }

  @Test
  void malformedYamlFailsTheEngineRun() {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile(".debt-hunter-arch.yml", "rules: [unterminated", "add bad rules");

    EngineResult result = engine.analyse(request(fixture.path()), ProgressSink.NO_OP);

    assertThat(result.status()).isEqualTo(EngineHealth.FAILED);
    assertThat(result.reason()).contains(ArchitectureRulesEngine.RULES_FILE_NAME);
  }

  @Test
  void supportsIsTrueRegardlessOfRepositoryContext() {
    fixture = FixtureRepoBuilder.init();

    assertThat(
            engine.supports(
                new com.debthunter.engine.spi.RepositoryContext(
                    fixture.path(),
                    List.of(),
                    com.debthunter.engine.spi.VcsType.NONE,
                    com.debthunter.domain.HistoryDepth.SHALLOW)))
        .isTrue();
  }

  private AnalysisRequest request(java.nio.file.Path repoPath) {
    return new AnalysisRequest(
        repoPath, null, AnalysisMode.FULL, null, Map.of(), Duration.ofSeconds(30), 0);
  }
}
