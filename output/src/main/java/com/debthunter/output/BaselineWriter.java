package com.debthunter.output;

import com.debthunter.domain.BaselineArtifact;
import com.debthunter.domain.ScanResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serialises a {@link ScanResult} as a baseline artefact, signing it with a configurable key. With
 * no key configured, the artefact is written unsigned.
 */
public final class BaselineWriter {

  /** The file name this writer writes within a directory. */
  public static final String FILE_NAME = "baseline.json";

  private final ObjectMapper objectMapper;
  private final BaselineSigner signer;
  private final String signingKey;

  /** Creates a writer that writes unsigned baselines. */
  public BaselineWriter() {
    this(null);
  }

  /**
   * Creates a writer that signs every baseline it writes.
   *
   * @param signingKey the signing key, or {@code null} to write unsigned baselines
   */
  public BaselineWriter(String signingKey) {
    this(DeterministicObjectMapper.create(), new BaselineSigner(), signingKey);
  }

  /**
   * Creates a writer with explicit collaborators, for testing.
   *
   * @param objectMapper the mapper to serialise with
   * @param signer computes the signature
   * @param signingKey the signing key, or {@code null} to write unsigned baselines
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "collaborators are stateless services, shared by reference intentionally")
  public BaselineWriter(ObjectMapper objectMapper, BaselineSigner signer, String signingKey) {
    this.objectMapper = objectMapper;
    this.signer = signer;
    this.signingKey = signingKey;
  }

  /**
   * Writes {@code scanResult} as {@value #FILE_NAME} under {@code outputDir}.
   *
   * @param scanResult the result to snapshot
   * @param outputDir the directory to write into; created if it does not exist
   * @return the path of the file written
   * @throws ReportWriteException if the file cannot be written
   */
  public Path write(ScanResult scanResult, Path outputDir) {
    BaselineArtifact unsigned = BaselineArtifact.unsigned(scanResult);
    BaselineArtifact artifact =
        signingKey == null ? unsigned : unsigned.withSignature(sign(unsigned));

    Path target = outputDir.resolve(FILE_NAME);
    try {
      Files.createDirectories(outputDir);
      objectMapper.writeValue(target.toFile(), artifact);
      return target;
    } catch (IOException e) {
      throw new ReportWriteException("Failed to write " + target, e);
    }
  }

  private String sign(BaselineArtifact unsigned) {
    try {
      return signer.sign(objectMapper.writeValueAsString(unsigned), signingKey);
    } catch (JsonProcessingException e) {
      throw new ReportWriteException("Failed to serialise baseline for signing", e);
    }
  }
}
