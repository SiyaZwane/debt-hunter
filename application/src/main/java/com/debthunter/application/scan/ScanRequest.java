package com.debthunter.application.scan;

import com.debthunter.engine.spi.AnalysisEngine;
import com.debthunter.engine.spi.AnalysisMode;
import com.debthunter.repository.HistoryWindow;
import java.nio.file.Path;
import java.util.List;
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
    String failOn,
    Path baselinePath) {

  /** Validates required fields and defensively copies {@code engines}. */
  public ScanRequest {
    Objects.requireNonNull(repoPath, "repoPath");
    Objects.requireNonNull(outputDir, "outputDir");
    Objects.requireNonNull(mode, "mode");
    engines = engines == null ? List.of() : List.copyOf(engines);
  }
}
