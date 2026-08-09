package com.debthunter.output;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes {@code debt-hunter.sarif}, conforming to SARIF 2.1.0, for consumption by tools like GitHub
 * Code Scanning or Azure DevOps. Only findings that map naturally to a single reviewable location
 * are included — see {@link #EXCLUDED_CATEGORIES}.
 */
public final class SarifReporter {

  /** The file name this reporter writes within a scan's output directory. */
  public static final String FILE_NAME = "debt-hunter.sarif";

  private static final String SARIF_VERSION = "2.1.0";
  private static final String SARIF_SCHEMA_URI =
      "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json";
  private static final String TOOL_NAME = "Debt Hunter";
  private static final String TOOL_INFORMATION_URI = "https://github.com/SiyaZwane/debt-hunter";
  private static final String CATEGORY_ROOT = "debt-hunter";
  private static final String FINGERPRINT_KEY = "debtHunter/v1";

  /**
   * Categories excluded from SARIF: hotspot rankings, coupling graphs, and knowledge concentration
   * aren't single-location "problems" the way SARIF consumers (e.g. GitHub Code Scanning) expect,
   * unlike a churn or static-analysis finding pinned to one file.
   */
  private static final Set<Category> EXCLUDED_CATEGORIES =
      Set.of(Category.HOTSPOT, Category.TEMPORAL_COUPLING, Category.KNOWLEDGE_CONCENTRATION);

  private final ObjectMapper objectMapper;

  /** Creates a reporter using the shared {@link DeterministicObjectMapper}. */
  public SarifReporter() {
    this(DeterministicObjectMapper.create());
  }

  /**
   * Creates a reporter using a caller-supplied mapper, for testing.
   *
   * @param objectMapper the mapper to serialise with
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "ObjectMapper is a shared, reusable configuration object, not owned state")
  public SarifReporter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Writes a single-run SARIF document for {@code scanResult}: the whole repository, or — if its
   * run has a project set — that one project.
   *
   * @param scanResult the result to write
   * @param outputDir the directory to write into; created if it does not exist
   * @return the path of the file written
   */
  public Path write(ScanResult scanResult, Path outputDir) {
    Map<String, List<Finding>> findingsByProject = new LinkedHashMap<>();
    findingsByProject.put(scanResult.run().project(), scanResult.findings());
    return writeMultiProject(findingsByProject, scanResult.run().toolVersion(), outputDir);
  }

  /**
   * Writes a SARIF document with one {@code run} per project.
   *
   * @param findingsByProject findings keyed by project name; a {@code null} key means the whole
   *     repository rather than a named sub-project
   * @param toolVersion this build's version, recorded in every run's {@code tool.driver.version}
   * @param outputDir the directory to write into; created if it does not exist
   * @return the path of the file written
   */
  public Path writeMultiProject(
      Map<String, List<Finding>> findingsByProject, String toolVersion, Path outputDir) {
    List<SarifRun> runs =
        findingsByProject.entrySet().stream()
            .map(entry -> buildRun(entry.getKey(), entry.getValue(), toolVersion))
            .toList();
    SarifDocument document = new SarifDocument(SARIF_SCHEMA_URI, SARIF_VERSION, runs);

    Path target = outputDir.resolve(FILE_NAME);
    try {
      Files.createDirectories(outputDir);
      objectMapper.writeValue(target.toFile(), document);
      return target;
    } catch (IOException e) {
      throw new ReportWriteException("Failed to write " + target, e);
    }
  }

  private SarifRun buildRun(String project, List<Finding> findings, String toolVersion) {
    List<Finding> included =
        findings.stream().filter(f -> !EXCLUDED_CATEGORIES.contains(f.category())).toList();
    String category = project == null ? CATEGORY_ROOT : CATEGORY_ROOT + "/" + project;

    List<SarifRule> rules = buildRules(included);
    List<SarifResult> results = included.stream().map(f -> buildResult(f, category)).toList();

    SarifDriver driver = new SarifDriver(TOOL_NAME, toolVersion, TOOL_INFORMATION_URI, rules);
    // A trailing slash matches the convention GitHub Code Scanning itself uses for categories.
    return new SarifRun(new SarifTool(driver), new SarifAutomationDetails(category + "/"), results);
  }

  private List<SarifRule> buildRules(List<Finding> findings) {
    Set<String> seen = new LinkedHashSet<>();
    List<SarifRule> rules = new ArrayList<>();
    for (Finding finding : findings) {
      if (seen.add(finding.ruleId())) {
        rules.add(
            new SarifRule(
                finding.ruleId(),
                finding.ruleId(),
                new SarifMessage("Debt Hunter rule: " + finding.ruleId())));
      }
    }
    return rules;
  }

  private SarifResult buildResult(Finding finding, String category) {
    SarifArtifactLocation artifactLocation = new SarifArtifactLocation(finding.path());
    SarifRegion region = finding.startLine() > 0 ? new SarifRegion(finding.startLine()) : null;
    SarifPhysicalLocation physicalLocation = new SarifPhysicalLocation(artifactLocation, region);
    SarifLocation location = new SarifLocation(physicalLocation);

    return new SarifResult(
        finding.ruleId(),
        toSarifLevel(finding.severity()),
        new SarifMessage(finding.message()),
        List.of(location),
        Map.of(FINGERPRINT_KEY, finding.fingerprint()),
        new SarifProperties(category));
  }

  private String toSarifLevel(Severity severity) {
    return switch (severity) {
      case CRITICAL, HIGH -> "error";
      case MEDIUM -> "warning";
      case LOW, INFO -> "note";
    };
  }

  // --- SARIF 2.1.0 document model below: private, purely serialisation scaffolding for this
  // reporter — not a general-purpose SARIF library, so it only models what this reporter emits.

  private record SarifDocument(String $schema, String version, List<SarifRun> runs) {}

  private record SarifRun(
      SarifTool tool, SarifAutomationDetails automationDetails, List<SarifResult> results) {}

  private record SarifTool(SarifDriver driver) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record SarifDriver(
      String name, String version, String informationUri, List<SarifRule> rules) {}

  private record SarifRule(String id, String name, SarifMessage shortDescription) {}

  private record SarifAutomationDetails(String id) {}

  private record SarifMessage(String text) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record SarifResult(
      String ruleId,
      String level,
      SarifMessage message,
      List<SarifLocation> locations,
      Map<String, String> partialFingerprints,
      SarifProperties properties) {}

  private record SarifLocation(SarifPhysicalLocation physicalLocation) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record SarifPhysicalLocation(
      SarifArtifactLocation artifactLocation, SarifRegion region) {}

  private record SarifArtifactLocation(String uri) {}

  private record SarifRegion(int startLine) {}

  private record SarifProperties(String category) {}
}
