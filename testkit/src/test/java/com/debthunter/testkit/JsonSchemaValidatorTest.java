package com.debthunter.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonSchemaValidatorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void aDocumentMatchingTheSchemaHasNoValidationFailures() throws Exception {
    JsonNode valid =
        objectMapper.readTree(
            """
            {
              "schemaVersion": "1.0",
              "run": {
                "id": "run-1",
                "toolVersion": "0.1.0-test",
                "imageDigest": null,
                "timestamp": "2026-01-01T00:00:00Z",
                "repository": "/repo",
                "project": null,
                "commit": "abc123",
                "baseCommit": null,
                "branch": null,
                "pullRequest": null,
                "historyDepth": "FULL",
                "engines": [],
                "degraded": false,
                "baselineProvenance": null
              },
              "findings": [],
              "metrics": {},
              "policy": {"bundleVersion": "unversioned", "status": "PASSED", "reasons": []}
            }
            """);

    Set<?> failures =
        JsonSchemaValidator.validate(valid, JsonSchemaValidator.DEBT_HUNTER_V1_SCHEMA);

    assertThat(failures).isEmpty();
  }

  @Test
  void aDocumentMissingARequiredFieldHasValidationFailures() throws Exception {
    JsonNode missingRun = objectMapper.readTree("{\"schemaVersion\": \"1.0.0\"}");

    Set<?> failures =
        JsonSchemaValidator.validate(missingRun, JsonSchemaValidator.DEBT_HUNTER_V1_SCHEMA);

    assertThat(failures).isNotEmpty();
  }

  @Test
  void validatesAFileOnDiskTheSameWayAsAnInMemoryTree(@TempDir Path tempDir) throws Exception {
    Path jsonFile = tempDir.resolve("report.json");
    Files.writeString(jsonFile, "{\"schemaVersion\": \"1.0.0\"}");

    Set<?> failures =
        JsonSchemaValidator.validate(jsonFile, JsonSchemaValidator.DEBT_HUNTER_V1_SCHEMA);

    assertThat(failures).isNotEmpty();
  }

  @Test
  void anUnknownSchemaResourceThrows() {
    assertThatThrownBy(
            () ->
                JsonSchemaValidator.validate(
                    objectMapper.createObjectNode(), "/no/such/schema.json"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
