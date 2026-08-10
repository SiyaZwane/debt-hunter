package com.debthunter.application.scan;

/**
 * The complete process exit-code contract for a scan. {@link ScanUseCase} decides which of these
 * applies; {@code ScanCommand} just returns the value it's given.
 */
public enum ExitCode {
  /** The policy passed (or observe mode overrode a failure — see {@link ScanOutcome}). */
  POLICY_SATISFIED(0),
  /** At least one policy rule was violated. */
  POLICY_VIOLATED(1),
  /** The target path is not usable, or the policy bundle's YAML is invalid. */
  CONFIGURATION_ERROR(2),
  /**
   * Reserved: more engines failed than the policy tolerates. Not yet triggered by any condition —
   * there is no policy setting yet for how many engine failures are acceptable.
   */
  ENGINE_TOLERANCE_EXCEEDED(3),
  /** The repository's history is shallower than the policy's minimum requirement. */
  INSUFFICIENT_HISTORY(4),
  /** An explicit or cached baseline was found but cannot be used. */
  BASELINE_UNAVAILABLE(5),
  /** Something failed that the caller cannot fix by changing their input. */
  INTERNAL_ERROR(10);

  private final int code;

  ExitCode(int code) {
    this.code = code;
  }

  /**
   * This exit code's process-level integer value.
   *
   * @return the value the process should exit with
   */
  public int code() {
    return code;
  }
}
