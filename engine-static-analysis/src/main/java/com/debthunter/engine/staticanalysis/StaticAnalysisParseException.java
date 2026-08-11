package com.debthunter.engine.staticanalysis;

/** Thrown when a SonarQube report cannot be parsed as a valid issues-search export. */
public final class StaticAnalysisParseException extends RuntimeException {

  public StaticAnalysisParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
