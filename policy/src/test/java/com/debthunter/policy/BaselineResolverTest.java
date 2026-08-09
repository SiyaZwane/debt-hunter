package com.debthunter.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.output.BaselineSigner;
import com.debthunter.output.BaselineWriter;
import com.debthunter.output.DeterministicObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaselineResolverTest {

  @TempDir private Path tempDir;

  @Test
  void anExplicitPathIsResolvedAsExplicitProvenance() {
    new BaselineWriter().write(scanResult(), tempDir);
    Path written = tempDir.resolve(BaselineWriter.FILE_NAME);

    BaselineResolution resolution = new BaselineResolver().resolve(written, "0.1.0-SNAPSHOT");

    assertThat(resolution.provenance()).isEqualTo(BaselineProvenance.EXPLICIT);
    assertThat(resolution.isIncompatible()).isFalse();
    assertThat(resolution.baseline()).isNotNull();
  }

  @Test
  void pipelineCacheIsUsedWhenNoExplicitPathIsGiven() {
    Path cachePath = tempDir.resolve("cache.json");
    new BaselineWriter().write(scanResult(), tempDir);
    try {
      Files.move(
          tempDir.resolve(BaselineWriter.FILE_NAME),
          cachePath,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
    BaselineResolver resolver =
        new BaselineResolver(
            DeterministicObjectMapper.create(), new BaselineSigner(), null, cachePath);

    BaselineResolution resolution = resolver.resolve(null, "0.1.0-SNAPSHOT");

    assertThat(resolution.provenance()).isEqualTo(BaselineProvenance.PIPELINE_CACHE);
    assertThat(resolution.baseline()).isNotNull();
  }

  @Test
  void noBaselineAnywhereResolvesToNone() {
    BaselineResolver resolver =
        new BaselineResolver(
            DeterministicObjectMapper.create(),
            new BaselineSigner(),
            null,
            tempDir.resolve("does-not-exist.json"));

    BaselineResolution resolution = resolver.resolve(null, "0.1.0-SNAPSHOT");

    assertThat(resolution.provenance()).isEqualTo(BaselineProvenance.NONE);
    assertThat(resolution.baseline()).isNull();
    assertThat(resolution.isIncompatible()).isFalse();
  }

  @Test
  void anIncompatibleMajorToolVersionIsRejected() {
    new BaselineWriter().write(scanResultWithToolVersion("1.0.0"), tempDir);
    Path written = tempDir.resolve(BaselineWriter.FILE_NAME);

    BaselineResolution resolution = new BaselineResolver().resolve(written, "2.0.0");

    assertThat(resolution.isIncompatible()).isTrue();
    assertThat(resolution.incompatibilityReason()).contains("incompatible");
  }

  @Test
  void aCompatibleMajorToolVersionAcrossDifferentMinorVersionsIsAccepted() {
    new BaselineWriter().write(scanResultWithToolVersion("1.2.0"), tempDir);
    Path written = tempDir.resolve(BaselineWriter.FILE_NAME);

    BaselineResolution resolution = new BaselineResolver().resolve(written, "1.9.0");

    assertThat(resolution.isIncompatible()).isFalse();
  }

  @Test
  void anUnreadableExplicitPathIsIncompatible() {
    Path missing = tempDir.resolve("missing.json");

    BaselineResolution resolution = new BaselineResolver().resolve(missing, "0.1.0-SNAPSHOT");

    assertThat(resolution.isIncompatible()).isTrue();
    assertThat(resolution.provenance()).isEqualTo(BaselineProvenance.EXPLICIT);
  }

  @Test
  void aValidSignatureIsAccepted() {
    new BaselineWriter("shared-secret").write(scanResult(), tempDir);
    Path written = tempDir.resolve(BaselineWriter.FILE_NAME);

    BaselineResolution resolution =
        new BaselineResolver("shared-secret").resolve(written, "0.1.0-SNAPSHOT");

    assertThat(resolution.isIncompatible()).isFalse();
    assertThat(resolution.baseline()).isNotNull();
  }

  @Test
  void anInvalidSignatureIsRejected() {
    new BaselineWriter("shared-secret").write(scanResult(), tempDir);
    Path written = tempDir.resolve(BaselineWriter.FILE_NAME);

    BaselineResolution resolution =
        new BaselineResolver("wrong-secret").resolve(written, "0.1.0-SNAPSHOT");

    assertThat(resolution.isIncompatible()).isTrue();
    assertThat(resolution.incompatibilityReason()).contains("signature");
  }

  @Test
  void anUnsignedBaselineIsRejectedWhenAVerificationKeyIsConfigured() {
    new BaselineWriter().write(scanResult(), tempDir);
    Path written = tempDir.resolve(BaselineWriter.FILE_NAME);

    BaselineResolution resolution =
        new BaselineResolver("shared-secret").resolve(written, "0.1.0-SNAPSHOT");

    assertThat(resolution.isIncompatible()).isTrue();
    assertThat(resolution.incompatibilityReason()).contains("unsigned");
  }

  private ScanResult scanResult() {
    return scanResultWithToolVersion("0.1.0-SNAPSHOT");
  }

  private ScanResult scanResultWithToolVersion(String toolVersion) {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("baseline-run")
            .toolVersion(toolVersion)
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    return new ScanResult(run, List.of(), Map.of(), PolicyResult.passed("unversioned"));
  }
}
