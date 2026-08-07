package com.debthunter.output;

/** Wraps a low-level I/O failure encountered while writing a report to disk. */
public final class ReportWriteException extends RuntimeException {

  /**
   * Creates the exception with a message and cause.
   *
   * @param message a human-readable description of what failed
   * @param cause the underlying cause
   */
  public ReportWriteException(String message, Throwable cause) {
    super(message, cause);
  }
}
