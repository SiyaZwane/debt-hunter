package com.debthunter.engine.spi;

/** Lets a long-running {@link AnalysisEngine} report progress back to its caller. */
public interface ProgressSink {

  /** A sink that discards every report; the default when no caller cares about progress. */
  ProgressSink NO_OP = (message, progress) -> {};

  /**
   * Reports progress on the current analysis.
   *
   * @param message a short human-readable description of the current step
   * @param progress completion fraction in the range [0.0, 1.0]
   */
  void report(String message, double progress);
}
