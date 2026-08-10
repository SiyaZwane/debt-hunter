package com.debthunter.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class VolatileFieldMaskerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void removesRunIdAndTimestampButLeavesEverythingElseUntouched() {
    ObjectNode root = objectMapper.createObjectNode();
    ObjectNode run = root.putObject("run");
    run.put("id", "run-1");
    run.put("timestamp", "2026-01-01T00:00:00Z");
    run.put("repository", "/repo");
    root.putArray("findings").add("f-1");

    VolatileFieldMasker.mask(root);

    assertThat(root.get("run").has("id")).isFalse();
    assertThat(root.get("run").has("timestamp")).isFalse();
    assertThat(root.get("run").get("repository").asText()).isEqualTo("/repo");
    assertThat(root.get("findings")).hasSize(1);
  }

  @Test
  void twoOtherwiseIdenticalReportsBecomeEqualAfterMasking() {
    ObjectNode a = objectMapper.createObjectNode();
    ObjectNode runA = a.putObject("run");
    runA.put("id", "run-a");
    runA.put("timestamp", "2026-01-01T00:00:00Z");
    runA.put("repository", "/repo");

    ObjectNode b = objectMapper.createObjectNode();
    ObjectNode runB = b.putObject("run");
    runB.put("id", "run-b");
    runB.put("timestamp", "2026-06-01T00:00:00Z");
    runB.put("repository", "/repo");

    assertThat(a).isNotEqualTo(b);

    VolatileFieldMasker.mask(a);
    VolatileFieldMasker.mask(b);

    assertThat(a).isEqualTo(b);
  }

  @Test
  void aReportWithNoRunNodeIsLeftUnchanged() {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("someOtherField", "value");

    VolatileFieldMasker.mask(root);

    JsonNode unchanged = root;
    assertThat(unchanged.get("someOtherField").asText()).isEqualTo("value");
  }
}
