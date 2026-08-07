package com.debthunter.engine.codemaat;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.output.DeterministicObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Every golden Code Maat CSV fixture, parsed and mapped, must still produce exactly the canonical
 * JSON captured the last time this suite ran clean. A diff here means either the mapping logic
 * changed (update the fixture deliberately) or it regressed (fix the code).
 */
class CodeMaatGoldenFileTest {

  private static final Path GOLDEN_DIR = Path.of("src/test/resources/golden");

  private final CodeMaatOutputParser parser = new CodeMaatOutputParser();
  private final CodeMaatFindingMapper mapper = new CodeMaatFindingMapper();
  private final ObjectMapper json = DeterministicObjectMapper.create();

  @Test
  void revisionsGoldenFileProducesExpectedFindings() throws Exception {
    assertMatchesGolden(
        "revisions", mapper.mapRevisions(parser.parseRevisions(readGolden("revisions.csv"))));
  }

  @Test
  void couplingGoldenFileProducesExpectedFindings() throws Exception {
    assertMatchesGolden(
        "coupling", mapper.mapCoupling(parser.parseCoupling(readGolden("coupling.csv"))));
  }

  @Test
  void ageGoldenFileProducesExpectedMetrics() throws Exception {
    assertMatchesGolden("age", mapper.mapAge(parser.parseAge(readGolden("age.csv"))).values());
  }

  @Test
  void authorsGoldenFileProducesExpectedFindings() throws Exception {
    assertMatchesGolden(
        "authors", mapper.mapAuthors(parser.parseAuthors(readGolden("authors.csv"))));
  }

  private void assertMatchesGolden(String name, Object actual) throws Exception {
    JsonNode actualJson = json.readTree(json.writeValueAsString(actual));
    JsonNode expectedJson = json.readTree(readGolden(name + "-expected.json"));
    assertThat(actualJson).isEqualTo(expectedJson);
  }

  private String readGolden(String fileName) throws Exception {
    return Files.readString(GOLDEN_DIR.resolve(fileName));
  }
}
