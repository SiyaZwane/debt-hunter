package com.debthunter.output;

/**
 * Wraps a low-level I/O or parsing failure encountered while reading a previously written report.
 */
public final class ReportReadException extends RuntimeException {

  /**
   * Creates the exception with a message and cause.
   *
   * @param message a human-readable description of what failed
   * @param cause the underlying cause
   */
  public ReportReadException(String message, Throwable cause) {
    super(message, cause);
  }
}
