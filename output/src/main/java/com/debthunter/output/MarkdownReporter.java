package com.debthunter.output;

import com.debthunter.domain.EngineHealth;
import com.debthunter.domain.EngineStatus;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.PolicyViolation;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.debthunter.domain.SuppressionEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Writes a human-readable Markdown summary of a scan. */
public final class MarkdownReporter {

  /** The file name this reporter writes within a scan's output directory. */
  public static final String FILE_NAME = "summary.md";

  /**
   * Writes {@code scanResult} as {@value #FILE_NAME} under {@code outputDir}.
   *
   * @param scanResult the result to summarise
   * @param outputDir the directory to write into; created if it does not exist
   * @return the path of the file written
   */
  public Path write(ScanResult scanResult, Path outputDir) {
    return write(scanResult, outputDir, List.of());
  }

  /**
   * Writes {@code scanResult} as {@value #FILE_NAME} under {@code outputDir}, listing {@code
   * activeSuppressions} in a dedicated section.
   *
   * @param scanResult the result to summarise
   * @param outputDir the directory to write into; created if it does not exist
   * @param activeSuppressions suppressions currently in effect (already filtered to those not yet
   *     expired)
   * @return the path of the file written
   */
  public Path write(
      ScanResult scanResult, Path outputDir, List<SuppressionEntry> activeSuppressions) {
    Path target = outputDir.resolve(FILE_NAME);
    try {
      Files.createDirectories(outputDir);
      Files.writeString(target, render(scanResult, activeSuppressions));
      return target;
    } catch (IOException e) {
      throw new ReportWriteException("Failed to write " + target, e);
    }
  }

