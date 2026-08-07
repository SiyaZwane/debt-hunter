package com.debthunter.application.scan;

import com.debthunter.domain.ScanResult;

/**
 * What {@link ScanUseCase#execute} produced: a process exit code, and either the completed {@link
 * ScanResult} or a diagnostic message explaining why analysis never ran.
 */
public record ScanOutcome(int exitCode, ScanResult scanResult, String diagnosticMessage) {

  /**
   * A completed scan.
   *
   * @param exitCode the process exit code to report
   * @param scanResult the completed result
   * @return an outcome carrying {@code scanResult} and no diagnostic message
   */
  public static ScanOutcome ofResult(int exitCode, ScanResult scanResult) {
    return new ScanOutcome(exitCode, scanResult, null);
  }

  /**
   * A scan that could not run at all (e.g. the path is not a Git repository).
   *
   * @param exitCode the process exit code to report
   * @param diagnosticMessage a human-readable explanation, suitable for stderr
   * @return an outcome with no {@link ScanResult}
   */
  public static ScanOutcome ofError(int exitCode, String diagnosticMessage) {
    return new ScanOutcome(exitCode, null, diagnosticMessage);
  }
}
