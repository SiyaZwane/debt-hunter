package com.debthunter.engine.codemaat;

import java.util.Objects;

/** One row of Code Maat's {@code revisions} analysis: how many times a file has changed. */
public record RevisionsRow(String entity, int revisions) {

  /** Validates required fields. */
  public RevisionsRow {
    Objects.requireNonNull(entity, "entity");
  }
}
