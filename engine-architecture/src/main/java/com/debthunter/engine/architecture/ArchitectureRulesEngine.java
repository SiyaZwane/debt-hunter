package com.debthunter.engine.architecture;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Fingerprinter;
import com.debthunter.engine.spi.AnalysisEngine;
import com.debthunter.engine.spi.AnalysisRequest;
import com.debthunter.engine.spi.CostClass;
import com.debthunter.engine.spi.EngineDescriptor;
import com.debthunter.engine.spi.EngineResult;
import com.debthunter.engine.spi.ProgressSink;
import com.debthunter.engine.spi.RepositoryContext;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks a repository's source files against declarative layer/dependency rules read from {@value
 * #RULES_FILE_NAME} at the repository root. Each rule names the files it applies to and the import
 * patterns those files may or may not depend on; a violating import produces an {@link
 * Category#ARCHITECTURE} finding. A repository with no rules file is not an error — it simply has
 * nothing to check.
 */
public final class ArchitectureRulesEngine implements AnalysisEngine {

  /** The declarative rules file this engine reads, resolved relative to the repository root. */
  public static final String RULES_FILE_NAME = ".debt-hunter-arch.yml";

  private static final String ENGINE_ID = "architecture-rules";
  private static final String ENGINE_VERSION = "1.0.0";

  private static final Pattern IMPORT_LINE =
      Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;");

  private final ArchRuleSetParser ruleSetParser;
  private final Fingerprinter fingerprinter;

  /** Creates the engine with the default rule parser and fingerprinter. */
  public ArchitectureRulesEngine() {
    this(new ArchRuleSetParser(), new Fingerprinter());
  }

  /**
   * Creates the engine with explicit collaborators, for testing.
   *
   * @param ruleSetParser parses {@value #RULES_FILE_NAME}
   * @param fingerprinter computes stable finding fingerprints
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "collaborators are stateless services, shared by reference intentionally")
  public ArchitectureRulesEngine(ArchRuleSetParser ruleSetParser, Fingerprinter fingerprinter) {
    this.ruleSetParser = ruleSetParser;
    this.fingerprinter = fingerprinter;
  }

  @Override
  public EngineDescriptor descriptor() {
    return new EngineDescriptor(
        ENGINE_ID, ENGINE_VERSION, List.of(Category.ARCHITECTURE), CostClass.LOW);
  }

  @Override
  public boolean supports(RepositoryContext context) {
    return true;
  }

  @Override
  public EngineResult analyse(AnalysisRequest request, ProgressSink sink) {
    long start = System.currentTimeMillis();
    Path rulesFile = request.repoPath().resolve(RULES_FILE_NAME);
    if (!Files.exists(rulesFile)) {
      return EngineResult.ok(List.of(), List.of(), elapsed(start));
    }

    List<ArchRule> rules;
    try {
      rules = ruleSetParser.parse(Files.readString(rulesFile, StandardCharsets.UTF_8));
    } catch (IOException e) {
      return EngineResult.failed(
          "Failed to read " + RULES_FILE_NAME + ": " + e.getMessage(), elapsed(start));
    } catch (ArchRuleParseException e) {
      return EngineResult.failed(
          "Failed to parse " + RULES_FILE_NAME + ": " + e.getMessage(), elapsed(start));
    }
    if (rules.isEmpty()) {
      return EngineResult.ok(List.of(), List.of(), elapsed(start));
    }

    List<Path> sourceFiles;
    try {
      sourceFiles = listJavaFiles(request.repoPath());
    } catch (IOException e) {
      return EngineResult.failed("Failed to list source files: " + e.getMessage(), elapsed(start));
    }

    sink.report("Checking source files against architecture rules", 0.5);
    Map<ArchRule, Pattern> appliesToPatterns = new HashMap<>();
    for (ArchRule rule : rules) {
      appliesToPatterns.put(rule, globToPattern(rule.appliesTo()));
    }

    List<Finding> findings = new ArrayList<>();
    List<String> readFailures = new ArrayList<>();
    for (Path file : sourceFiles) {
      String relativePath = relativize(request.repoPath(), file);
      for (ArchRule rule : rules) {
        if (!appliesToPatterns.get(rule).matcher(relativePath).matches()) {
          continue;
        }
        List<String> imports;
        try {
          imports = extractImports(file);
        } catch (IOException e) {
          readFailures.add(relativePath + ": " + e.getMessage());
          continue;
        }
        for (String importName : imports) {
          String violationReason = checkImport(rule, importName);
          if (violationReason != null) {
            findings.add(violationFinding(rule, relativePath, importName, violationReason));
          }
        }
      }
    }
    sink.report("Architecture check complete", 1.0);

    if (!readFailures.isEmpty()) {
      return EngineResult.degraded(
          findings,
          List.of(),
          "Could not read: " + String.join("; ", readFailures),
          elapsed(start));
    }
    return EngineResult.ok(findings, List.of(), elapsed(start));
  }

  private String checkImport(ArchRule rule, String importName) {
    for (String denied : rule.deniedImports()) {
      if (matchesImportPattern(importName, denied)) {
        return "denied by " + denied;
      }
    }
    if (!rule.allowedImports().isEmpty()
        && rule.allowedImports().stream().noneMatch(p -> matchesImportPattern(importName, p))) {
      return "not in the allowed import list";
    }
    return null;
  }

  private boolean matchesImportPattern(String importName, String pattern) {
    if (pattern.endsWith(".**")) {
      String prefix = pattern.substring(0, pattern.length() - 2);
      return importName.startsWith(prefix);
    }
    return importName.equals(pattern);
  }

  /**
   * Compiles a slash-separated glob to a {@link Pattern}, where {@code **} matches zero or more
   * whole path segments (including zero — so {@code "** /domain/** /*.java"} matches a file
   * directly inside {@code domain/}, not only one nested further down, unlike {@code
   * java.nio.file}'s glob matcher) and {@code *} matches within a single segment.
   */
  private Pattern globToPattern(String pattern) {
    String[] segments = pattern.split("/");
    StringBuilder regex = new StringBuilder("^");
    for (int i = 0; i < segments.length; i++) {
      boolean last = i == segments.length - 1;
      if (segments[i].equals("**")) {
        regex.append(last ? ".*" : "(?:.*/)?");
      } else {
        regex.append(segmentToRegex(segments[i]));
        if (!last) {
          regex.append('/');
        }
      }
    }
    regex.append('$');
    return Pattern.compile(regex.toString());
  }

  private String segmentToRegex(String segment) {
    StringBuilder sb = new StringBuilder();
    for (char c : segment.toCharArray()) {
      switch (c) {
        case '*' -> sb.append("[^/]*");
        case '?' -> sb.append("[^/]");
        default -> {
          if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
            sb.append('\\');
          }
          sb.append(c);
        }
      }
    }
    return sb.toString();
  }

  private List<Path> listJavaFiles(Path repoPath) throws IOException {
    try (Stream<Path> stream = Files.walk(repoPath)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().endsWith(".java"))
          .filter(p -> !relativize(repoPath, p).startsWith(".git/"))
          .sorted()
          .toList();
    }
  }

  private List<String> extractImports(Path file) throws IOException {
    List<String> imports = new ArrayList<>();
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      Matcher matcher = IMPORT_LINE.matcher(line.strip());
      if (matcher.matches()) {
        imports.add(matcher.group(1));
      }
    }
    return imports;
  }

  private String relativize(Path repoPath, Path file) {
    return repoPath.relativize(file).toString().replace(File.separatorChar, '/');
  }

  private Finding violationFinding(
      ArchRule rule, String relativePath, String importName, String reason) {
    String ruleId = "architecture." + rule.name();
    String message =
        relativePath
            + " imports "
            + importName
            + ", violating architecture rule '"
            + rule.name()
            + "' ("
            + reason
            + ")";
    return Finding.builder()
        .id(ruleId + ":" + relativePath + ":" + importName)
        .ruleId(ruleId)
        .category(Category.ARCHITECTURE)
        .severity(rule.severity())
        .confidence(1.0)
        .path(relativePath)
        .startLine(0)
        .message(message)
        .evidence(Map.of("import", importName, "rule", rule.name(), "engine", ENGINE_ID))
        .score(1.0)
        .fingerprint(fingerprinter.fingerprint(ruleId, relativePath, "", importName))
        .build();
  }

  private long elapsed(long start) {
    return System.currentTimeMillis() - start;
  }
}
