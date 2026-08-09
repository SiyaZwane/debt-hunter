package com.debthunter.policy;

import com.debthunter.domain.Finding;
import com.debthunter.domain.ScanResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compares a scan's findings against a baseline by fingerprint, classifying each and setting {@link
 * Finding#isNew()} accordingly.
 */
public final class BaselineComparator {

  /** How a finding relates to the baseline it was compared against. */
  public enum Classification {
    /** No baseline finding shares this fingerprint. */
    NEW,
    /** A baseline finding shares this fingerprint and is no more severe. */
    EXISTING,
    /** A baseline finding shares this fingerprint but was less severe. */
    REGRESSED,
    /** A baseline finding whose fingerprint no longer appears in the current scan. */
    RESOLVED
  }

  /** One finding, tagged with how it compares to the baseline. */
  public record ClassifiedFinding(Finding finding, Classification classification) {}

  /**
   * The full comparison outcome.
   *
   * @param findings the current findings, with {@link Finding#isNew()} set
   * @param classified every current finding (NEW, EXISTING, or REGRESSED) followed by every
   *     baseline finding with no matching fingerprint in the current scan (RESOLVED)
   */
  public record ComparisonResult(List<Finding> findings, List<ClassifiedFinding> classified) {

    /** Defensively copies both lists. */
    public ComparisonResult {
      findings = List.copyOf(findings);
      classified = List.copyOf(classified);
    }
  }

  /**
   * Compares {@code currentFindings} against {@code baseline}'s findings by fingerprint.
   *
   * @param currentFindings this scan's findings
   * @param baseline the baseline to compare against, or {@code null} if none was found
   * @return the classified comparison, with {@code isNew} set on every returned current finding
   */
  public ComparisonResult compare(List<Finding> currentFindings, ScanResult baseline) {
    List<Finding> baselineFindings = baseline == null ? List.of() : baseline.findings();
    Map<String, Finding> baselineByFingerprint =
        baselineFindings.stream()
            .collect(Collectors.toMap(Finding::fingerprint, Function.identity(), (a, b) -> a));

    List<Finding> findings = new ArrayList<>();
    List<ClassifiedFinding> classified = new ArrayList<>();
    for (Finding current : currentFindings) {
      Finding baselineMatch = baselineByFingerprint.get(current.fingerprint());
      Classification classification = classify(current, baselineMatch);
      Finding withIsNew = current.withIsNew(classification == Classification.NEW);
      findings.add(withIsNew);
      classified.add(new ClassifiedFinding(withIsNew, classification));
    }

    Map<String, Finding> currentByFingerprint =
        currentFindings.stream()
            .collect(Collectors.toMap(Finding::fingerprint, Function.identity(), (a, b) -> a));
    for (Finding baselineFinding : baselineFindings) {
      if (!currentByFingerprint.containsKey(baselineFinding.fingerprint())) {
        classified.add(new ClassifiedFinding(baselineFinding, Classification.RESOLVED));
      }
    }

    return new ComparisonResult(findings, classified);
  }

  private Classification classify(Finding current, Finding baselineMatch) {
    if (baselineMatch == null) {
      return Classification.NEW;
    }
    return current.severity().ordinal() < baselineMatch.severity().ordinal()
        ? Classification.REGRESSED
        : Classification.EXISTING;
  }
}
