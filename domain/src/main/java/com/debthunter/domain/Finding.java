package com.debthunter.domain;

import java.util.Map;
import java.util.Objects;

/** A single instance of technical debt detected by an analysis engine. */
public record Finding(
    String id,
    String ruleId,
    Category category,
    Severity severity,
    double confidence,
    String path,
    int startLine,
    String message,
    Map<String, Object> evidence,
    double score,
    boolean isNew,
    String fingerprint) {

  /** Canonical constructor: validates required fields and defensively copies the evidence map. */
  public Finding {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(ruleId, "ruleId");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(fingerprint, "fingerprint");
    evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
  }

  /**
   * A typed view over this finding's evidence map.
   *
   * @return the evidence, wrapped for typed accessors
   */
  public Evidence evidenceView() {
    return Evidence.of(evidence);
  }

  /**
   * Creates a new builder for constructing a {@link Finding}.
   *
   * @return a fresh, empty builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Mutable builder for {@link Finding}, since records have no fluent constructor. */
  public static final class Builder {
    private String id;
    private String ruleId;
    private Category category;
    private Severity severity;
    private double confidence;
    private String path;
    private int startLine;
    private String message;
    private Map<String, Object> evidence = Map.of();
    private double score;
    private boolean isNew;
    private String fingerprint;

    private Builder() {}

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder ruleId(String ruleId) {
      this.ruleId = ruleId;
      return this;
    }

    public Builder category(Category category) {
      this.category = category;
      return this;
    }

    public Builder severity(Severity severity) {
      this.severity = severity;
      return this;
    }

    public Builder confidence(double confidence) {
      this.confidence = confidence;
      return this;
    }

    public Builder path(String path) {
      this.path = path;
      return this;
    }

    public Builder startLine(int startLine) {
      this.startLine = startLine;
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public Builder evidence(Map<String, Object> evidence) {
      this.evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
      return this;
    }

    public Builder score(double score) {
      this.score = score;
      return this;
    }

    public Builder isNew(boolean isNew) {
      this.isNew = isNew;
      return this;
    }

    public Builder fingerprint(String fingerprint) {
      this.fingerprint = fingerprint;
      return this;
    }

    /**
     * Builds the {@link Finding}, validating required fields via the canonical constructor.
     *
     * @return the constructed, immutable finding
     */
    public Finding build() {
      return new Finding(
          id,
          ruleId,
          category,
          severity,
          confidence,
          path,
          startLine,
          message,
          evidence,
          score,
          isNew,
          fingerprint);
    }
  }
}
