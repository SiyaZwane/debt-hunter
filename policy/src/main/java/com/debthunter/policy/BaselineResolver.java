package com.debthunter.policy;

import com.debthunter.domain.BaselineArtifact;
import com.debthunter.output.BaselineSigner;
import com.debthunter.output.DeterministicObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the baseline to compare a scan against, following a fixed chain: an explicit path, then
 * a conventional local pipeline-cache path, then the control plane, then none.
 *
 * <p>The control-plane step is not implemented: that scope doesn't exist as a running service until
 * the control-plane module does (a later FR), so this resolver always falls through it to {@link
 * BaselineProvenance#NONE}.
 */
public final class BaselineResolver {

  private static final String PIPELINE_CACHE_ENV_VAR = "DEBTHUNTER_BASELINE_CACHE";
  private static final String DEFAULT_PIPELINE_CACHE_PATH = ".debt-hunter/baseline-cache.json";

  private final ObjectMapper objectMapper;
  private final BaselineSigner signer;
  private final String verificationKey;
  private final Path pipelineCachePath;

  /** Creates a resolver that trusts every baseline's signature (or lack of one). */
  public BaselineResolver() {
    this(null);
  }

  /**
   * Creates a resolver that rejects any baseline whose signature does not verify against {@code
   * verificationKey}, and rejects any unsigned baseline once a key is configured.
   *
   * @param verificationKey the key to verify signatures with, or {@code null} to skip verification
   */
  public BaselineResolver(String verificationKey) {
    this(
        DeterministicObjectMapper.create(),
        new BaselineSigner(),
        verificationKey,
        defaultPipelineCachePath());
  }

  /**
   * Creates a resolver with explicit collaborators, for testing.
   *
   * @param objectMapper the mapper to deserialise baselines with
   * @param signer recomputes a signature for verification
   * @param verificationKey the key to verify signatures with, or {@code null} to skip verification
   * @param pipelineCachePath the conventional local path to check when no explicit path is given
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "collaborators are stateless services, shared by reference intentionally")
  public BaselineResolver(
      ObjectMapper objectMapper,
      BaselineSigner signer,
      String verificationKey,
      Path pipelineCachePath) {
    this.objectMapper = objectMapper;
    this.signer = signer;
    this.verificationKey = verificationKey;
    this.pipelineCachePath = pipelineCachePath;
  }

  private static Path defaultPipelineCachePath() {
    String configured = System.getenv(PIPELINE_CACHE_ENV_VAR);
    return Path.of(configured != null ? configured : DEFAULT_PIPELINE_CACHE_PATH);
  }

  /**
   * Resolves the baseline to use, following the resolution chain.
   *
   * @param explicitPath an explicitly supplied baseline path, or {@code null}
   * @param currentToolVersion this build's tool version, for compatibility validation
   * @return the resolution: a usable baseline, an incompatible one, or none found
   */
  public BaselineResolution resolve(Path explicitPath, String currentToolVersion) {
    if (explicitPath != null) {
      return loadAndValidate(explicitPath, BaselineProvenance.EXPLICIT, currentToolVersion);
    }
    if (pipelineCachePath != null && Files.isRegularFile(pipelineCachePath)) {
      return loadAndValidate(
          pipelineCachePath, BaselineProvenance.PIPELINE_CACHE, currentToolVersion);
    }
    return BaselineResolution.none();
  }

  private BaselineResolution loadAndValidate(
      Path path, BaselineProvenance provenance, String currentToolVersion) {
    BaselineArtifact artifact;
    try {
      artifact = objectMapper.readValue(path.toFile(), BaselineArtifact.class);
    } catch (IOException e) {
      return BaselineResolution.incompatible(
          provenance, "Could not read baseline at " + path + ": " + e.getMessage());
    }

    if (!majorVersionsMatch(artifact.toolVersion(), currentToolVersion)) {
      return BaselineResolution.incompatible(
          provenance,
          "Baseline tool version "
              + artifact.toolVersion()
              + " is incompatible with current version "
              + currentToolVersion);
    }

    if (verificationKey != null) {
      if (artifact.signature() == null) {
        return BaselineResolution.incompatible(
            provenance,
            "Baseline at " + path + " is unsigned but a verification key is configured");
      }
      if (!verifySignature(artifact)) {
        return BaselineResolution.incompatible(
            provenance, "Baseline at " + path + " has an invalid signature");
      }
    }

    return BaselineResolution.resolved(provenance, artifact.scanResult());
  }

  private boolean majorVersionsMatch(String baselineVersion, String currentVersion) {
    return majorOf(baselineVersion).equals(majorOf(currentVersion));
  }

  private String majorOf(String version) {
    int dot = version.indexOf('.');
    return dot < 0 ? version : version.substring(0, dot);
  }

  private boolean verifySignature(BaselineArtifact artifact) {
    BaselineArtifact unsigned =
        new BaselineArtifact(
            artifact.schemaVersion(), artifact.toolVersion(), artifact.scanResult(), null);
    try {
      String expected = signer.sign(objectMapper.writeValueAsString(unsigned), verificationKey);
      return expected.equals(artifact.signature());
    } catch (JsonProcessingException e) {
      return false;
    }
  }
}
