package com.debthunter.application.scan;

import com.debthunter.domain.Finding;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Splits a monorepo scan's findings into per-project groups, by matching each finding's path
 * against a set of caller-configured project patterns. A finding matching no pattern is attributed
 * to {@value #DEFAULT_PROJECT} rather than silently dropped.
 */
public final class ProjectSlicer {

  /** The project a finding is attributed to when it matches no configured pattern. */
  public static final String DEFAULT_PROJECT = "default";

  /**
   * One named project and the pattern that identifies its files.
   *
   * @param name the project's name, e.g. {@code "frontend"}
   * @param pattern a path prefix (e.g. {@code "frontend"}) or, if it contains {@code *} or {@code
   *     ?}, a glob matched against the finding's path
   */
  public record ProjectSpec(String name, String pattern) {

    /** Validates required fields. */
    public ProjectSpec {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(pattern, "pattern");
    }
  }

  /**
   * Slices {@code findings} by {@code projects}, in the order {@code projects} declares them. Every
   * declared project appears in the result, even with an empty list, so an empty project is still
   * visible as "scanned, nothing found" rather than absent entirely. A finding matching no declared
   * project is grouped under {@value #DEFAULT_PROJECT}, which is added to the result only if at
   * least one finding actually falls into it.
   *
   * @param findings the findings to slice
   * @param projects the configured projects, in declaration order
   * @return findings grouped by project name; empty if {@code projects} is empty
   */
  public Map<String, List<Finding>> slice(List<Finding> findings, List<ProjectSpec> projects) {
    Objects.requireNonNull(findings, "findings");
    Objects.requireNonNull(projects, "projects");
    if (projects.isEmpty()) {
      return Map.of();
    }

    Map<String, List<Finding>> byProject = new LinkedHashMap<>();
    for (ProjectSpec spec : projects) {
      byProject.put(spec.name(), new ArrayList<>());
    }

    List<Finding> unmatched = new ArrayList<>();
    for (Finding finding : findings) {
      ProjectSpec match = firstMatch(finding.path(), projects);
      if (match == null) {
        unmatched.add(finding);
      } else {
        byProject.get(match.name()).add(finding);
      }
    }
    if (!unmatched.isEmpty()) {
      byProject.put(DEFAULT_PROJECT, unmatched);
    }
    return byProject;
  }

  private ProjectSpec firstMatch(String path, List<ProjectSpec> projects) {
    for (ProjectSpec spec : projects) {
      if (matches(path, spec.pattern())) {
        return spec;
      }
    }
    return null;
  }

  private boolean matches(String path, String pattern) {
    if (pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0) {
      return FileSystems.getDefault().getPathMatcher("glob:" + pattern).matches(Path.of(path));
    }
    String prefix = pattern.endsWith("/") ? pattern : pattern + "/";
    return path.equals(pattern) || path.startsWith(prefix);
  }
}
