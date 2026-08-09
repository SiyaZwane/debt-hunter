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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AC-18: the SARIF file is always present, and always valid against the 2.1.0 schema. */
class AC18_SarifPresentAndValidTest {

  @Test
  void ac18_sarifFileIsPresentAndValid(@TempDir Path outputDir) {
    Finding finding =
        Finding.builder()
            .id("f-1")
            .ruleId("static.rule")
            .category(Category.STATIC_ANALYSIS)
            .severity(Severity.MEDIUM)
            .path("Foo.java")
            .startLine(5)
            .message("message")
            .fingerprint("fp-1")
            .build();
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    ScanResult scanResult =
        new ScanResult(run, List.of(finding), Map.of(), PolicyResult.passed("b"));

    Path written = new SarifReporter().write(scanResult, outputDir);

    assertThat(Files.exists(written)).isTrue();
    assertThat(JsonSchemaValidator.validate(written, "/schemas/sarif-2.1.0.schema.json")).isEmpty();
  }
}
