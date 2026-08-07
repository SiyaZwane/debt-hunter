package com.debthunter.engine.spi;

/** A pluggable source of technical-debt findings, run once per scan. */
public interface AnalysisEngine {

  /**
   * This engine's static identity and capabilities.
   *
   * @return the descriptor, never {@code null}
   */
  EngineDescriptor descriptor();

  /**
   * Whether this engine can meaningfully analyse the given repository.
   *
   * @param context the repository's characteristics
   * @return {@code true} if {@link #analyse} should be called for this repository
   */
  boolean supports(RepositoryContext context);

  /**
   * Analyses the repository described by {@code request}.
   *
   * @param request what to analyse and under what constraints
   * @param sink where to report progress; never {@code null}, may be {@link ProgressSink#NO_OP}
   * @return the result, never {@code null}
   */
  EngineResult analyse(AnalysisRequest request, ProgressSink sink);
}
