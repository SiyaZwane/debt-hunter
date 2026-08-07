package com.debthunter.engine.spi;

import com.debthunter.domain.DebtMetric;
import com.debthunter.domain.EngineHealth;
import com.debthunter.domain.Finding;
import java.util.List;
import java.util.Objects;

/** What an {@link AnalysisEngine} produced from one {@link AnalysisRequest}. */
public record EngineResult(
    EngineHealth status,
    List<Finding> findings,
    List<DebtMetric> metrics,
    String reason,
    long durationMs) {

  /** Validates required fields and defensively copies {@code findings} and {@code metrics}. */
  public EngineResult {
    Objects.requireNonNull(status, "status");
    findings = findings == null ? List.of() : List.copyOf(findings);
    metrics = metrics == null ? List.of() : List.copyOf(metrics);
  }

  /**
   * A successful result with no degradation.
   *
   * @param findings the findings produced
   * @param metrics the metrics produced
   * @param durationMs how long the engine took, in milliseconds
   * @return an {@link EngineResult} with status {@link EngineHealth#OK}
   */
  public static EngineResult ok(List<Finding> findings, List<DebtMetric> metrics, long durationMs) {
    return new EngineResult(EngineHealth.OK, findings, metrics, null, durationMs);
  }

  /**
   * A result that produced partial output but was not fully reliable.
   *
   * @param findings the findings produced despite the degradation
   * @param metrics the metrics produced despite the degradation
   * @param reason why the engine is reporting degraded
   * @param durationMs how long the engine took, in milliseconds
   * @return an {@link EngineResult} with status {@link EngineHealth#DEGRADED}
   */
  public static EngineResult degraded(
      List<Finding> findings, List<DebtMetric> metrics, String reason, long durationMs) {
    return new EngineResult(EngineHealth.DEGRADED, findings, metrics, reason, durationMs);
  }

  /**
   * A result that produced nothing usable.
   *
   * @param reason why the engine failed
   * @param durationMs how long the engine ran before failing, in milliseconds
   * @return an {@link EngineResult} with status {@link EngineHealth#FAILED}
   */
  public static EngineResult failed(String reason, long durationMs) {
    return new EngineResult(EngineHealth.FAILED, List.of(), List.of(), reason, durationMs);
  }
}
