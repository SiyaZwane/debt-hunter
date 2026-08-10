package com.debthunter.policy;

import com.debthunter.domain.Category;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.Severity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Parses a policy bundle's YAML text into a validated {@link PolicyBundle}. Uses SnakeYAML's {@link
 * SafeConstructor} (no custom tags, no arbitrary type instantiation) since this YAML may come from
 * a repository under scan, not a fully trusted source.
 *
 * <p>Expected shape:
 *
 * <pre>{@code
 * version: "1.0"
 * metadata:
 *   name: default
 * analysis:
 *   minimumHistoryDepth: FULL        # optional: FULL | PARTIAL | SHALLOW
 * policy:
 *   main:
 *     rules:
 *       - id: no-new-critical
 *         severity: CRITICAL          # CRITICAL | HIGH | MEDIUM | LOW | INFO
 *         maxCount: 0
 *   pullRequest:
 *     rules: []
 * exclusions:
 *   categories: []                    # optional: Category names
 *   paths: []                         # optional: exact or prefix path matches
 * suppressions:
 *   maxExpiryDays: 90                 # optional; parsed only, not yet enforced
 * }</pre>
 */
public final class PolicyBundleParser {

  /**
   * Parses {@code yaml} into a validated {@link PolicyBundle}.
   *
   * @param yaml the policy bundle's YAML text
   * @return the parsed, validated bundle
   * @throws PolicyParseException if the YAML is malformed or does not conform to the expected shape
   */
  public PolicyBundle parse(String yaml) {
    Object loaded;
    try {
      loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
    } catch (RuntimeException e) {
      throw new PolicyParseException("Malformed YAML: " + e.getMessage(), e);
    }
    if (loaded == null) {
      throw new PolicyParseException("Policy bundle is empty");
    }
    if (!(loaded instanceof Map<?, ?> rawRoot)) {
      throw new PolicyParseException("Policy bundle must be a YAML mapping at the top level");
    }
    return build(asStringKeyedMap(rawRoot, "<root>"));
  }

  /**
   * Loads and parses the central policy bundle at {@code policyPath}, or {@link
   * PolicyBundle#permissive()} if none was configured.
   *
   * @param policyPath path to a policy bundle YAML file, or {@code null} if none is configured
   * @return the parsed bundle, or the permissive default
   * @throws PolicyParseException if {@code policyPath} is set but cannot be read or parsed
   */
  public PolicyBundle loadCentral(Path policyPath) {
    if (policyPath == null) {
      return PolicyBundle.permissive();
    }
    String yaml;
    try {
      yaml = Files.readString(policyPath);
    } catch (IOException e) {
      throw new PolicyParseException(
          "Could not read policy file " + policyPath + ": " + e.getMessage(), e);
    }
    return parse(yaml);
  }

  private PolicyBundle build(Map<String, Object> root) {
    String version = requireString(root, "version", "<root>");
    Map<String, Object> metadata = optionalMap(root, "metadata", "<root>");

    Map<String, Object> analysis = optionalMap(root, "analysis", "<root>");
    HistoryDepth minimumHistoryDepth = parseHistoryDepth(analysis);

    Map<String, Object> policy = optionalMap(root, "policy", "<root>");
    List<PolicyRule> mainRules = parseRules(policy, "main");
    List<PolicyRule> pullRequestRules = parseRules(policy, "pullRequest");

    Map<String, Object> exclusions = optionalMap(root, "exclusions", "<root>");
    List<Category> excludedCategories = parseCategories(exclusions);
    List<String> excludedPaths = parseStringList(exclusions, "paths", "exclusions");

    Map<String, Object> suppressions = optionalMap(root, "suppressions", "<root>");
    int maxExpiryDays = parseNonNegativeInt(suppressions, "maxExpiryDays", "suppressions", 0);

    return new PolicyBundle(
        version,
        metadata,
        minimumHistoryDepth,
        mainRules,
        pullRequestRules,
        excludedCategories,
        excludedPaths,
        maxExpiryDays);
  }

  private HistoryDepth parseHistoryDepth(Map<String, Object> analysis) {
    Object value = analysis.get("minimumHistoryDepth");
    if (value == null) {
      return null;
    }
    try {
      return HistoryDepth.valueOf(value.toString());
    } catch (IllegalArgumentException e) {
      throw new PolicyParseException(
          "analysis.minimumHistoryDepth must be one of FULL, PARTIAL, SHALLOW, was: " + value);
    }
  }

