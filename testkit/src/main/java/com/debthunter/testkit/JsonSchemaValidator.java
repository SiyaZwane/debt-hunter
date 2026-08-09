package com.debthunter.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Validates JSON documents against Debt Hunter's own JSON Schema fixtures. */
public final class JsonSchemaValidator {

  /** Classpath location of the v1 findings-report schema. */
  public static final String DEBT_HUNTER_V1_SCHEMA = "/schemas/debt-hunter-v1.schema.json";

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final JsonSchemaFactory FACTORY =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

  private JsonSchemaValidator() {}

  /**
   * Validates a JSON file on disk against a schema on the classpath.
   *
   * @param jsonFile the JSON document to validate
   * @param schemaResourcePath classpath location of the JSON Schema, e.g. {@link
   *     #DEBT_HUNTER_V1_SCHEMA}
   * @return every validation failure; empty if the document is valid
   */
  public static Set<ValidationMessage> validate(Path jsonFile, String schemaResourcePath) {
    try {
      JsonNode json = JSON.readTree(Files.readString(jsonFile));
      return validate(json, schemaResourcePath);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Validates a JSON document already parsed into a tree against a schema on the classpath.
   *
   * @param json the document to validate
   * @param schemaResourcePath classpath location of the JSON Schema, e.g. {@link
   *     #DEBT_HUNTER_V1_SCHEMA}
   * @return every validation failure; empty if the document is valid
   */
  public static Set<ValidationMessage> validate(JsonNode json, String schemaResourcePath) {
    return loadSchema(schemaResourcePath).validate(json);
  }

  private static JsonSchema loadSchema(String resourcePath) {
    try (InputStream in = JsonSchemaValidator.class.getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IllegalArgumentException(
            "Schema resource not found on classpath: " + resourcePath);
      }
      return FACTORY.getSchema(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
