package com.debthunter.policy;

/** A policy bundle's YAML is malformed, or does not conform to the expected shape. */
public final class PolicyParseException extends RuntimeException {

  /**
   * Creates the exception.
   *
   * @param message a human-readable description of what's wrong with the bundle
   */
  public PolicyParseException(String message) {
    super(message);
  }

  /**
   * Creates the exception with an underlying cause.
   *
   * @param message a human-readable description of what's wrong with the bundle
   * @param cause the underlying cause
   */
  public PolicyParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
