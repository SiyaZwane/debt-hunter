package com.debthunter.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Proves conformance by building a fixture once and scanning it with two different {@link
 * ScanInvoker}s — a "reference" (the environment presumed correct) and a "candidate" (the
 * environment under test, e.g. a different time zone, locale, or CPU architecture). Rather than a
 * fixed golden file that would need updating every time a finding rule changes, the reference run
 * itself is the golden output: if reference and candidate ever disagree once volatile fields are
 * masked out, something about the candidate environment leaked into the result.
 */
public final class ConformanceRunner {

  private final ScanInvoker reference;
  private final ScanInvoker candidate;

  /**
   * Creates a runner.
   *
   * @param reference the environment presumed correct
   * @param candidate the environment being checked for conformance against {@code reference}
   */
  public ConformanceRunner(ScanInvoker reference, ScanInvoker candidate) {
    this.reference = Objects.requireNonNull(reference, "reference");
    this.candidate = Objects.requireNonNull(candidate, "candidate");
  }

  /**
   * Runs one fixture through both invokers and compares their (masked) output. The fixture is built
   * exactly once and both invokers scan that same repository — only their output directories differ
   * — so a repository path recorded in the report can never itself be the source of a mismatch.
   *
   * @param fixture the fixture to build and scan
   * @param workDir a scratch directory this run may freely use and pollute
   * @return the comparison result
   * @throws Exception if either invoker fails, or its output cannot be read
   */
  public ConformanceResult run(ConformanceFixture fixture, Path workDir) throws Exception {
    Path repoDir = workDir.resolve("repo");
    try (FixtureRepoBuilder builder = FixtureRepoBuilder.initAt(repoDir)) {
      fixture.setup().accept(builder);
    }

    JsonNode referenceOutput = reference.scan(repoDir, workDir.resolve("reference-output"));
    JsonNode candidateOutput = candidate.scan(repoDir, workDir.resolve("candidate-output"));
    VolatileFieldMasker.mask(referenceOutput);
    VolatileFieldMasker.mask(candidateOutput);
    return new ConformanceResult(
        fixture.name(), referenceOutput.equals(candidateOutput), referenceOutput, candidateOutput);
  }

  /**
   * Runs every fixture in {@code fixtures} through {@link #run}.
   *
   * @param fixtures the fixtures to check
   * @param workDir a scratch directory this run may freely use and pollute; each fixture gets its
   *     own subdirectory
   * @return one result per fixture, in the same order
   * @throws Exception if any invoker fails, or its output cannot be read
   */
  public List<ConformanceResult> runAll(List<ConformanceFixture> fixtures, Path workDir)
      throws Exception {
    List<ConformanceResult> results = new ArrayList<>();
    for (ConformanceFixture fixture : fixtures) {
      results.add(run(fixture, workDir.resolve(fixture.name())));
    }
    return results;
  }
}
