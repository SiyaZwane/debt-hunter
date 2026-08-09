package com.debthunter.engine.codemaat;

import com.debthunter.domain.Category;
import com.debthunter.domain.DebtMetric;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Fingerprinter;
import com.debthunter.domain.Severity;
import com.debthunter.engine.spi.SymbolAnchorExtractor;
import com.debthunter.repository.RenameTracker;
import java.nio.file.Path;
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
 *
 * <p>Every fingerprint is anchored to a file's canonical path identity (via {@link RenameTracker},
 * resolved from {@code git log --follow}) rather than its raw current path, so a rename alone does
 * not change a finding's identity across scans. Code Maat has no symbol-level granularity, so
 * {@link SymbolAnchorExtractor#NONE} is used throughout.
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

  private final RenameTracker renameTracker;
  private final Fingerprinter fingerprinter;

  public CodeMaatFindingMapper() {
    this(new RenameTracker(), new Fingerprinter());
  }

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "collaborators are stateless services, shared by reference intentionally")
  public CodeMaatFindingMapper(RenameTracker renameTracker, Fingerprinter fingerprinter) {
    this.renameTracker = renameTracker;
    this.fingerprinter = fingerprinter;
  }

  /**
   * Maps {@code revisions} rows to churn findings, escalating to an additional hotspot finding for
   * files that clear the higher {@link #HOTSPOT_MIN_REVISIONS} bar.
   *
   * @param repoPath the repository's working-tree path, used to resolve rename history
   * @param rows the parsed {@code revisions} rows
   * @return churn and hotspot findings, most severe signal per file included
   */
  public List<Finding> mapRevisions(Path repoPath, List<RevisionsRow> rows) {
    List<Finding> findings = new ArrayList<>();
    for (RevisionsRow row : rows) {
      if (row.revisions() < CHURN_MIN_REVISIONS) {
        continue;
      }
      String canonicalPath = renameTracker.canonicalPath(repoPath, row.entity());
      findings.add(churnFinding(row, canonicalPath));
      if (row.revisions() >= HOTSPOT_MIN_REVISIONS) {
        findings.add(hotspotFinding(row, canonicalPath));
      }
    }
    return findings;
  }

  /**
   * Maps {@code coupling} rows to temporal-coupling findings for pairs at or above {@link
   * #COUPLING_MIN_DEGREE}.
   *
   * @param repoPath the repository's working-tree path, used to resolve rename history
   * @param rows the parsed {@code coupling} rows
   * @return one finding per qualifying coupled pair
   */
  public List<Finding> mapCoupling(Path repoPath, List<CouplingRow> rows) {
    List<Finding> findings = new ArrayList<>();
    for (CouplingRow row : rows) {
      if (row.degree() < COUPLING_MIN_DEGREE) {
        continue;
      }
      String canonicalEntity = renameTracker.canonicalPath(repoPath, row.entity());
      String canonicalCoupled = renameTracker.canonicalPath(repoPath, row.coupled());
      findings.add(couplingFinding(row, canonicalEntity, canonicalCoupled));
    }
    return findings;
  }

  /**
   * Maps {@code authors} rows to knowledge-concentration findings for files with at most {@link
   * #KNOWLEDGE_MAX_AUTHORS} contributors and at least {@link #KNOWLEDGE_MIN_REVISIONS} revisions.
   *
   * @param repoPath the repository's working-tree path, used to resolve rename history
   * @param rows the parsed {@code authors} rows
   * @return one finding per qualifying file
   */
  public List<Finding> mapAuthors(Path repoPath, List<AuthorsRow> rows) {
    List<Finding> findings = new ArrayList<>();
    for (AuthorsRow row : rows) {
      if (row.authors() > KNOWLEDGE_MAX_AUTHORS || row.revisions() < KNOWLEDGE_MIN_REVISIONS) {
        continue;
      }
      String canonicalPath = renameTracker.canonicalPath(repoPath, row.entity());
      findings.add(knowledgeConcentrationFinding(row, canonicalPath));
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

  private Finding churnFinding(RevisionsRow row, String canonicalPath) {
    Severity severity =
        row.revisions() >= 20
            ? Severity.HIGH
            : row.revisions() >= 10 ? Severity.MEDIUM : Severity.LOW;
    String ruleId = "codemaat.churn";
    return Finding.builder()
        .id(ruleId + ":" + row.entity())
        .ruleId(ruleId)
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
        .fingerprint(fingerprint(ruleId, canonicalPath, ""))
        .build();
  }

  private Finding hotspotFinding(RevisionsRow row, String canonicalPath) {
    Severity severity = row.revisions() >= 30 ? Severity.CRITICAL : Severity.HIGH;
    String ruleId = "codemaat.hotspot";
    return Finding.builder()
        .id(ruleId + ":" + row.entity())
        .ruleId(ruleId)
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
        .fingerprint(fingerprint(ruleId, canonicalPath, ""))
        .build();
  }

  private Finding couplingFinding(
      CouplingRow row, String canonicalEntity, String canonicalCoupled) {
    Severity severity = row.degree() >= 80 ? Severity.HIGH : Severity.MEDIUM;
    String ruleId = "codemaat.temporal-coupling";
    return Finding.builder()
        .id(ruleId + ":" + row.entity() + "->" + row.coupled())
        .ruleId(ruleId)
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
        .fingerprint(fingerprint(ruleId, canonicalEntity, canonicalCoupled))
        .build();
  }

  private Finding knowledgeConcentrationFinding(AuthorsRow row, String canonicalPath) {
    Severity severity = row.revisions() >= 10 ? Severity.HIGH : Severity.MEDIUM;
    String ruleId = "codemaat.knowledge-concentration";
    return Finding.builder()
        .id(ruleId + ":" + row.entity())
        .ruleId(ruleId)
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
        .fingerprint(fingerprint(ruleId, canonicalPath, ""))
        .build();
  }

  /**
   * Fingerprints file-scoped Code Maat findings: no symbol anchor is derivable ({@link
   * SymbolAnchorExtractor#NONE}), so identity rests entirely on the rule, the canonical path, and
   * any extra identity-bearing evidence (e.g. a coupled path) passed as {@code evidenceKey}.
   */
  private String fingerprint(String ruleId, String canonicalPath, String evidenceKey) {
    String anchor = SymbolAnchorExtractor.NONE.anchorFor(Path.of(canonicalPath), 0).orElse("");
    return fingerprinter.fingerprint(ruleId, canonicalPath, anchor, evidenceKey);
  }

  private double confidence(int signal, double scaleAtFullConfidence) {
    return Math.max(0.3, Math.min(1.0, signal / scaleAtFullConfidence));
  }
}
