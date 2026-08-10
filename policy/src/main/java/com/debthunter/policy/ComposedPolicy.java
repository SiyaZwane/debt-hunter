package com.debthunter.policy;

import java.util.List;
import java.util.Objects;

/**
 * The result of {@link PolicyComposer#compose}: the effective, merged policy bundle, and a
 * human-readable trail of where each of its fields came from.
 *
 * @param bundle the effective policy bundle to evaluate scans against
 * @param provenance one entry per field, in a stable, deterministic order
 */
public record ComposedPolicy(PolicyBundle bundle, List<PolicyProvenance> provenance) {

  /** Validates required fields and defensively copies {@code provenance}. */
  public ComposedPolicy {
    Objects.requireNonNull(bundle, "bundle");
    provenance = provenance == null ? List.of() : List.copyOf(provenance);
  }
}