  private List<PolicyRule> parseRules(Map<String, Object> policy, String modeKey) {
    Map<String, Object> modeSection = optionalMap(policy, modeKey, "policy");
    Object rawRules = modeSection.get("rules");
    if (rawRules == null) {
      return List.of();
    }
    if (!(rawRules instanceof List<?> rawList)) {
      throw new PolicyParseException("policy." + modeKey + ".rules must be a list");
    }
    List<PolicyRule> rules = new ArrayList<>();
    for (Object entry : rawList) {
      rules.add(parseRule(entry, modeKey));
    }
    return rules;
  }

  private PolicyRule parseRule(Object entry, String modeKey) {
    if (!(entry instanceof Map<?, ?> rawRule)) {
      throw new PolicyParseException("policy." + modeKey + ".rules entries must be mappings");
    }
    String location = "policy." + modeKey + ".rules";
    Map<String, Object> rule = asStringKeyedMap(rawRule, location);
    String id = requireString(rule, "id", location);
    Object severityValue = rule.get("severity");
    if (severityValue == null) {
      throw new PolicyParseException(location + "[" + id + "].severity is required");
    }
    Severity severity;
    try {
      severity = Severity.valueOf(severityValue.toString());
    } catch (IllegalArgumentException e) {
      throw new PolicyParseException(
          location + "[" + id + "].severity is not a valid Severity: " + severityValue);
    }
    int maxCount = parseNonNegativeInt(rule, "maxCount", location + "[" + id + "]", null);
    try {
      return new PolicyRule(id, severity, maxCount);
    } catch (IllegalArgumentException e) {
      throw new PolicyParseException(location + "[" + id + "]: " + e.getMessage(), e);
    }
  }

  private List<Category> parseCategories(Map<String, Object> exclusions) {
    Object raw = exclusions.get("categories");
    if (raw == null) {
      return List.of();
    }
    if (!(raw instanceof List<?> rawList)) {
      throw new PolicyParseException("exclusions.categories must be a list");
    }
    List<Category> categories = new ArrayList<>();
    for (Object entry : rawList) {
      try {
        categories.add(Category.valueOf(String.valueOf(entry)));
      } catch (IllegalArgumentException e) {
        throw new PolicyParseException("exclusions.categories has an invalid entry: " + entry);
      }
    }
    return categories;
  }

  private List<String> parseStringList(Map<String, Object> parent, String key, String location) {
    Object raw = parent.get(key);
    if (raw == null) {
      return List.of();
    }
    if (!(raw instanceof List<?> rawList)) {
      throw new PolicyParseException(location + "." + key + " must be a list");
    }
    List<String> values = new ArrayList<>();
    for (Object entry : rawList) {
      values.add(String.valueOf(entry));
    }
    return values;
  }

  private int parseNonNegativeInt(
      Map<String, Object> parent, String key, String location, Integer defaultValue) {
    Object raw = parent.get(key);
    if (raw == null) {
      if (defaultValue != null) {
        return defaultValue;
      }
      throw new PolicyParseException(location + "." + key + " is required");
    }
    if (!(raw instanceof Number number)) {
      throw new PolicyParseException(location + "." + key + " must be a whole number, was: " + raw);
    }
    int value = number.intValue();
    if (value < 0) {
      throw new PolicyParseException(location + "." + key + " must not be negative: " + value);
    }
    return value;
  }

  private String requireString(Map<String, Object> parent, String key, String location) {
    Object value = parent.get(key);
    if (value == null || String.valueOf(value).isBlank()) {
      throw new PolicyParseException(location + "." + key + " is required");
    }
    return String.valueOf(value);
  }

  private Map<String, Object> optionalMap(Map<String, Object> parent, String key, String location) {
    Object value = parent.get(key);
    if (value == null) {
      return Map.of();
    }
    if (!(value instanceof Map<?, ?> rawMap)) {
      throw new PolicyParseException(location + "." + key + " must be a mapping");
    }
    return asStringKeyedMap(rawMap, location + "." + key);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> asStringKeyedMap(Map<?, ?> raw, String location) {
    for (Object key : raw.keySet()) {
      if (!(key instanceof String)) {
        throw new PolicyParseException(location + " has a non-string key: " + key);
      }
    }
    return (Map<String, Object>) raw;
  }
}
