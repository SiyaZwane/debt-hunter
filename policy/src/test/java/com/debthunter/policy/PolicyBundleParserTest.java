package com.debthunter.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.debthunter.domain.Category;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.Severity;
import com.debthunter.engine.spi.AnalysisMode;
import org.junit.jupiter.api.Test;

class PolicyBundleParserTest {

  private final PolicyBundleParser parser = new PolicyBundleParser();

  private static final String FULL_YAML =
      """
      version: "1.0"
      metadata:
        name: default
      analysis:
        minimumHistoryDepth: FULL
      policy:
        main:
          rules:
            - id: no-new-critical
              severity: CRITICAL
              maxCount: 0
            - id: limit-new-high
              severity: HIGH
              maxCount: 5
        pullRequest:
          rules:
            - id: no-new-high-or-above
              severity: HIGH
              maxCount: 0
      exclusions:
        categories: [HOTSPOT]
        paths: [generated]
      suppressions:
        maxExpiryDays: 90
      """;

  @Test
  void parsesEveryFieldOfAFullyPopulatedBundle() {
    PolicyBundle bundle = parser.parse(FULL_YAML);

    assertThat(bundle.version()).isEqualTo("1.0");
    assertThat(bundle.metadata()).containsEntry("name", "default");
    assertThat(bundle.minimumHistoryDepth()).isEqualTo(HistoryDepth.FULL);
    assertThat(bundle.mainRules())
        .containsExactly(
            new PolicyRule("no-new-critical", Severity.CRITICAL, 0),
            new PolicyRule("limit-new-high", Severity.HIGH, 5));
    assertThat(bundle.pullRequestRules())
        .containsExactly(new PolicyRule("no-new-high-or-above", Severity.HIGH, 0));
    assertThat(bundle.excludedCategories()).containsExactly(Category.HOTSPOT);
    assertThat(bundle.excludedPaths()).containsExactly("generated");
    assertThat(bundle.suppressionsMaxExpiryDays()).isEqualTo(90);
    assertThat(bundle.rulesFor(AnalysisMode.FULL)).isEqualTo(bundle.mainRules());
    assertThat(bundle.rulesFor(AnalysisMode.PULL_REQUEST)).isEqualTo(bundle.pullRequestRules());
  }

  @Test
  void minimalBundleOmittingEveryOptionalSectionParsesWithSensibleDefaults() {
    PolicyBundle bundle = parser.parse("version: \"1.0\"\n");

    assertThat(bundle.metadata()).isEmpty();
    assertThat(bundle.minimumHistoryDepth()).isNull();
    assertThat(bundle.mainRules()).isEmpty();
    assertThat(bundle.pullRequestRules()).isEmpty();
    assertThat(bundle.excludedCategories()).isEmpty();
    assertThat(bundle.excludedPaths()).isEmpty();
    assertThat(bundle.suppressionsMaxExpiryDays()).isZero();
  }

  @Test
  void malformedYamlSyntaxIsRejected() {
    assertThatThrownBy(() -> parser.parse("version: [unterminated"))
        .isInstanceOf(PolicyParseException.class)
        .hasMessageContaining("Malformed YAML");
  }

  @Test
  void aNonMappingTopLevelIsRejected() {
    assertThatThrownBy(() -> parser.parse("- just\n- a\n- list\n"))
        .isInstanceOf(PolicyParseException.class)
        .hasMessageContaining("mapping");
  }

  @Test
  void aMissingVersionIsRejected() {
    assertThatThrownBy(() -> parser.parse("metadata:\n  name: default\n"))
        .isInstanceOf(PolicyParseException.class)
        .hasMessageContaining("version");
  }

  @Test
  void anInvalidHistoryDepthValueIsRejected() {
    assertThatThrownBy(
            () -> parser.parse("version: \"1.0\"\nanalysis:\n  minimumHistoryDepth: BOGUS\n"))
        .isInstanceOf(PolicyParseException.class)
        .hasMessageContaining("minimumHistoryDepth");
  }

  @Test
  void aRuleMissingSeverityIsRejected() {
    String yaml =
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-severity
                maxCount: 0
        """;

    assertThatThrownBy(() -> parser.parse(yaml))
        .isInstanceOf(PolicyParseException.class)
        .hasMessageContaining("severity");
  }

  @Test
  void aRuleWithAnInvalidSeverityIsRejected() {
    String yaml =
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: bad-severity
                severity: EXTREME
                maxCount: 0
        """;

    assertThatThrownBy(() -> parser.parse(yaml))
        .isInstanceOf(PolicyParseException.class)
        .hasMessageContaining("Severity");
  }

  @Test
  void aRuleMissingMaxCountIsRejected() {
    String yaml =
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-max-count
                severity: HIGH
        """;

    assertThatThrownBy(() -> parser.parse(yaml))
        .isInstanceOf(PolicyParseException.class)
        .hasMessageContaining("maxCount");
  }

  @Test
  void aNegativeMaxCountIsRejected() {
    String yaml =
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: negative
                severity: HIGH
                maxCount: -1
        """;

    assertThatThrownBy(() -> parser.parse(yaml))
        .isInstanceOf(PolicyParseException.class)
        .hasMessageContaining("negative");
  }

  @Test
  void anInvalidExcludedCategoryIsRejected() {
    String yaml = "version: \"1.0\"\nexclusions:\n  categories: [NOT_A_CATEGORY]\n";

    assertThatThrownBy(() -> parser.parse(yaml))
        .isInstanceOf(PolicyParseException.class)
        .hasMessageContaining("exclusions.categories");
  }

  @Test
  void anEmptyDocumentIsRejected() {
    assertThatThrownBy(() -> parser.parse("")).isInstanceOf(PolicyParseException.class);
  }
}
