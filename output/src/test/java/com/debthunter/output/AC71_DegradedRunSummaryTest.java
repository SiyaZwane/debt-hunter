package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.EngineHealth;
import com.debthunter.domain.EngineStatus;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AC-71: a degraded run's summary calls out exactly the engines that degraded or failed, with their
 * reason, and leaves healthy engines out of that section.
 */
class AC71_DegradedRunSummaryTest {

  private final MarkdownReporter reporter = new MarkdownReporter();

  @Test
  void ac71_degradedRunSummaryListsOnlyTheDegradedEngines() {
    EngineStatus healthy = new EngineStatus("architecture", "1.0", EngineHealth.OK, 10, null);
    EngineStatus degraded =
        new EngineStatus("code-maat", "1.0", EngineHealth.DEGRADED, 5000, "timed out after 5s");
    EngineStatus failed =
        new EngineStatus("static-analysis", "1.0", EngineHealth.FAILED, 0, "binary not found");

    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(com.debthunter.domain.HistoryDepth.FULL)
            .engines(List.of(healthy, degraded, failed))
            .build();
    ScanResult scanResult =
        new ScanResult(run, List.of(), Map.of(), PolicyResult.passed("bundle-1"));

    String markdown = reporter.render(scanResult);

    assertThat(markdown).contains("**Degraded:** true");
    assertThat(markdown)
        .contains("## Degraded engines (2)")
        .contains("code-maat")
        .contains("timed out after 5s")
        .contains("static-analysis")
        .contains("binary not found");

    int sectionStart = markdown.indexOf("## Degraded engines");
    int sectionEnd = markdown.indexOf("\n## ", sectionStart + 1);
    String degradedSection = markdown.substring(sectionStart, sectionEnd);
    assertThat(degradedSection).doesNotContain("architecture");
  }
}
