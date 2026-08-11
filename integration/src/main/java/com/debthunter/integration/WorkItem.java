package com.debthunter.integration;

import java.util.Objects;

/** A tracker item published for one finding fingerprint, and whether it is still open. */
public record WorkItem(String fingerprint, String externalId, boolean open) {

  /** Validates required fields. */
  public WorkItem {
    Objects.requireNonNull(fingerprint, "fingerprint");
    Objects.requireNonNull(externalId, "externalId");
  }
}
