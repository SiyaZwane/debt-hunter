package com.debthunter.engine.spi;

import com.debthunter.domain.Category;
import java.util.List;
import java.util.Objects;

/** Static identity and capability metadata for an {@link AnalysisEngine}. */
public record EngineDescriptor(
    String id, String version, List<Category> categories, CostClass costClass) {

  /** Validates required fields and defensively copies {@code categories}. */
  public EngineDescriptor {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(version, "version");
    categories = categories == null ? List.of() : List.copyOf(categories);
    Objects.requireNonNull(costClass, "costClass");
  }
}
