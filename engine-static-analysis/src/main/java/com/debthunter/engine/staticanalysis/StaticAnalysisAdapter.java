package com.debthunter.engine.staticanalysis;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Fingerprinter;
import com.debthunter.domain.Severity;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

/**
 * Adapts a SonarQube issues-search export ({@code {"issues":[...]}}) into canonical {@link
 * Finding}s. An adapter, not a re-analyser: every issue it produces was already computed by
 * SonarQube, not recomputed here.
 */
public final class StaticAnalysisAdapter {

  private static final String ENGINE_ID = "static-analysis";

  private final ObjectMapper objectMapper;
  private final Fingerprinter fingerprinter;

  /** Creates the adapter with a default, unconfigured {@link ObjectMapper}. */
  public StaticAnalysisAdapter() {
    this(new ObjectMapper(), new Fingerprinter());
  }

  /**
   * Creates the adapter with explicit collaborators, for testing.
   *
   * @param objectMapper deserialises the report JSON
   * @param fingerprinter computes stable finding fingerprints
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "collaborators are stateless services, shared by reference intentionally")
  public StaticAnalysisAdapter(ObjectMapper objectMapper, Fingerprinter fingerprinter) {
    this.objectMapper = objectMapper;
    this.fingerprinter = fingerprinter;
  }

  /**
   * Parses a SonarQube issues-search export and maps every issue to a canonical finding.
   *
   * @param json the report's JSON text
   * @return one finding per issue, in report order
   * @throws StaticAnalysisParseException if the JSON is malformed or does not conform to the
   *     expected shape
   */
  public List<Finding> parse(String json) {
    SonarIssuesReport report;
    try {
      report =
          objectMapper
              .copy()
              .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
              .readValue(json, SonarIssuesReport.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new StaticAnalysisParseException(
          "Malformed SonarQube report JSON: " + e.getMessage(), e);
    }
    return report.issues().stream().map(this::toFinding).toList();
  }

  private Finding toFinding(SonarIssue issue) {
    String ruleId = "sonar." + issue.rule();
    String path = relativePath(issue.component());
    int line = issue.line() == null ? 0 : issue.line();
    Severity severity = mapSeverity(issue.severity());
    return Finding.builder()
        .id(ENGINE_ID + ":" + issue.key())
        .ruleId(ruleId)
        .category(Category.STATIC_ANALYSIS)
        .severity(severity)
        .confidence(1.0)
        .path(path)
        .startLine(line)
        .message(issue.message())
        .evidence(
            Map.of(
                "sonarRule",
                issue.rule(),
                "sonarSeverity",
                issue.severity(),
                "type",
                issue.type() == null ? "" : issue.type(),
                "engine",
                ENGINE_ID))
        .score(1.0)
        .fingerprint(fingerprinter.fingerprint(ruleId, path, "", issue.key()))
        .build();
  }

  /**
   * Maps SonarQube's five-level severity scale to {@link Severity}: {@code BLOCKER} to {@link
   * Severity#CRITICAL}, {@code CRITICAL} to {@link Severity#HIGH}, {@code MAJOR} to {@link
   * Severity#MEDIUM}, {@code MINOR} to {@link Severity#LOW}, and {@code INFO} to {@link
   * Severity#INFO}. An unrecognised value maps to {@link Severity#MEDIUM} rather than failing the
   * whole report over one issue.
   */
  private Severity mapSeverity(String sonarSeverity) {
    if (sonarSeverity == null) {
      return Severity.MEDIUM;
    }
    return switch (sonarSeverity) {
      case "BLOCKER" -> Severity.CRITICAL;
      case "CRITICAL" -> Severity.HIGH;
      case "MAJOR" -> Severity.MEDIUM;
      case "MINOR" -> Severity.LOW;
      case "INFO" -> Severity.INFO;
      default -> Severity.MEDIUM;
    };
  }

  /** Strips SonarQube's {@code "<projectKey>:"} prefix from a component identifier. */
  private String relativePath(String component) {
    if (component == null) {
      return "";
    }
    int colon = component.indexOf(':');
    return colon >= 0 ? component.substring(colon + 1) : component;
  }
}
