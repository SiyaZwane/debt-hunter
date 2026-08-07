package com.debthunter.domain;

import java.util.Objects;

/** The recorded outcome of running one analysis engine during a scan. */
public record EngineStatus(
    String id, String version, EngineHealth status, long durationMs, String reason) {

  /** Validates required fields; {@code reason} is optional and may be {@code null}. */
  public EngineStatus {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(status, "status");
  }
}
