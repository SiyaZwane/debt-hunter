package com.debthunter.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Strips fields from a scan report that are expected to differ between otherwise-identical runs — a
 * fresh random run id, the wall-clock timestamp — so two reports can be compared for conformance
 * without those fields causing a spurious mismatch.
 */
public final class VolatileFieldMasker {

  private VolatileFieldMasker() {}

  /**
   * Removes {@code run.id} and {@code run.timestamp} from {@code root} in place, if present.
   *
   * @param root the root node of a debt-hunter.json-shaped report
   */
  public static void mask(JsonNode root) {
    JsonNode run = root.get("run");
    if (run instanceof ObjectNode runNode) {
      runNode.remove("id");
      runNode.remove("timestamp");
    }
  }
}
