package com.debthunter.ai;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Where and how to request a finding's explanation from an AI service. */
public record ExplainConfig(URI endpoint, String apiKey, Duration timeout) {

  /** Validates required fields; {@code apiKey} is intentionally nullable. */
  public ExplainConfig {
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(timeout, "timeout");
  }
}
