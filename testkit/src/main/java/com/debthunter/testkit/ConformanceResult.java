package com.debthunter.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/** The outcome of comparing a fixture's reference and candidate outputs. */
public record ConformanceResult(
    String fixtureName, boolean matches, JsonNode referenceOutput, JsonNode candidateOutput) {

  /** Validates required fields. */
  public ConformanceResult {
    Objects.requireNonNull(fixtureName, "fixtureName");
    Objects.requireNonNull(referenceOutput, "referenceOutput");
    Objects.requireNonNull(candidateOutput, "candidateOutput");
  }

  /**
   * A human-readable description of this result, suitable for a failed assertion message.
   *
   * @return a one-line summary if matching, or both outputs if not
   */
  public String describe() {
    if (matches) {
      return fixtureName + ": conforms";
    }
    return fixtureName
        + ": MISMATCH\nreference: "
        + referenceOutput
        + "\ncandidate: "
        + candidateOutput;
  }
}
