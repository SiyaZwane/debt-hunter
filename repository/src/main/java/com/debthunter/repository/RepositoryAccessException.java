package com.debthunter.repository;

/** Wraps a low-level VCS access failure (e.g. an {@link java.io.IOException} from JGit). */
public final class RepositoryAccessException extends RuntimeException {

  /**
   * Creates the exception with a message and cause.
   *
   * @param message a human-readable description of what failed
   * @param cause the underlying cause
   */
  public RepositoryAccessException(String message, Throwable cause) {
    super(message, cause);
  }
}
