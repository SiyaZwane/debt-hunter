package com.debthunter.engine.spi;

/** Whether a scan is analysing the whole repository or just a pull request's changes. */
public enum AnalysisMode {
  FULL,
  PULL_REQUEST
}
