package com.debthunter.policy;

import com.debthunter.domain.SuppressionEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Loads a repository's {@value #SUPPRESSIONS_FILE_NAME}: which findings its owners have excused
 * from gating, why, and until when.
 *
 * <p>Expected shape:
 *
 * <pre>{@code
 * suppressions:
 *   - fingerprint: fp-abc123
 *     owner: alice
 *     reason: "Tracked in JIRA-123, fix scheduled for Q3"
 *     expires: 2026-06-01
 * }</pre>
 */
public final class SuppressionRegistry {

  /** The suppressions file this registry looks for, relative to the repository root. */
  public static final String SUPPRESSIONS_FILE_NAME = ".debt-hunter-suppressions.yml";

  /**
   * Loads {@code repoRoot}'s {@value #SUPPRESSIONS_FILE_NAME}, if one exists.
   *
   * @param repoRoot the repository root to look for a suppressions file in
   * @param maxExpiryDays the policy's {@code suppressions.maxExpiryDays}; {@code 0} means no
   *     ceiling is configured, matching the parser's own "omitted or explicit zero" default
   * @param commitDate the scanned commit's date, used both as the ceiling's anchor and as the "now"
   *     every entry's expiry is ultimately checked against — never the wall clock, so the same
   *     commit always evaluates the same way regardless of which day the scan runs
   * @return every entry the file declares, in file order; empty if no file exists
   * @throws SuppressionParseException if the file exists but its YAML is malformed
   * @throws SuppressionRejectedException if an entry's expiry exceeds {@code maxExpiryDays} from
   *     {@code commitDate}
   */
  public List<SuppressionEntry> load(Path repoRoot, int maxExpiryDays, LocalDate commitDate) {
    Path file = repoRoot.resolve(SUPPRESSIONS_FILE_NAME);
    if (!Files.exists(file)) {
      return List.of();
    }

    String yaml;
    try {
      yaml = Files.readString(file);
    } catch (IOException e) {
      throw new SuppressionParseException("Could not read " + file + ": " + e.getMessage(), e);
    }

    Object loaded;
    try {
      loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
    } catch (RuntimeException e) {
      throw new SuppressionParseException("Malformed YAML: " + e.getMessage(), e);
    }
    if (!(loaded instanceof Map<?, ?> root)) {
      throw new SuppressionParseException(SUPPRESSIONS_FILE_NAME + " must be a YAML mapping");
    }
    Object rawSuppressions = root.get("suppressions");
    if (rawSuppressions == null) {
      return List.of();
    }
    if (!(rawSuppressions instanceof List<?> rawList)) {
      throw new SuppressionParseException("suppressions must be a list");
    }

    List<SuppressionEntry> entries = new ArrayList<>();
    int index = 0;
    for (Object rawEntry : rawList) {
      entries.add(parseEntry(rawEntry, index, maxExpiryDays, commitDate));
      index++;
    }
    return List.copyOf(entries);
  }

  private SuppressionEntry parseEntry(
      Object rawEntry, int index, int maxExpiryDays, LocalDate commitDate) {
    String location = "suppressions[" + index + "]";
    if (!(rawEntry instanceof Map<?, ?> rawMap)) {
      throw new SuppressionParseException(location + " must be a mapping");
    }
    Map<String, Object> entry = asStringKeyedMap(rawMap, location);

    String fingerprint = requireString(entry, "fingerprint", location);
    String owner = requireString(entry, "owner", location);
    String reason = requireString(entry, "reason", location);
    LocalDate expiresOn = requireDate(entry, "expires", location);

    if (maxExpiryDays > 0 && expiresOn.isAfter(commitDate.plusDays(maxExpiryDays))) {
      throw new SuppressionRejectedException(
          location
              + ": expires "
              + expiresOn
              + " is more than "
              + maxExpiryDays
              + " day(s) after "
              + commitDate
              + ", the policy's suppressions.maxExpiryDays limit");
    }

    return new SuppressionEntry(fingerprint, owner, reason, expiresOn);
  }

  private String requireString(Map<String, Object> entry, String key, String location) {
    Object value = entry.get(key);
    if (value == null || String.valueOf(value).isBlank()) {
      throw new SuppressionParseException(location + "." + key + " is required");
    }
    return String.valueOf(value);
  }

  private LocalDate requireDate(Map<String, Object> entry, String key, String location) {
    Object value = entry.get(key);
    if (value == null) {
      throw new SuppressionParseException(location + "." + key + " is required");
    }
    if (value instanceof LocalDate date) {
      return date;
    }
    // An unquoted YYYY-MM-DD scalar matches YAML 1.1's timestamp tag, which SafeConstructor
    // resolves to a java.util.Date rather than a String — handled explicitly here rather than
    // asking every author to remember to quote the date.
    if (value instanceof java.util.Date legacyDate) {
      return legacyDate.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
    }
    try {
      return LocalDate.parse(String.valueOf(value));
    } catch (DateTimeParseException e) {
      throw new SuppressionParseException(
          location + "." + key + " must be an ISO-8601 date (YYYY-MM-DD), was: " + value);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> asStringKeyedMap(Map<?, ?> raw, String location) {
    for (Object key : raw.keySet()) {
      if (!(key instanceof String)) {
        throw new SuppressionParseException(location + " has a non-string key: " + key);
      }
    }
    return (Map<String, Object>) raw;
  }
}
