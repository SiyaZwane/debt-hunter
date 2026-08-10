package com.debthunter.policy;

/** A suppressions file's YAML is malformed, or does not conform to the expected shape. */
public final class SuppressionParseException extends RuntimeException {

  /**
   * Creates the exception.
   *
   * @param message a human-readable description of what's wrong with the file
   */
  public SuppressionParseException(String message) {
    super(message);
  }

  /**
   * Creates the exception with an underlying cause.
   *
   * @param message a human-readable description of what's wrong with the file
   * @param cause the underlying cause
   */
  public SuppressionParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
