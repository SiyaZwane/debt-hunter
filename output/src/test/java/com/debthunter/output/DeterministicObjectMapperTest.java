package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeterministicObjectMapperTest {

  private final ObjectMapper mapper = DeterministicObjectMapper.create();

  private record Sample(String zebra, String alpha, String mike) {}

  @Test
  void objectPropertiesAreSortedAlphabeticallyRegardlessOfDeclarationOrder() throws Exception {
    String json = mapper.writeValueAsString(new Sample("z", "a", "m"));

    assertThat(json.indexOf("\"alpha\"")).isLessThan(json.indexOf("\"mike\""));
    assertThat(json.indexOf("\"mike\"")).isLessThan(json.indexOf("\"zebra\""));
  }

  @Test
  void mapKeysAreSortedRegardlessOfInsertionOrder() throws Exception {
    Map<String, Integer> insertedOutOfOrder = new LinkedHashMap<>();
    insertedOutOfOrder.put("zebra", 1);
    insertedOutOfOrder.put("alpha", 2);

    String json = mapper.writeValueAsString(insertedOutOfOrder);

    assertThat(json.indexOf("\"alpha\"")).isLessThan(json.indexOf("\"zebra\""));
  }

  @Test
  void outputIsIndented() throws Exception {
    String json = mapper.writeValueAsString(new Sample("z", "a", "m"));

    assertThat(json).contains(System.lineSeparator());
  }

  @Test
  void instantsAreSerialisedAsIso8601TextNotEpochNumbers() throws Exception {
    String json = mapper.writeValueAsString(Instant.parse("2026-01-01T12:30:00Z"));

    assertThat(json).isEqualTo("\"2026-01-01T12:30:00Z\"");
  }

  @Test
  void datesWithoutTheirOwnZoneAreFormattedInUtcRegardlessOfJvmDefaultTimeZone() throws Exception {
    java.util.TimeZone originalDefault = java.util.TimeZone.getDefault();
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"));
    try {
      java.util.Date date = java.util.Date.from(Instant.parse("2026-06-15T08:00:00Z"));
      String json = mapper.writeValueAsString(date);

      assertThat(json).contains("+00:00");
    } finally {
      java.util.TimeZone.setDefault(originalDefault);
    }
  }

  @Test
  void mapperConfigurationIsIndependentAcrossInstances() {
    ObjectMapper first = DeterministicObjectMapper.create();
    ObjectMapper second = DeterministicObjectMapper.create();

    assertThat(first).isNotSameAs(second);
  }
}
