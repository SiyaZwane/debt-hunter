package com.debthunter.engine.architecture;

import com.debthunter.domain.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Parses {@code .debt-hunter-arch.yml} into {@link ArchRule}s. Uses SnakeYAML's {@link
 * SafeConstructor} since this YAML comes from the repository under scan, not a trusted source.
 *
 * <p>Expected shape:
 *
 * <pre>{@code
 * rules:
 *   - id: domain-no-application-dependency
 *     appliesTo: "**&#47;domain/**&#47;*.java"
 *     deniedImports:
 *       - "com.debthunter.application.**"
 *     severity: HIGH                    # optional, defaults to HIGH
 * }</pre>
 */
public final class ArchRuleSetParser {

  /**
   * Parses {@code yaml} into a list of {@link ArchRule}s. An empty or {@code rules}-less document
   * parses to an empty list, since architecture rules are entirely optional.
   *
   * @param yaml the architecture rules file's YAML text
   * @return the parsed rules, in declaration order
   * @throws ArchRuleParseException if the YAML is malformed or does not conform to the expected
   *     shape
   */
  public List<ArchRule> parse(String yaml) {
    Object loaded;
    try {
      loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
    } catch (RuntimeException e) {
      throw new ArchRuleParseException("Malformed YAML: " + e.getMessage(), e);
    }
    if (loaded == null) {
      return List.of();
    }
    if (!(loaded instanceof Map<?, ?> rawRoot)) {
      throw new ArchRuleParseException("Architecture rules file must be a YAML mapping");
    }
    Map<String, Object> root = asStringKeyedMap(rawRoot);
    Object rawRules = root.get("rules");
    if (rawRules == null) {
      return List.of();
    }
    if (!(rawRules instanceof List<?> rawList)) {
      throw new ArchRuleParseException("rules must be a list");
    }
    List<ArchRule> rules = new ArrayList<>();
    for (Object entry : rawList) {
      rules.add(parseRule(entry));
    }
    return rules;
  }

  private ArchRule parseRule(Object entry) {
    if (!(entry instanceof Map<?, ?> rawRule)) {
      throw new ArchRuleParseException("rules entries must be mappings");
    }
    Map<String, Object> rule = asStringKeyedMap(rawRule);
    String name = requireString(rule, "id");
    String appliesTo = requireString(rule, "appliesTo");
    List<String> allowedImports = parseStringList(rule, "allowedImports");
    List<String> deniedImports = parseStringList(rule, "deniedImports");
    Severity severity = parseSeverity(rule, name);
    return new ArchRule(name, appliesTo, allowedImports, deniedImports, severity);
  }

  private Severity parseSeverity(Map<String, Object> rule, String name) {
    Object value = rule.get("severity");
    if (value == null) {
      return Severity.HIGH;
    }
    try {
      return Severity.valueOf(value.toString());
    } catch (IllegalArgumentException e) {
      throw new ArchRuleParseException(
          "rules[" + name + "].severity is not a valid Severity: " + value);
    }
  }

  private List<String> parseStringList(Map<String, Object> rule, String key) {
    Object raw = rule.get(key);
    if (raw == null) {
      return List.of();
    }
    if (!(raw instanceof List<?> rawList)) {
      throw new ArchRuleParseException("rules." + key + " must be a list");
    }
    List<String> values = new ArrayList<>();
    for (Object entry : rawList) {
      values.add(String.valueOf(entry));
    }
    return values;
  }

  private String requireString(Map<String, Object> rule, String key) {
    Object value = rule.get(key);
    if (value == null || String.valueOf(value).isBlank()) {
      throw new ArchRuleParseException("rules[]." + key + " is required");
    }
    return String.valueOf(value);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> asStringKeyedMap(Map<?, ?> raw) {
    for (Object key : raw.keySet()) {
      if (!(key instanceof String)) {
        throw new ArchRuleParseException("has a non-string key: " + key);
      }
    }
    return (Map<String, Object>) raw;
  }
}
