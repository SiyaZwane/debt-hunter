package com.debthunter.engine.spi;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Everything an {@link AnalysisEngine} needs to analyse one repository.
 *
 * @param repoPath the repository's working-tree path
 * @param baseRef base ref to compare against, for pull-request mode, or {@code null}
 * @param mode which rule set applies: {@link AnalysisMode#PULL_REQUEST} or {@link
 *     AnalysisMode#FULL}
 * @param historyWindowSince only consider commits at or after this instant, or {@code null} for the
 *     engine's complete available history
 * @param policySettings engine-specific settings sourced from the active policy bundle
 * @param timeout the maximum time this engine may run before being cancelled
 * @param memoryLimitBytes an advisory memory ceiling, or {@code 0} for no limit
 */
public record AnalysisRequest(
    Path repoPath,
    String baseRef,
    AnalysisMode mode,
    Instant historyWindowSince,
    Map<String, Object> policySettings,
    Duration timeout,
    long memoryLimitBytes) {

  /** Validates required fields and defensively copies {@code policySettings}. */
  public AnalysisRequest {
    Objects.requireNonNull(repoPath, "repoPath");
    Objects.requireNonNull(mode, "mode");
    policySettings = policySettings == null ? Map.of() : Map.copyOf(policySettings);
    Objects.requireNonNull(timeout, "timeout");
  }
}
