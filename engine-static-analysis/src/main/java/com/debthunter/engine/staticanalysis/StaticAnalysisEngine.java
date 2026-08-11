package com.debthunter.engine.staticanalysis;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.engine.spi.AnalysisEngine;
import com.debthunter.engine.spi.AnalysisRequest;
import com.debthunter.engine.spi.CostClass;
import com.debthunter.engine.spi.EngineDescriptor;
import com.debthunter.engine.spi.EngineResult;
import com.debthunter.engine.spi.ProgressSink;
import com.debthunter.engine.spi.RepositoryContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Adapts a pre-existing SonarQube issues-search export at {@value #REPORT_FILE_NAME}, at the
 * repository root, into canonical findings. Never runs SonarQube itself and never re-analyses
 * anything SonarQube already reported; a repository with no report file simply has nothing to
 * adapt.
 */
public final class StaticAnalysisEngine implements AnalysisEngine {

  /** The pre-existing SonarQube report this engine reads, resolved at the repository root. */
  public static final String REPORT_FILE_NAME = "sonar-report.json";

  private static final String ENGINE_ID = "static-analysis";
  private static final String ENGINE_VERSION = "1.0.0";

  private final StaticAnalysisAdapter adapter;

  /** Creates the engine with the default adapter. */
  public StaticAnalysisEngine() {
    this(new StaticAnalysisAdapter());
  }

  /**
   * Creates the engine with an explicit adapter, for testing.
   *
   * @param adapter parses and maps the SonarQube report
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "the adapter is a stateless service, shared by reference intentionally")
  public StaticAnalysisEngine(StaticAnalysisAdapter adapter) {
    this.adapter = adapter;
  }

  @Override
  public EngineDescriptor descriptor() {
    return new EngineDescriptor(
        ENGINE_ID, ENGINE_VERSION, List.of(Category.STATIC_ANALYSIS), CostClass.LOW);
  }

  @Override
  public boolean supports(RepositoryContext context) {
    return true;
  }

  @Override
  public EngineResult analyse(AnalysisRequest request, ProgressSink sink) {
    long start = System.currentTimeMillis();
    Path reportFile = request.repoPath().resolve(REPORT_FILE_NAME);
    if (!Files.exists(reportFile)) {
      return EngineResult.ok(List.of(), List.of(), elapsed(start));
    }

    sink.report("Adapting SonarQube report", 0.5);
    List<Finding> findings;
    try {
      findings = adapter.parse(Files.readString(reportFile, StandardCharsets.UTF_8));
    } catch (IOException e) {
      return EngineResult.failed(
          "Failed to read " + REPORT_FILE_NAME + ": " + e.getMessage(), elapsed(start));
    } catch (StaticAnalysisParseException e) {
      return EngineResult.failed(
          "Failed to parse " + REPORT_FILE_NAME + ": " + e.getMessage(), elapsed(start));
    }
    sink.report("SonarQube report adapted", 1.0);
    return EngineResult.ok(findings, List.of(), elapsed(start));
  }

  private long elapsed(long start) {
    return System.currentTimeMillis() - start;
  }
}
