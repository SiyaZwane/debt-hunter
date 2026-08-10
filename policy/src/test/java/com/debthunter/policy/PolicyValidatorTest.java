package com.debthunter.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PolicyValidatorTest {

  private final PolicyValidator validator = new PolicyValidator();

  @Test
  void aValidBundleReportsNoErrors() {
    assertThat(validator.validate("version: \"1.0\"\n")).isEmpty();
    assertThat(validator.isValid("version: \"1.0\"\n")).isTrue();
  }

  @Test
  void malformedYamlReportsExactlyOneError() {
    var errors = validator.validate("version: [unterminated");

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("Malformed YAML");
    assertThat(validator.isValid("version: [unterminated")).isFalse();
  }

  @Test
  void aMissingRequiredFieldReportsADescriptiveError() {
    var errors = validator.validate("metadata:\n  name: default\n");

    assertThat(errors).singleElement().asString().contains("version");
  }

  @Test
  void anInvalidSeverityReportsADescriptiveError() {
    String yaml =
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: bad
                severity: NOT_A_SEVERITY
                maxCount: 0
        """;

    var errors = validator.validate(yaml);

    assertThat(errors).singleElement().asString().contains("Severity");
  }

  @Test
  void doesNotThrowForInvalidInput() {
    // validate() must never propagate PolicyParseException — that's the whole point of having it
    // alongside PolicyBundleParser.parse(), which does throw.
    assertThat(validator.validate("version: [unterminated")).isNotEmpty();
  }
}
