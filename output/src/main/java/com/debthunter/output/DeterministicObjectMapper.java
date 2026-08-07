package com.debthunter.output;

import com.debthunter.domain.ScanResult;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.TimeZone;

/**
 * The single source of truth for how Debt Hunter serialises JSON: alphabetically sorted properties
 * and map keys, indented output, and UTC timestamps, so that the same input always produces
 * byte-identical output.
 */
public final class DeterministicObjectMapper {

  private DeterministicObjectMapper() {}

  /**
   * Creates a new, independently configured {@link ObjectMapper} with deterministic output.
   *
   * @return a fresh {@link ObjectMapper} instance
   */
  public static ObjectMapper create() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.setTimeZone(TimeZone.getTimeZone("UTC"));
    mapper.registerModule(new JavaTimeModule());
    // ScanResult.isDegraded() is a convenience method, not a canonical record component; without
    // this, Jackson's bean introspection would still serialise it as a spurious top-level
    // "degraded" property alongside run/findings/metrics/policy. domain stays Jackson-free — the
    // mixin lives here instead of an annotation on the record itself.
    mapper.addMixIn(ScanResult.class, ScanResultMixin.class);
    return mapper;
  }

  private abstract static class ScanResultMixin {
    @JsonIgnore
    abstract boolean isDegraded();
  }
}
