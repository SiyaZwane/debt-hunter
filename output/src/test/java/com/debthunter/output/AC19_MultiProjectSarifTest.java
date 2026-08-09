package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import com.debthunter.testkit.JsonSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AC-19: a monorepo scan produces one SARIF run per project, each scoped to its own findings. */
class AC19_MultiProjectSarifTest {

  private final SarifReporter reporter = new SarifReporter();
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void ac19_eachProjectGetsItsOwnRunWithOnlyItsOwnFindingsAndCategory(@TempDir Path outputDir)
      throws Exception {
    Finding frontendFinding = finding("js.rule", "app.js");
    Finding backendFinding = finding("java.rule", "Api.java");
    Map<String, List<Finding>> byProject = new LinkedHashMap<>();
    byProject.put("frontend", List.of(frontendFinding));
    byProject.put("backend", List.of(backendFinding));

    Path written = reporter.writeMultiProject(byProject, "0.1.0", outputDir);

    JsonNode runs = json.readTree(written.toFile()).get("runs");
    assertThat(runs).hasSize(2);

    JsonNode frontendRun = runByCategory(runs, "debt-hunter/frontend");
    assertThat(frontendRun.get("automationDetails").get("id").asText())
        .isEqualTo("debt-hunter/frontend/");
    assertThat(frontendRun.get("results")).hasSize(1);
    assertThat(frontendRun.get("results").get(0).get("ruleId").asText()).isEqualTo("js.rule");

    JsonNode backendRun = runByCategory(runs, "debt-hunter/backend");
    assertThat(backendRun.get("automationDetails").get("id").asText())
        .isEqualTo("debt-hunter/backend/");
    assertThat(backendRun.get("results")).hasSize(1);
    assertThat(backendRun.get("results").get(0).get("ruleId").asText()).isEqualTo("java.rule");

    assertThat(JsonSchemaValidator.validate(written, "/schemas/sarif-2.1.0.schema.json")).isEmpty();
  }

  @Test
  void ac19_wholeRepoScanProducesExactlyOneRun(@TempDir Path outputDir) throws Exception {
    Map<String, List<Finding>> byProject = new LinkedHashMap<>();
    byProject.put(null, List.of(finding("rule", "Foo.java")));

    Path written = reporter.writeMultiProject(byProject, "0.1.0", outputDir);

    JsonNode runs = json.readTree(written.toFile()).get("runs");
    assertThat(runs).hasSize(1);
    assertThat(runs.get(0).get("automationDetails").get("id").asText()).isEqualTo("debt-hunter/");
  }

  private JsonNode runByCategory(JsonNode runs, String category) {
    for (JsonNode run : runs) {
      if (run.get("results").get(0).get("properties").get("category").asText().equals(category)) {
        return run;
      }
    }
    throw new AssertionError("No run found with category " + category);
  }

  private Finding finding(String ruleId, String path) {
    return Finding.builder()
        .id(ruleId + ":" + path)
        .ruleId(ruleId)
        .category(Category.STATIC_ANALYSIS)
        .severity(Severity.MEDIUM)
        .path(path)
        .startLine(1)
        .message("message")
        .fingerprint("fp-" + ruleId + ":" + path)
        .build();
  }
}
