package com.debthunter.engine.codemaat;

import com.debthunter.domain.Category;
import com.debthunter.domain.DebtMetric;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps Code Maat's raw analysis rows to canonical {@link Finding}s and {@link DebtMetric}s.
 *
 * <p>Code Maat reports revision counts, coupling degrees, and author counts, but not complexity or
 * line-level detail, so every finding here is file-scoped ({@code startLine} is always 0) and every
 * threshold below is a deliberately simple heuristic over that limited signal — not a substitute
 * for combining it with a complexity or static-analysis engine later.
 */
public final class CodeMaatFindingMapper {

  /** Minimum revision count for a file to be flagged as churning. */
  public static final int CHURN_MIN_REVISIONS = 3;

  /** Minimum revision count for a churning file to additionally be flagged as a hotspot. */
  public static final int HOTSPOT_MIN_REVISIONS = 10;

  /** Minimum coupling degree (percent of shared commits) to flag a file pair. */
  public static final int COUPLING_MIN_DEGREE = 50;

  /** Maximum distinct authors for a file to be flagged as knowledge-concentrated. */
  public static final int KNOWLEDGE_MAX_AUTHORS = 1;

  /** Minimum revision count for a knowledge-concentrated file to be worth flagging. */
  public static final int KNOWLEDGE_MIN_REVISIONS = 3;

  /**
   * Maps {@code revisions} rows to churn findings, escalating to an additional hotspot finding for
   * files that clear the higher {@link #HOTSPOT_MIN_REVISIONS} bar.
   *
   * @param rows the parsed {@code revisions} rows
   * @return churn and hotspot findings, most severe signal per file included
   */
  public List<Finding> mapRevisions(List<RevisionsRow> rows) {
    List<Finding> findings = new ArrayList<>();
    for (RevisionsRow row : rows) {
      if (row.revisions() < CHURN_MIN_REVISIONS) {
        continue;
      }
      findings.add(churnFinding(row));
      if (row.revisions() >= HOTSPOT_MIN_REVISIONS) {
        findings.add(hotspotFinding(row));
      }
    }
    return findings;
  }

  /**
   * Maps {@code coupling} rows to temporal-coupling findings for pairs at or above {@link
   * #COUPLING_MIN_DEGREE}.
   *
   * @param rows the parsed {@code coupling} rows
   * @return one finding per qualifying coupled pair
   */
  public List<Finding> mapCoupling(List<CouplingRow> rows) {
    List<Finding> findings = new ArrayList<>();
    for (CouplingRow row : rows) {
      if (row.degree() < COUPLING_MIN_DEGREE) {
        continue;
      }
      findings.add(couplingFinding(row));
    }
    return findings;
  }

  /**
   * Maps {@code authors} rows to knowledge-concentration findings for files with at most {@link
   * #KNOWLEDGE_MAX_AUTHORS} contributors and at least {@link #KNOWLEDGE_MIN_REVISIONS} revisions.
   *
   * @param rows the parsed {@code authors} rows
   * @return one finding per qualifying file
   */
  public List<Finding> mapAuthors(List<AuthorsRow> rows) {
    List<Finding> findings = new ArrayList<>();
    for (AuthorsRow row : rows) {
      if (row.authors() > KNOWLEDGE_MAX_AUTHORS || row.revisions() < KNOWLEDGE_MIN_REVISIONS) {
        continue;
      }
      findings.add(knowledgeConcentrationFinding(row));
    }
    return findings;
  }

  /**
   * Maps {@code age} rows to metrics. Code Maat's age signal alone (without a companion churn or
   * coupling signal) isn't a debt indicator by itself, so it's recorded as data, not a finding.
   *
   * @param rows the parsed {@code age} rows
   * @return one metric per file, keyed by {@code "age:<path>"}
   */
  public Map<String, DebtMetric> mapAge(List<AgeRow> rows) {
    Map<String, DebtMetric> metrics = new java.util.LinkedHashMap<>();
    for (AgeRow row : rows) {
      String key = "age:" + row.entity();
      metrics.put(key, new DebtMetric(key, row.ageMonths(), row.entity()));
    }
    return metrics;
  }

