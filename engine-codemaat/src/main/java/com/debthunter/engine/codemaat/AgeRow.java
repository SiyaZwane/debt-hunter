package com.debthunter.engine.codemaat;

import java.util.Objects;

/** One row of Code Maat's {@code age} analysis: how long since a file last changed. */
public record AgeRow(String entity, int ageMonths) {

  /** Validates required fields. */
  public AgeRow {
    Objects.requireNonNull(entity, "entity");
  }
}
