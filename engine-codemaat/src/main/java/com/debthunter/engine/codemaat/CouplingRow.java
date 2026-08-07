package com.debthunter.engine.codemaat;

import java.util.Objects;

/** One row of Code Maat's {@code coupling} analysis: two files that tend to change together. */
public record CouplingRow(String entity, String coupled, int degree, int averageRevs) {

  /** Validates required fields. */
  public CouplingRow {
    Objects.requireNonNull(entity, "entity");
    Objects.requireNonNull(coupled, "coupled");
  }
}
