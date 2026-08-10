package com.debthunter.policy;

import java.util.List;

/**
 * Reports whether a policy bundle's YAML is valid, without throwing — for callers (like {@code
 * ScanCommand}) that need a yes/no answer plus diagnostics rather than an exception.
 */
public final class PolicyValidator {

  private final PolicyBundleParser parser;

  /** Creates a validator backed by a default {@link PolicyBundleParser}. */
  public PolicyValidator() {
    this(new PolicyBundleParser());
  }

  /**
   * Creates a validator with an explicit parser, for testing.
   *
   * @param parser parses the YAML this validator checks
   */
  public PolicyValidator(PolicyBundleParser parser) {
    this.parser = parser;
  }

  /**
   * Validates {@code yaml}.
   *
   * @param yaml the policy bundle's YAML text
   * @return an empty list if valid, or a single-element list describing the first problem found
   */
  public List<String> validate(String yaml) {
    try {
      parser.parse(yaml);
      return List.of();
    } catch (PolicyParseException e) {
      return List.of(e.getMessage());
    }
  }

  /**
   * Whether {@code yaml} is a valid policy bundle.
   *
   * @param yaml the policy bundle's YAML text
   * @return {@code true} if {@link #validate} reports no problems
   */
  public boolean isValid(String yaml) {
    return validate(yaml).isEmpty();
  }
}
