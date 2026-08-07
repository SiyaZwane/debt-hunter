package com.debthunter.engine.spi;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Everything an {@link AnalysisEngine} needs to analyse one repository. */
public record AnalysisRequest(
    Path repoPath,
    String baseRef,
    AnalysisMode mode,
    Duration historyWindow,
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
