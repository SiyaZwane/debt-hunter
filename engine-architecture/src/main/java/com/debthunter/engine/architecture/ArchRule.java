package com.debthunter.engine.architecture;

import com.debthunter.domain.Severity;
import java.util.List;
import java.util.Objects;

/**
 * One declarative import rule from {@code .debt-hunter-arch.yml}: which files it applies to ({@code
 * appliesTo}, a glob), and which imports are allowed and/or denied within them.
 */
public record ArchRule(
    String name,
    String appliesTo,
    List<String> allowedImports,
    List<String> deniedImports,
    Severity severity) {

  /** Validates required fields and defensively copies the import lists. */
  public ArchRule {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(appliesTo, "appliesTo");
    allowedImports = allowedImports == null ? List.of() : List.copyOf(allowedImports);
    deniedImports = deniedImports == null ? List.of() : List.copyOf(deniedImports);
    Objects.requireNonNull(severity, "severity");
  }
}
