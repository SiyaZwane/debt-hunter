package com.debthunter.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The complete, aggregated result of one scan: the run, its findings, metrics, and verdict. */
public record ScanResult(
    AnalysisRun run, List<Finding> findings, Map<String, DebtMetric> metrics, PolicyResult policy) {

  /** Validates required fields and defensively copies {@code findings} and {@code metrics}. */
  public ScanResult {
    Objects.requireNonNull(run, "run");
    Objects.requireNonNull(policy, "policy");
    findings = findings == null ? List.of() : List.copyOf(findings);
    metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
  }

  /**
   * Whether any engine in this scan's run finished with a non-OK status.
   *
   * @return {@code true} if the run is degraded
   */
  public boolean isDegraded() {
    return run.degraded();
  }
}
