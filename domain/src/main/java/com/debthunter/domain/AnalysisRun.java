package com.debthunter.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable metadata describing one execution of the analyser. */
public record AnalysisRun(
    String id,
    String toolVersion,
    String imageDigest,
    Instant timestamp,
    String repository,
    String project,
    String commit,
    String baseCommit,
    String branch,
    String pullRequest,
    HistoryDepth historyDepth,
    List<EngineStatus> engines,
    boolean degraded) {

  /** Validates required fields and defensively copies {@code engines}. */
  public AnalysisRun {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(toolVersion, "toolVersion");
    Objects.requireNonNull(timestamp, "timestamp");
    Objects.requireNonNull(repository, "repository");
    Objects.requireNonNull(commit, "commit");
    Objects.requireNonNull(historyDepth, "historyDepth");
    engines = engines == null ? List.of() : List.copyOf(engines);
  }

  /**
   * Creates a new builder for constructing an {@link AnalysisRun}.
   *
   * @return a fresh, empty builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Mutable builder for {@link AnalysisRun}. The {@code degraded} flag is not settable directly: it
   * is always derived from whether any engine in {@link #engines(List)} finished with a non-{@link
   * EngineHealth#OK} status, so it can never drift from the data it summarises.
   */
  public static final class Builder {
    private String id;
    private String toolVersion;
    private String imageDigest;
    private Instant timestamp;
    private String repository;
    private String project;
    private String commit;
    private String baseCommit;
    private String branch;
    private String pullRequest;
    private HistoryDepth historyDepth;
    private List<EngineStatus> engines = List.of();

    private Builder() {}

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder toolVersion(String toolVersion) {
      this.toolVersion = toolVersion;
      return this;
    }

    public Builder imageDigest(String imageDigest) {
      this.imageDigest = imageDigest;
      return this;
    }

    public Builder timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder repository(String repository) {
      this.repository = repository;
      return this;
    }

    public Builder project(String project) {
      this.project = project;
      return this;
    }

    public Builder commit(String commit) {
      this.commit = commit;
      return this;
    }

    public Builder baseCommit(String baseCommit) {
      this.baseCommit = baseCommit;
      return this;
    }

    public Builder branch(String branch) {
      this.branch = branch;
      return this;
    }

    public Builder pullRequest(String pullRequest) {
      this.pullRequest = pullRequest;
      return this;
    }

    public Builder historyDepth(HistoryDepth historyDepth) {
      this.historyDepth = historyDepth;
      return this;
    }

    public Builder engines(List<EngineStatus> engines) {
      this.engines = engines == null ? List.of() : List.copyOf(engines);
      return this;
    }

    /**
     * Builds the {@link AnalysisRun}, computing {@code degraded} from {@link #engines}.
     *
     * @return the constructed, immutable run
     */
    public AnalysisRun build() {
      boolean computedDegraded =
          engines.stream().anyMatch(engine -> engine.status() != EngineHealth.OK);
      return new AnalysisRun(
          id,
          toolVersion,
          imageDigest,
          timestamp,
          repository,
          project,
          commit,
          baseCommit,
          branch,
          pullRequest,
          historyDepth,
          engines,
          computedDegraded);
    }
  }
}
