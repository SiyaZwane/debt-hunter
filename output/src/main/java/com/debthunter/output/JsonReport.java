package com.debthunter.output;

import com.debthunter.domain.ScanResult;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.Objects;

/**
 * The versioned envelope every {@code debt-hunter.json} document is wrapped in: a {@code
 * schemaVersion} field alongside the (unwrapped) {@link ScanResult} fields, so a consumer can tell
 * at a glance which shape it's reading before parsing further.
 */
public record JsonReport(String schemaVersion, @JsonUnwrapped ScanResult scanResult) {

  /** The schema version this build of Debt Hunter writes. */
  public static final String CURRENT_SCHEMA_VERSION = "1.0";

  /** Validates required fields. */
  public JsonReport {
    Objects.requireNonNull(schemaVersion, "schemaVersion");
    Objects.requireNonNull(scanResult, "scanResult");
  }

  /**
   * Wraps a {@link ScanResult} with the current schema version.
   *
   * @param scanResult the result to wrap
   * @return the versioned envelope
   */
  public static JsonReport of(ScanResult scanResult) {
    return new JsonReport(CURRENT_SCHEMA_VERSION, scanResult);
  }
}