  /**
   * Appends a publish-failure warning to a previously written {@value #FILE_NAME}. Publication
   * happens after every report is already written (it runs after policy evaluation, orchestrated by
   * the CLI layer, not this reporter's own {@link #write}), so this is a deliberate append to an
   * existing file rather than something folded into {@link #render}.
   *
   * @param outputDir the directory {@value #FILE_NAME} was already written into
   * @param reason why publication failed
   * @throws ReportWriteException if the file cannot be appended to
   */
  public void appendPublishFailureWarning(Path outputDir, String reason) {
    Path target = outputDir.resolve(FILE_NAME);
    String warning = "\n> **Warning:** failed to publish scan result: " + reason + "\n";
    try {
      Files.writeString(target, warning, java.nio.file.StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new ReportWriteException("Failed to append publish warning to " + target, e);
    }
  }

  /**
   * Renders the Markdown summary for {@code scanResult} without writing it to disk.
   *
   * @param scanResult the result to summarise
   * @return the rendered Markdown document
   */
  public String render(ScanResult scanResult) {
    return render(scanResult, List.of());
  }

  /**
   * Renders the Markdown summary for {@code scanResult}, including {@code activeSuppressions},
   * without writing it to disk.
   *
   * @param scanResult the result to summarise
   * @param activeSuppressions suppressions currently in effect (already filtered to those not yet
   *     expired)
   * @return the rendered Markdown document
   */
  public String render(ScanResult scanResult, List<SuppressionEntry> activeSuppressions) {
    StringBuilder markdown = new StringBuilder();
    markdown.append("# Debt Hunter Summary\n\n");
    markdown.append("**Verdict:** ").append(scanResult.policy().status()).append("\n\n");
    markdown.append("**Commit:** ").append(scanResult.run().commit()).append("\n\n");
    markdown
        .append("**History depth:** ")
        .append(scanResult.run().historyDepth().displayName())
        .append("\n\n");
    markdown.append("**Degraded:** ").append(scanResult.isDegraded()).append("\n\n");
    markdown
        .append("**Existing findings:** ")
        .append(countExisting(scanResult.findings()))
        .append("\n\n");

    appendHistoryWarning(markdown, scanResult.run().historyDepth());
    appendEngineStatuses(markdown, scanResult.run().engines());
    appendDegradedEngines(markdown, scanResult.run().engines());
    appendFindings(markdown, scanResult.findings());
    appendNewFindingsBySeverity(markdown, scanResult.findings());
    appendTopNewFindings(markdown, scanResult.findings());
    appendBreachedThresholds(markdown, scanResult.policy());
    appendActiveSuppressions(markdown, activeSuppressions);

    return markdown.toString();
  }

  private long countExisting(List<Finding> findings) {
    return findings.stream().filter(finding -> !finding.isNew()).count();
  }

  private void appendHistoryWarning(StringBuilder markdown, HistoryDepth historyDepth) {
    if (historyDepth == HistoryDepth.FULL) {
      return;
    }
    markdown
        .append("> **Warning:** history depth is ")
        .append(historyDepth.displayName())
        .append(". Hotspot, churn, temporal-coupling, and knowledge-concentration findings have")
        .append(" reduced confidence until full history is available. Run `debt-hunter doctor`")
        .append(" for recommendations.\n\n");
  }

  private void appendEngineStatuses(StringBuilder markdown, java.util.List<EngineStatus> engines) {
    markdown.append("## Engines (").append(engines.size()).append(")\n\n");
    if (engines.isEmpty()) {
      markdown.append("No engines ran.\n\n");
      return;
    }
    for (EngineStatus engine : engines) {
      markdown
          .append("- ")
          .append(engine.id())
          .append(" (")
          .append(engine.version())
          .append("): ")
          .append(engine.status());
      if (engine.reason() != null) {
        markdown.append(" — ").append(engine.reason());
      }
      markdown.append("\n");
    }
    markdown.append("\n");
  }

  private void appendFindings(StringBuilder markdown, java.util.List<Finding> findings) {
    markdown.append("## Findings (").append(findings.size()).append(")\n\n");
    if (findings.isEmpty()) {
      markdown.append("No findings.\n\n");
      return;
    }
    Map<Severity, Long> countsBySeverity =
        findings.stream()
            .collect(
                Collectors.groupingBy(
                    Finding::severity, () -> new EnumMap<>(Severity.class), Collectors.counting()));
    for (Severity severity : Severity.values()) {
      long count = countsBySeverity.getOrDefault(severity, 0L);
      if (count > 0) {
        markdown.append("- ").append(severity).append(": ").append(count).append("\n");
      }
    }
    markdown.append("\n");
  }

  private void appendNewFindingsBySeverity(StringBuilder markdown, List<Finding> findings) {
    List<Finding> newFindings = findings.stream().filter(Finding::isNew).toList();
    markdown.append("## New findings (").append(newFindings.size()).append(")\n\n");
    if (newFindings.isEmpty()) {
      markdown.append("No new findings.\n\n");
      return;
    }
    Map<Severity, Long> countsBySeverity =
        newFindings.stream()
            .collect(
                Collectors.groupingBy(
                    Finding::severity, () -> new EnumMap<>(Severity.class), Collectors.counting()));
    for (Severity severity : Severity.values()) {
      long count = countsBySeverity.getOrDefault(severity, 0L);
      if (count > 0) {
        markdown.append("- ").append(severity).append(": ").append(count).append("\n");
      }
    }
    markdown.append("\n");
  }

  private void appendTopNewFindings(StringBuilder markdown, List<Finding> findings) {
    List<Finding> top =
        findings.stream()
            .filter(Finding::isNew)
            .sorted(Comparator.comparingDouble(Finding::score).reversed())
            .limit(3)
            .toList();
    markdown.append("## Top new findings\n\n");
    if (top.isEmpty()) {
      markdown.append("No new findings.\n\n");
      return;
    }
    for (Finding finding : top) {
      markdown
          .append("- `")
          .append(finding.fingerprint())
          .append("` ")
          .append(finding.severity())
          .append(" (score ")
          .append(finding.score())
          .append("): ")
          .append(finding.message())
          .append(" — ")
          .append(finding.path())
          .append("\n");
    }
    markdown.append("\n");
  }

  private void appendBreachedThresholds(StringBuilder markdown, PolicyResult policy) {
    List<PolicyViolation> violations = policy.reasons();
    markdown.append("## Breached thresholds (").append(violations.size()).append(")\n\n");
    if (violations.isEmpty()) {
      markdown.append("No breached thresholds.\n\n");
      return;
    }
    for (PolicyViolation violation : violations) {
      markdown
          .append("- `")
          .append(violation.rule())
          .append("` — threshold: ")
          .append(violation.threshold())
          .append(", actual: ")
          .append(violation.actual())
          .append("\n");
    }
    markdown.append("\n");
  }

  private void appendDegradedEngines(StringBuilder markdown, List<EngineStatus> engines) {
    List<EngineStatus> degraded =
        engines.stream().filter(engine -> engine.status() != EngineHealth.OK).toList();
    markdown.append("## Degraded engines (").append(degraded.size()).append(")\n\n");
    if (degraded.isEmpty()) {
      markdown.append("No degraded engines.\n\n");
      return;
    }
    for (EngineStatus engine : degraded) {
      markdown
          .append("- ")
          .append(engine.id())
          .append(" (")
          .append(engine.version())
          .append("): ")
          .append(engine.status());
      if (engine.reason() != null) {
        markdown.append(" — ").append(engine.reason());
      }
      markdown.append("\n");
    }
    markdown.append("\n");
  }

  private void appendActiveSuppressions(
      StringBuilder markdown, List<SuppressionEntry> activeSuppressions) {
    markdown.append("## Active suppressions (").append(activeSuppressions.size()).append(")\n\n");
    if (activeSuppressions.isEmpty()) {
      markdown.append("No active suppressions.\n");
      return;
    }
    for (SuppressionEntry suppression : activeSuppressions) {
      markdown
          .append("- `")
          .append(suppression.fingerprint())
          .append("` — owner: ")
          .append(suppression.owner())
          .append(", expires: ")
          .append(suppression.expiresOn())
          .append(", reason: ")
          .append(suppression.reason())
          .append("\n");
    }
  }
}
