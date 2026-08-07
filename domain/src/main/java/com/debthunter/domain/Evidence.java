package com.debthunter.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Typed, read-only view over a {@link Finding}'s raw evidence map. */
public final class Evidence {

  private static final Evidence EMPTY = new Evidence(Map.of());

  private final Map<String, Object> raw;

  private Evidence(Map<String, Object> raw) {
    this.raw = raw;
  }

  /**
   * Wraps a raw evidence map for typed access.
   *
   * @param raw the evidence map, or {@code null}
   * @return a typed view backed by an immutable copy of {@code raw}
   */
  public static Evidence of(Map<String, Object> raw) {
    return raw == null || raw.isEmpty() ? EMPTY : new Evidence(Map.copyOf(raw));
  }

  /**
   * Returns an empty evidence view.
   *
   * @return an {@link Evidence} with no entries
   */
  public static Evidence empty() {
    return EMPTY;
  }

  /**
   * How frequently the finding's location has changed, if the producing engine supplied it.
   *
   * @return the change frequency, or empty if not present or not a number
   */
  public Optional<Double> changeFrequency() {
    return numeric("changeFrequency");
  }

  /**
   * The authors associated with the finding's location, if the producing engine supplied them.
   *
   * @return an immutable list of author identifiers, empty if not present
   */
  @SuppressWarnings("unchecked")
  public List<String> authors() {
    Object value = raw.get("authors");
    if (value instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return List.of();
  }

  /**
   * The change in cyclomatic (or similar) complexity attributed to this finding.
   *
   * @return the complexity delta, or empty if not present or not a number
   */
  public Optional<Double> complexityDelta() {
    return numeric("complexityDelta");
  }

  /**
   * A human-readable description of how the finding's score was calculated.
   *
   * @return the calculation description, or empty if not present
   */
  public Optional<String> calculation() {
    return text("calculation");
  }

  /**
   * The identifier of the engine that produced this evidence.
   *
   * @return the engine id, or empty if not present
   */
  public Optional<String> engine() {
    return text("engine");
  }

  /**
   * The full underlying evidence map.
   *
   * @return an immutable map, never {@code null}
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "raw is always the result of Map.copyOf(), already unmodifiable")
  public Map<String, Object> asMap() {
    return raw;
  }

  private Optional<Double> numeric(String key) {
    Object value = raw.get(key);
    if (value instanceof Number number) {
      return Optional.of(number.doubleValue());
    }
    return Optional.empty();
  }

  private Optional<String> text(String key) {
    Object value = raw.get(key);
    return value == null ? Optional.empty() : Optional.of(String.valueOf(value));
  }
}
