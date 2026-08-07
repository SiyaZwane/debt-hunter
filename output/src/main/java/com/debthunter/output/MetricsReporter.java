package com.debthunter.output;

import com.debthunter.domain.DebtMetric;
import com.debthunter.domain.ScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Writes the scan's debt metrics as a standalone JSON document. */
public final class MetricsReporter {

  /** The file name this reporter writes within a scan's output directory. */
  public static final String FILE_NAME = "metrics.json";

  private final ObjectMapper objectMapper;

  /** Creates a reporter using the shared {@link DeterministicObjectMapper}. */
  public MetricsReporter() {
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
  public MetricsReporter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Writes {@code scanResult}'s metrics as {@value #FILE_NAME} under {@code outputDir}.
   *
   * @param scanResult the result whose metrics should be written
   * @param outputDir the directory to write into; created if it does not exist
   * @return the path of the file written
   */
  public Path write(ScanResult scanResult, Path outputDir) {
    Path target = outputDir.resolve(FILE_NAME);
    try {
      Files.createDirectories(outputDir);
      Map<String, DebtMetric> metrics = scanResult.metrics();
      objectMapper.writeValue(target.toFile(), metrics);
      return target;
    } catch (IOException e) {
      throw new ReportWriteException("Failed to write " + target, e);
    }
  }
}
