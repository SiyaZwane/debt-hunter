package com.debthunter.engine.architecture;

/** Thrown when {@code .debt-hunter-arch.yml} is malformed or does not conform to its shape. */
public final class ArchRuleParseException extends RuntimeException {

  public ArchRuleParseException(String message) {
    super(message);
  }

  public ArchRuleParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
