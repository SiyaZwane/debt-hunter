package com.debthunter.policy;

/** A repo-local policy override tried to loosen a threshold the central policy bundle sets. */
public final class PolicyLoosenedException extends RuntimeException {

  /**
   * Creates the exception.
   *
   * @param message a human-readable description of what was loosened, and by how much
   */
  public PolicyLoosenedException(String message) {
    super(message);
  }
}
