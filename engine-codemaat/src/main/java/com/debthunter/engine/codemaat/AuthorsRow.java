package com.debthunter.engine.codemaat;

import java.util.Objects;

/** One row of Code Maat's {@code authors} analysis: how many people have touched a file. */
public record AuthorsRow(String entity, int authors, int revisions) {

  /** Validates required fields. */
  public AuthorsRow {
    Objects.requireNonNull(entity, "entity");
  }
}
