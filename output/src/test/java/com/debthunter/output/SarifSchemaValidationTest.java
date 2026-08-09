package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.debthunter.testkit.JsonSchemaValidator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every shape of {@code debt-hunter.sarif} this reporter can produce must validate against 2.1.0.
 */
class SarifSchemaValidationTest {

  private static final String SARIF_SCHEMA = "/schemas/sarif-2.1.0.schema.json";

  private final SarifReporter reporter = new SarifReporter();

  @Test
  void emptyFindingsStillValidates(@TempDir Path outputDir) {
    Path written = reporter.write(scanResultWith(List.of()), outputDir);

    assertThat(JsonSchemaValidator.validate(written, SARIF_SCHEMA)).isEmpty();
  }

  @Test
  void findingsWithAndWithoutSpecificLinesValidate(@TempDir Path outputDir) {
    Finding fileLevel = finding("codemaat.churn", Category.CHURN, Severity.MEDIUM, "Foo.java", 0);
    Finding lineLevel =
        finding("static.rule", Category.STATIC_ANALYSIS, Severity.HIGH, "Bar.java", 42);

    Path written = reporter.write(scanResultWith(List.of(fileLevel, lineLevel)), outputDir);

    assertThat(JsonSchemaValidator.validate(written, SARIF_SCHEMA)).isEmpty();
  }

  @Test
  void everySeverityLevelValidates(@TempDir Path outputDir) {
    List<Finding> findings =
        List.of(
            finding("r1", Category.STATIC_ANALYSIS, Severity.CRITICAL, "A.java", 1),
            finding("r2", Category.STATIC_ANALYSIS, Severity.HIGH, "B.java", 1),
            finding("r3", Category.STATIC_ANALYSIS, Severity.MEDIUM, "C.java", 1),
            finding("r4", Category.STATIC_ANALYSIS, Severity.LOW, "D.java", 1),
            finding("r5", Category.STATIC_ANALYSIS, Severity.INFO, "E.java", 1));

    Path written = reporter.write(scanResultWith(findings), outputDir);

    assertThat(JsonSchemaValidator.validate(written, SARIF_SCHEMA)).isEmpty();
  }

  @Test
  void multiProjectDocumentValidates(@TempDir Path outputDir) {
    Map<String, List<Finding>> byProject = new LinkedHashMap<>();
    byProject.put(
        "frontend", List.of(finding("r1", Category.STATIC_ANALYSIS, Severity.LOW, "app.js", 1)));
    byProject.put(
        "backend", List.of(finding("r2", Category.STATIC_ANALYSIS, Severity.LOW, "Api.java", 1)));

    Path written = reporter.writeMultiProject(byProject, "0.1.0", outputDir);

    assertThat(JsonSchemaValidator.validate(written, SARIF_SCHEMA)).isEmpty();
  }

  private ScanResult scanResultWith(List<Finding> findings) {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    return new ScanResult(run, findings, Map.of(), PolicyResult.passed("b"));
  }

  private Finding finding(
      String ruleId, Category category, Severity severity, String path, int startLine) {
    return Finding.builder()
        .id(ruleId + ":" + path)
        .ruleId(ruleId)
        .category(category)
        .severity(severity)
        .path(path)
        .startLine(startLine)
        .message("message")
        .fingerprint("fp-" + ruleId + ":" + path)
        .build();
  }
}
