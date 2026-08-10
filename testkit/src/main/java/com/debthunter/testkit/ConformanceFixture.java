package com.debthunter.testkit;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A named repository shape used to prove conformance: the same fixture, built identically, must
 * produce the same (volatile-fields-aside) output regardless of which platform or environment
 * scanned it.
 */
public record ConformanceFixture(String name, Consumer<FixtureRepoBuilder> setup) {

  /** Validates required fields. */
  public ConformanceFixture {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(setup, "setup");
  }
}
