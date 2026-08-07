package com.debthunter.domain;

import java.util.Objects;

/** A named, scoped numeric measurement produced alongside findings. */
public record DebtMetric(String name, double value, String scope) {

  /** Validates required fields. */
  public DebtMetric {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(scope, "scope");
  }
}