  private Finding churnFinding(RevisionsRow row) {
    Severity severity =
        row.revisions() >= 20
            ? Severity.HIGH
            : row.revisions() >= 10 ? Severity.MEDIUM : Severity.LOW;
    return Finding.builder()
        .id("codemaat.churn:" + row.entity())
        .ruleId("codemaat.churn")
        .category(Category.CHURN)
        .severity(severity)
        .confidence(confidence(row.revisions(), 30.0))
        .path(row.entity())
        .startLine(0)
        .message(row.entity() + " has changed " + row.revisions() + " times, indicating high churn")
        .evidence(
            Map.of(
                "changeFrequency",
                (double) row.revisions(),
                "engine",
                "code-maat",
                "calculation",
                "revisions >= " + CHURN_MIN_REVISIONS))
        .score(row.revisions())
        .fingerprint("codemaat.churn:" + row.entity())
        .build();
  }

  private Finding hotspotFinding(RevisionsRow row) {
    Severity severity = row.revisions() >= 30 ? Severity.CRITICAL : Severity.HIGH;
    return Finding.builder()
        .id("codemaat.hotspot:" + row.entity())
        .ruleId("codemaat.hotspot")
        .category(Category.HOTSPOT)
        .severity(severity)
        .confidence(confidence(row.revisions(), 30.0))
        .path(row.entity())
        .startLine(0)
        .message(row.entity() + " is a change hotspot (" + row.revisions() + " revisions)")
        .evidence(
            Map.of(
                "changeFrequency",
                (double) row.revisions(),
                "engine",
                "code-maat",
                "calculation",
                "revisions >= " + HOTSPOT_MIN_REVISIONS))
        .score(row.revisions())
        .fingerprint("codemaat.hotspot:" + row.entity())
        .build();
  }

  private Finding couplingFinding(CouplingRow row) {
    Severity severity = row.degree() >= 80 ? Severity.HIGH : Severity.MEDIUM;
    return Finding.builder()
        .id("codemaat.temporal-coupling:" + row.entity() + "->" + row.coupled())
        .ruleId("codemaat.temporal-coupling")
        .category(Category.TEMPORAL_COUPLING)
        .severity(severity)
        .confidence(confidence(row.averageRevs(), 20.0))
        .path(row.entity())
        .startLine(0)
        .message(
            row.entity()
                + " and "
                + row.coupled()
                + " change together in "
                + row.degree()
                + "% of commits")
        .evidence(
            Map.of(
                "coupled",
                row.coupled(),
                "degree",
                (double) row.degree(),
                "averageRevs",
                (double) row.averageRevs(),
                "engine",
                "code-maat"))
        .score(row.degree())
        .fingerprint("codemaat.temporal-coupling:" + row.entity() + "->" + row.coupled())
        .build();
  }

  private Finding knowledgeConcentrationFinding(AuthorsRow row) {
    Severity severity = row.revisions() >= 10 ? Severity.HIGH : Severity.MEDIUM;
    return Finding.builder()
        .id("codemaat.knowledge-concentration:" + row.entity())
        .ruleId("codemaat.knowledge-concentration")
        .category(Category.KNOWLEDGE_CONCENTRATION)
        .severity(severity)
        .confidence(confidence(row.revisions(), 15.0))
        .path(row.entity())
        .startLine(0)
        .message(
            row.entity()
                + " has been modified by only "
                + row.authors()
                + " author(s) across "
                + row.revisions()
                + " revisions")
        .evidence(
            Map.of(
                "authorCount", (double) row.authors(),
                "changeFrequency", (double) row.revisions(),
                "engine", "code-maat"))
        .score(row.revisions())
        .fingerprint("codemaat.knowledge-concentration:" + row.entity())
        .build();
  }

  private double confidence(int signal, double scaleAtFullConfidence) {
    return Math.max(0.3, Math.min(1.0, signal / scaleAtFullConfidence));
  }
}
