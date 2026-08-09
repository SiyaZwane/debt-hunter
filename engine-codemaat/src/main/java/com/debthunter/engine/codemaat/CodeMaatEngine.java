package com.debthunter.engine.codemaat;

import com.debthunter.domain.Category;
import com.debthunter.domain.DebtMetric;
import com.debthunter.domain.EngineHealth;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.engine.spi.AnalysisEngine;
import com.debthunter.engine.spi.AnalysisRequest;
import com.debthunter.engine.spi.CostClass;
import com.debthunter.engine.spi.EngineDescriptor;
import com.debthunter.engine.spi.EngineResult;
import com.debthunter.engine.spi.ProgressSink;
import com.debthunter.engine.spi.RepositoryContext;
import com.debthunter.engine.spi.VcsType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs Code Maat as an isolated subprocess and maps its CSV output to canonical findings. Code Maat
 * itself is never a compile-time dependency of this module — see {@code CodeMaatLogWriter} for how
 * the input log is built without a native {@code git}, and the absence of any Code Maat/Clojure
 * class on this module's classpath, verified by {@code AC10_CoreVariantNoCodeMaatTest}.
 */
public final class CodeMaatEngine implements AnalysisEngine {

  private static final String ENGINE_ID = "code-maat";
  private static final String ENGINE_VERSION = "1.0.0";
  private static final List<String> ANALYSIS_TYPES =
      List.of("revisions", "coupling", "age", "authors");

  private final Path codeMaatExecutable;
  private final CodeMaatLogWriter logWriter;
  private final CodeMaatOutputParser outputParser;
  private final CodeMaatFindingMapper findingMapper;

  /**
   * Creates the engine, resolving the Code Maat executable from the {@code CODEMAAT_EXECUTABLE}
   * environment variable, falling back to {@code /opt/code-maat/code-maat} if unset.
   */
  public CodeMaatEngine() {
    this(defaultExecutablePath());
  }

  /**
   * Creates the engine with an explicit executable path, for production configuration or tests.
   *
   * @param codeMaatExecutable path to a Code Maat launcher (a script or binary accepting {@code -l
   *     <log> -c git2 -a <analysis>}); need not exist until {@link #analyse} is called
   */
  public CodeMaatEngine(Path codeMaatExecutable) {
    this(
        codeMaatExecutable,
        new CodeMaatLogWriter(),
        new CodeMaatOutputParser(),
        new CodeMaatFindingMapper());
  }

