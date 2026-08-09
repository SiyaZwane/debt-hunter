package com.debthunter.domain;

import java.util.Objects;

/**
 * A stored snapshot of a {@link ScanResult}, used as the comparison point for a later scan. {@code
 * signature} is {@code null} when the artefact was written without a signing key configured.
 */
public record BaselineArtifact(
    String schemaVersion, String toolVersion, ScanResult scanResult, String signature) {

  /** The schema version this build of Debt Hunter writes. */
  public static final String CURRENT_SCHEMA_VERSION = "1.0";

  /** Validates required fields; {@code signature} is intentionally nullable. */
  public BaselineArtifact {
    Objects.requireNonNull(schemaVersion, "schemaVersion");
    Objects.requireNonNull(toolVersion, "toolVersion");
    Objects.requireNonNull(scanResult, "scanResult");
  }

  /**
   * Wraps a {@link ScanResult} as an unsigned baseline artefact, with its tool version taken from
   * the result's own run metadata.
   *
   * @param scanResult the result to snapshot
   * @return the unsigned artefact
   */
  public static BaselineArtifact unsigned(ScanResult scanResult) {
    return new BaselineArtifact(
        CURRENT_SCHEMA_VERSION, scanResult.run().toolVersion(), scanResult, null);
  }

  /**
   * Returns a copy of this artefact with {@code signature} set.
   *
   * @param signature the computed signature
   * @return the signed copy
   */
  public BaselineArtifact withSignature(String signature) {
    return new BaselineArtifact(schemaVersion, toolVersion, scanResult, signature);
  }
}
