package com.debthunter.output;

import com.debthunter.domain.EngineStatus;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
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
    Path target = outputDir.resolve(FILE_NAME);
    try {
      Files.createDirectories(outputDir);
      Files.writeString(target, render(scanResult));
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
    StringBuilder markdown = new StringBuilder();
    markdown.append("# Debt Hunter Summary\n\n");
    markdown.append("**Verdict:** ").append(scanResult.policy().status()).append("\n\n");
    markdown.append("**Commit:** ").append(scanResult.run().commit()).append("\n\n");
    markdown
        .append("**History depth:** ")
        .append(scanResult.run().historyDepth().displayName())
        .append("\n\n");
    markdown.append("**Degraded:** ").append(scanResult.isDegraded()).append("\n\n");

    appendHistoryWarning(markdown, scanResult.run().historyDepth());
    appendEngineStatuses(markdown, scanResult.run().engines());
    appendFindings(markdown, scanResult.findings());

    return markdown.toString();
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
      markdown.append("No findings.\n");
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
  }
}
