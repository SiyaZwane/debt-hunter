package com.debthunter.application.scan;

import com.debthunter.domain.Severity;
import com.debthunter.engine.spi.AnalysisEngine;
import com.debthunter.engine.spi.AnalysisMode;
import com.debthunter.repository.HistoryWindow;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Everything {@link ScanUseCase} needs to run one scan. */
public record ScanRequest(
    Path repoPath,
    Path outputDir,
    AnalysisMode mode,
    String baseRef,
    Path policyPath,
    List<AnalysisEngine> engines,
    HistoryWindow historyWindow,
    boolean offline,
    Severity failOn,
    Path baselinePath,
    Map<String, String> projects) {

  /** Validates required fields and defensively copies {@code engines} and {@code projects}. */
  public ScanRequest {
    Objects.requireNonNull(repoPath, "repoPath");
    Objects.requireNonNull(outputDir, "outputDir");
    Objects.requireNonNull(mode, "mode");
    engines = engines == null ? List.of() : List.copyOf(engines);
    projects = projects == null ? Map.of() : Map.copyOf(projects);
  }

  /**
   * Convenience constructor for a single-project scan (no {@code --project} slicing), used by every
   * call site that predates monorepo project slicing.
   *
   * @param repoPath the repository to scan
   * @param outputDir directory to write report files into
   * @param mode analysis mode: FULL or PULL_REQUEST
   * @param baseRef base ref to compare against, for pull-request mode
   * @param policyPath path to a policy bundle file, or {@code null}
   * @param engines the analysis engines to run
   * @param historyWindow only consider commits at or after this point, or {@code null}
   * @param offline whether to skip network-dependent steps
   * @param failOn severity threshold that should fail the build in addition to the policy bundle's
   *     own rules, or {@code null}
   * @param baselinePath path to a baseline artefact to compare against, or {@code null}
   */
  public ScanRequest(
      Path repoPath,
      Path outputDir,
      AnalysisMode mode,
      String baseRef,
      Path policyPath,
      List<AnalysisEngine> engines,
      HistoryWindow historyWindow,
      boolean offline,
      Severity failOn,
      Path baselinePath) {
    this(
        repoPath,
        outputDir,
        mode,
        baseRef,
        policyPath,
        engines,
        historyWindow,
        offline,
        failOn,
        baselinePath,
        Map.of());
  }
}
