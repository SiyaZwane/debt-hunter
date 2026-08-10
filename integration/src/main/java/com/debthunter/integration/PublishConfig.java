package com.debthunter.integration;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Where and how to publish a scan result. */
public record PublishConfig(URI endpoint, String apiKey, Duration timeout) {

  /** Validates required fields; {@code apiKey} is intentionally nullable. */
  public PublishConfig {
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(timeout, "timeout");
  }
}