  /**
   * Creates the engine with explicit collaborators, for testing.
   *
   * @param codeMaatExecutable path to a Code Maat launcher
   * @param logWriter builds the git2-format log Code Maat reads
   * @param outputParser parses Code Maat's CSV output
   * @param findingMapper maps parsed rows to canonical findings and metrics
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "collaborators are stateless services, shared by reference intentionally")
  public CodeMaatEngine(
      Path codeMaatExecutable,
      CodeMaatLogWriter logWriter,
      CodeMaatOutputParser outputParser,
      CodeMaatFindingMapper findingMapper) {
    this.codeMaatExecutable = codeMaatExecutable;
    this.logWriter = logWriter;
    this.outputParser = outputParser;
    this.findingMapper = findingMapper;
  }

  private static Path defaultExecutablePath() {
    String configured = System.getenv("CODEMAAT_EXECUTABLE");
    return Path.of(configured != null ? configured : "/opt/code-maat/code-maat");
  }

  @Override
  public EngineDescriptor descriptor() {
    return new EngineDescriptor(
        ENGINE_ID,
        ENGINE_VERSION,
        List.of(
            Category.HOTSPOT,
            Category.TEMPORAL_COUPLING,
            Category.CHURN,
            Category.KNOWLEDGE_CONCENTRATION),
        CostClass.HIGH);
  }

  @Override
  public boolean supports(RepositoryContext context) {
    return context.vcsType() == VcsType.GIT && context.historyDepth() != HistoryDepth.SHALLOW;
  }

  @Override
  public EngineResult analyse(AnalysisRequest request, ProgressSink sink) {
    long start = System.currentTimeMillis();

    if (!Files.isExecutable(codeMaatExecutable)) {
      return EngineResult.failed(
          "Code Maat engine binary not found: " + codeMaatExecutable, elapsed(start));
    }

    Path logFile;
    try {
      logFile = Files.createTempFile("debt-hunter-codemaat-", ".log");
      logWriter.writeLog(request.repoPath(), logFile);
    } catch (RuntimeException | IOException e) {
      return EngineResult.failed(
          "Failed to build Code Maat log: " + e.getMessage(), elapsed(start));
    }

    try {
      List<Finding> findings = new ArrayList<>();
      Map<String, DebtMetric> metrics = new java.util.LinkedHashMap<>();
      List<String> failures = new ArrayList<>();
      List<String> degradations = new ArrayList<>();
      int successCount = 0;

      for (String analysisType : ANALYSIS_TYPES) {
        sink.report("Running code-maat -a " + analysisType, progressFraction(analysisType));
        AnalysisOutcome outcome = runAnalysis(analysisType, logFile, request.timeout());
        switch (outcome.status()) {
          case FAILED -> failures.add(analysisType + ": " + outcome.reason());
          case DEGRADED -> {
            degradations.add(analysisType + ": " + outcome.reason());
            successCount++;
          }
          case OK -> successCount++;
        }
        collect(analysisType, outcome, request.repoPath(), findings, metrics);
      }

      if (successCount == 0) {
        return EngineResult.failed(
            "All Code Maat analyses failed: " + String.join("; ", failures), elapsed(start));
      }
      if (!degradations.isEmpty()) {
        return EngineResult.degraded(
            findings,
            List.copyOf(metrics.values()),
            String.join("; ", degradations),
            elapsed(start));
      }
      return EngineResult.ok(findings, List.copyOf(metrics.values()), elapsed(start));
    } finally {
      deleteQuietly(logFile);
    }
  }

  private void collect(
      String analysisType,
      AnalysisOutcome outcome,
      Path repoPath,
      List<Finding> findings,
      Map<String, DebtMetric> metrics) {
    switch (analysisType) {
      case "revisions" ->
          findings.addAll(findingMapper.mapRevisions(repoPath, outcome.revisionsRows()));
      case "coupling" ->
          findings.addAll(findingMapper.mapCoupling(repoPath, outcome.couplingRows()));
      case "authors" -> findings.addAll(findingMapper.mapAuthors(repoPath, outcome.authorsRows()));
      case "age" -> metrics.putAll(findingMapper.mapAge(outcome.ageRows()));
      default -> throw new IllegalStateException("Unknown analysis type: " + analysisType);
    }
  }

  private double progressFraction(String analysisType) {
    return (double) (ANALYSIS_TYPES.indexOf(analysisType) + 1) / ANALYSIS_TYPES.size();
  }

  private AnalysisOutcome runAnalysis(
      String analysisType, Path logFile, java.time.Duration timeout) {
    List<String> command =
        List.of(
            codeMaatExecutable.toString(),
            "-l",
            logFile.toString(),
            "-c",
            "git2",
            "-a",
            analysisType);
    Process process;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(false).start();
    } catch (IOException e) {
      return AnalysisOutcome.failed("Failed to start Code Maat: " + e.getMessage());
    }

    // stdout and stderr must be drained concurrently, not sequentially: if either pipe's buffer
    // fills while we're blocked reading only the other one, the child blocks on its write and we
    // deadlock waiting for it to exit.
    String stdout;
    String stderr;
    boolean finished;
    ExecutorService streamReaders = Executors.newFixedThreadPool(2);
    try {
      Future<String> stdoutFuture = streamReaders.submit(() -> readAll(process.getInputStream()));
      Future<String> stderrFuture = streamReaders.submit(() -> readAll(process.getErrorStream()));
      finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      stdout = finished ? stdoutFuture.get() : "";
      stderr = finished ? stderrFuture.get() : "";
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      return AnalysisOutcome.failed("Interrupted while waiting for Code Maat");
    } catch (ExecutionException e) {
      process.destroyForcibly();
      return AnalysisOutcome.failed("Failed to read Code Maat output: " + e.getCause());
    } finally {
      streamReaders.shutdownNow();
    }

    if (!finished) {
      process.destroyForcibly();
      return AnalysisOutcome.failed("Code Maat timed out after " + timeout);
    }
    if (process.exitValue() != 0) {
      return AnalysisOutcome.failed(
          "Code Maat exited with code " + process.exitValue() + ": " + stderr.strip());
    }

    try {
      return parse(analysisType, stdout);
    } catch (CodeMaatParseException e) {
      return AnalysisOutcome.degraded(e.getMessage());
    }
  }

  private AnalysisOutcome parse(String analysisType, String stdout) {
    return switch (analysisType) {
      case "revisions" -> AnalysisOutcome.ok(outputParser.parseRevisions(stdout));
      case "coupling" -> AnalysisOutcome.okCoupling(outputParser.parseCoupling(stdout));
      case "age" -> AnalysisOutcome.okAge(outputParser.parseAge(stdout));
      case "authors" -> AnalysisOutcome.okAuthors(outputParser.parseAuthors(stdout));
      default -> throw new IllegalStateException("Unknown analysis type: " + analysisType);
    };
  }

  private String readAll(java.io.InputStream stream) throws IOException {
    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
  }

  private long elapsed(long start) {
    return System.currentTimeMillis() - start;
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // best-effort cleanup of a transient working file
    }
  }

  /** The result of running one Code Maat analysis type: success, degraded, or failed. */
  private record AnalysisOutcome(
      EngineHealth status,
      String reason,
      List<RevisionsRow> revisionsRows,
      List<CouplingRow> couplingRows,
      List<AgeRow> ageRows,
      List<AuthorsRow> authorsRows) {

    static AnalysisOutcome ok(List<RevisionsRow> rows) {
      return new AnalysisOutcome(EngineHealth.OK, null, rows, List.of(), List.of(), List.of());
    }

    static AnalysisOutcome okCoupling(List<CouplingRow> rows) {
      return new AnalysisOutcome(EngineHealth.OK, null, List.of(), rows, List.of(), List.of());
    }

    static AnalysisOutcome okAge(List<AgeRow> rows) {
      return new AnalysisOutcome(EngineHealth.OK, null, List.of(), List.of(), rows, List.of());
    }

    static AnalysisOutcome okAuthors(List<AuthorsRow> rows) {
      return new AnalysisOutcome(EngineHealth.OK, null, List.of(), List.of(), List.of(), rows);
    }

    static AnalysisOutcome degraded(String reason) {
      return new AnalysisOutcome(
          EngineHealth.DEGRADED, reason, List.of(), List.of(), List.of(), List.of());
    }

    static AnalysisOutcome failed(String reason) {
      return new AnalysisOutcome(
          EngineHealth.FAILED, reason, List.of(), List.of(), List.of(), List.of());
    }
  }
}
