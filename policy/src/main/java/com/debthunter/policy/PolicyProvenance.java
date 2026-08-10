package com.debthunter.policy;

import java.util.Objects;

/**
 * One human-readable line describing where a single field of a {@link ComposedPolicy} came from.
 *
 * @param field the field's path, e.g. {@code "policy.main.rules[no-new-critical].maxCount"}
 * @param source where the effective value came from: {@code "central"}, {@code "local (new)"}, or
 *     {@code "local (tightened)"}
 * @param detail the effective value, and — for a local override — what it tightened from
 */
public record PolicyProvenance(String field, String source, String detail) {

  /** Validates required fields. */
  public PolicyProvenance {
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(detail, "detail");
  }
}
