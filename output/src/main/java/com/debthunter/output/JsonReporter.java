package com.debthunter.output;

import com.debthunter.domain.Finding;
import com.debthunter.domain.ScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Writes the native, schema-versioned JSON findings report. */
public final class JsonReporter {

  /** The file name this reporter writes within a scan's output directory. */
  public static final String FILE_NAME = "debt-hunter.json";

  private final ObjectMapper objectMapper;

  /** Creates a reporter using the shared {@link DeterministicObjectMapper}. */
  public JsonReporter() {
    this(DeterministicObjectMapper.create());
  }

  /**
   * Creates a reporter using a caller-supplied mapper, for testing.
   *
   * @param objectMapper the mapper to serialise with
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "ObjectMapper is a shared, reusable configuration object, not owned state")
  public JsonReporter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Writes {@code scanResult} as {@value #FILE_NAME} under {@code outputDir}, sorting findings by
   * rule id, then path, then start line for deterministic output.
   *
   * @param scanResult the result to write
   * @param outputDir the directory to write into; created if it does not exist
   * @return the path of the file written
   */
  public Path write(ScanResult scanResult, Path outputDir) {
    Path target = outputDir.resolve(FILE_NAME);
    try {
      Files.createDirectories(outputDir);
      objectMapper.writeValue(target.toFile(), withSortedFindings(scanResult));
      return target;
    } catch (IOException e) {
      throw new ReportWriteException("Failed to write " + target, e);
    }
  }

  private ScanResult withSortedFindings(ScanResult scanResult) {
    List<Finding> sorted =
        scanResult.findings().stream()
            .sorted(
                Comparator.comparing(Finding::ruleId)
                    .thenComparing(Finding::path)
                    .thenComparingInt(Finding::startLine))
            .toList();
    return new ScanResult(scanResult.run(), sorted, scanResult.metrics(), scanResult.policy());
  }
}
