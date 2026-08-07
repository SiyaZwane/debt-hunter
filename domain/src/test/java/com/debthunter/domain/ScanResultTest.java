package com.debthunter.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScanResultTest {

  private AnalysisRun.Builder runBuilder() {
    return AnalysisRun.builder()
        .id("run-1")
        .toolVersion("0.1.0")
        .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
        .repository("/repo")
        .commit("abc123")
        .historyDepth(HistoryDepth.FULL);
  }

  @Test
  void ac01_aggregatesRunFindingsMetricsAndPolicy() {
    Finding finding =
        Finding.builder()
            .id("f-1")
            .ruleId("rule")
            .category(Category.HOTSPOT)
            .severity(Severity.LOW)
            .path("Foo.java")
            .message("msg")
            .fingerprint("fp")
            .build();
    DebtMetric metric = new DebtMetric("churn", 1.0, "repo");
    AnalysisRun run = runBuilder().build();

    ScanResult result =
        new ScanResult(
            run, List.of(finding), Map.of("churn", metric), PolicyResult.passed("bundle-1"));

    assertThat(result.run()).isEqualTo(run);
    assertThat(result.findings()).containsExactly(finding);
    assertThat(result.metrics()).containsEntry("churn", metric);
    assertThat(result.policy().status()).isEqualTo(PolicyStatus.PASSED);
  }

  @Test
  void nullFindingsAndMetricsDefaultToEmptyCollections() {
    ScanResult result = new ScanResult(runBuilder().build(), null, null, PolicyResult.passed("b"));

    assertThat(result.findings()).isEmpty();
    assertThat(result.metrics()).isEmpty();
  }

  @Test
  void collectionsAreDefensivelyCopiedAndImmutable() {
    var mutableFindings = new java.util.ArrayList<Finding>();
    ScanResult result =
        new ScanResult(runBuilder().build(), mutableFindings, null, PolicyResult.passed("b"));

    mutableFindings.add(
        Finding.builder()
            .id("late")
            .ruleId("rule")
            .category(Category.HOTSPOT)
            .severity(Severity.LOW)
            .path("Foo.java")
            .message("msg")
            .fingerprint("fp")
            .build());

    assertThat(result.findings()).isEmpty();
  }

  @Test
  void degradedIsFalseWhenAllEnginesOk() {
    AnalysisRun run =
        runBuilder()
            .engines(List.of(new EngineStatus("code-maat", "1.0", EngineHealth.OK, 100, null)))
            .build();

    ScanResult result = new ScanResult(run, null, null, PolicyResult.passed("b"));

    assertThat(result.isDegraded()).isFalse();
  }

  @Test
  void degradedIsTrueWhenAnyEngineIsNotOk() {
    AnalysisRun run =
        runBuilder()
            .engines(
                List.of(
                    new EngineStatus("code-maat", "1.0", EngineHealth.OK, 100, null),
                    new EngineStatus("static-analysis", "1.0", EngineHealth.FAILED, 50, "boom")))
            .build();

    ScanResult result = new ScanResult(run, null, null, PolicyResult.passed("b"));

    assertThat(result.isDegraded()).isTrue();
  }

  @Test
  void degradedIsFalseWhenNoEnginesRan() {
    AnalysisRun run = runBuilder().build();

    ScanResult result = new ScanResult(run, null, null, PolicyResult.passed("b"));

    assertThat(result.isDegraded()).isFalse();
  }
}
