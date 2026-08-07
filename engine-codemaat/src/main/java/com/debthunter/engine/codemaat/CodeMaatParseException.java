package com.debthunter.engine.codemaat;

/** Code Maat's CSV output didn't match the shape this parser expects for the given analysis. */
public final class CodeMaatParseException extends RuntimeException {

  /**
   * Creates the exception with a message describing the mismatch.
   *
   * @param message a human-readable description of what didn't match
   */
  public CodeMaatParseException(String message) {
    super(message);
  }
}
