package com.debthunter.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.debthunter.domain.Category;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.Severity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolicyComposerTest {

  private final PolicyComposer composer = new PolicyComposer(new PolicyBundleParser());

  @Test
  void withNoLocalOverrideFileComposedBundleIsExactlyCentral(@TempDir Path repoRoot) {
    PolicyBundle central = bundleWithMainRule("no-critical", Severity.CRITICAL, 0);

    ComposedPolicy composed = composer.compose(repoRoot, central);

    assertThat(composed.bundle()).isEqualTo(central);
    assertThat(composed.provenance()).allSatisfy(p -> assertThat(p.source()).isEqualTo("central"));
  }

  @Test
  void aLocalOverrideCanLowerMaxCount(@TempDir Path repoRoot) throws IOException {
    PolicyBundle central = bundleWithMainRule("no-critical", Severity.CRITICAL, 5);
    writeLocalPolicy(
        repoRoot,
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-critical
                severity: CRITICAL
                maxCount: 1
        """);

    ComposedPolicy composed = composer.compose(repoRoot, central);

    assertThat(composed.bundle().mainRules())
        .containsExactly(new PolicyRule("no-critical", Severity.CRITICAL, 1));
    assertThat(composed.provenance())
        .anySatisfy(
            p -> {
              assertThat(p.field()).isEqualTo("policy.main.rules[no-critical]");
              assertThat(p.source()).isEqualTo("local (tightened)");
            });
  }

  @Test
  void aLocalOverrideCanLowerTheSeverityFloorToCatchMoreFindings(@TempDir Path repoRoot)
      throws IOException {
    PolicyBundle central = bundleWithMainRule("no-critical", Severity.CRITICAL, 0);
    writeLocalPolicy(
        repoRoot,
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-critical
                severity: LOW
                maxCount: 0
        """);

    ComposedPolicy composed = composer.compose(repoRoot, central);

    assertThat(composed.bundle().mainRules())
        .containsExactly(new PolicyRule("no-critical", Severity.LOW, 0));
  }

  @Test
  void aLocalOverrideRaisingMaxCountIsRejectedAsLoosening(@TempDir Path repoRoot)
      throws IOException {
    PolicyBundle central = bundleWithMainRule("no-critical", Severity.CRITICAL, 0);
    writeLocalPolicy(
        repoRoot,
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-critical
                severity: CRITICAL
                maxCount: 5
        """);

    assertThatThrownBy(() -> composer.compose(repoRoot, central))
        .isInstanceOf(PolicyLoosenedException.class)
        .hasMessageContaining("no-critical")
        .hasMessageContaining("maxCount");
  }

  @Test
  void aLocalOverrideRaisingTheSeverityFloorIsRejectedAsLoosening(@TempDir Path repoRoot)
      throws IOException {
    PolicyBundle central = bundleWithMainRule("no-critical", Severity.LOW, 0);
    writeLocalPolicy(
        repoRoot,
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-critical
                severity: CRITICAL
                maxCount: 0
        """);

    assertThatThrownBy(() -> composer.compose(repoRoot, central))
        .isInstanceOf(PolicyLoosenedException.class)
        .hasMessageContaining("severity");
  }

  @Test
  void aLocalOverrideCanAddABrandNewRule(@TempDir Path repoRoot) throws IOException {
    PolicyBundle central = bundleWithMainRule("no-critical", Severity.CRITICAL, 0);
    writeLocalPolicy(
        repoRoot,
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-high
                severity: HIGH
                maxCount: 0
        """);

    ComposedPolicy composed = composer.compose(repoRoot, central);

    assertThat(composed.bundle().mainRules())
        .containsExactlyInAnyOrder(
            new PolicyRule("no-critical", Severity.CRITICAL, 0),
            new PolicyRule("no-high", Severity.HIGH, 0));
    assertThat(composed.provenance())
        .anySatisfy(
            p -> {
              assertThat(p.field()).isEqualTo("policy.main.rules[no-high]");
              assertThat(p.source()).isEqualTo("local (new)");
            });
  }

  @Test
  void aLocalOverrideCanLowerTheMinimumHistoryDepthRequirement(@TempDir Path repoRoot)
      throws IOException {
    PolicyBundle central = bundleWithHistoryDepth(null);
    writeLocalPolicy(
        repoRoot,
        """
        version: "1.0"
        analysis:
          minimumHistoryDepth: FULL
        """);

    ComposedPolicy composed = composer.compose(repoRoot, central);

    assertThat(composed.bundle().minimumHistoryDepth()).isEqualTo(HistoryDepth.FULL);
  }

  @Test
  void aLocalOverrideRaisingTheMinimumHistoryDepthRequirementIsRejectedAsLoosening(
      @TempDir Path repoRoot) throws IOException {
    PolicyBundle central = bundleWithHistoryDepth(HistoryDepth.FULL);
    writeLocalPolicy(
        repoRoot,
        """
        version: "1.0"
        analysis:
          minimumHistoryDepth: SHALLOW
        """);

    assertThatThrownBy(() -> composer.compose(repoRoot, central))
        .isInstanceOf(PolicyLoosenedException.class)
        .hasMessageContaining("minimumHistoryDepth");
  }

  @Test
  void aLocalOverrideCanNarrowExcludedCategoriesToASubset(@TempDir Path repoRoot)
      throws IOException {
    PolicyBundle central = bundleWithExcludedCategories(List.of(Category.HOTSPOT, Category.CHURN));
    writeLocalPolicy(
        repoRoot,
        """
        version: "1.0"
        exclusions:
          categories: [HOTSPOT]
        """);

    ComposedPolicy composed = composer.compose(repoRoot, central);

    assertThat(composed.bundle().excludedCategories()).containsExactly(Category.HOTSPOT);
  }

  @Test
  void aLocalOverrideAddingAnExcludedCategoryNotInCentralIsRejectedAsLoosening(
      @TempDir Path repoRoot) throws IOException {
    PolicyBundle central = bundleWithExcludedCategories(List.of(Category.HOTSPOT));
    writeLocalPolicy(
        repoRoot,
        """
        version: "1.0"
        exclusions:
          categories: [HOTSPOT, CHURN]
        """);

    assertThatThrownBy(() -> composer.compose(repoRoot, central))
        .isInstanceOf(PolicyLoosenedException.class)
        .hasMessageContaining("exclusions.categories");
  }

  @Test
  void aLocalOverrideCanLowerTheMaxSuppressionExpiry(@TempDir Path repoRoot) throws IOException {
    PolicyBundle central = bundleWithMaxExpiryDays(90);
    writeLocalPolicy(
        repoRoot,
        """
        version: "1.0"
        suppressions:
          maxExpiryDays: 30
        """);

    ComposedPolicy composed = composer.compose(repoRoot, central);

    assertThat(composed.bundle().suppressionsMaxExpiryDays()).isEqualTo(30);
  }

  @Test
  void aLocalOverrideRaisingTheMaxSuppressionExpiryIsRejectedAsLoosening(@TempDir Path repoRoot)
      throws IOException {
    PolicyBundle central = bundleWithMaxExpiryDays(30);
    writeLocalPolicy(
        repoRoot,
        """
        version: "1.0"
        suppressions:
          maxExpiryDays: 90
        """);

    assertThatThrownBy(() -> composer.compose(repoRoot, central))
        .isInstanceOf(PolicyLoosenedException.class)
        .hasMessageContaining("maxExpiryDays");
  }

  @Test
  void aMalformedLocalOverrideFilePropagatesAsAPolicyParseException(@TempDir Path repoRoot)
      throws IOException {
    PolicyBundle central = PolicyBundle.permissive();
    writeLocalPolicy(repoRoot, "version: [unterminated");

    assertThatThrownBy(() -> composer.compose(repoRoot, central))
        .isInstanceOf(PolicyParseException.class);
  }

  private void writeLocalPolicy(Path repoRoot, String yaml) throws IOException {
    Files.writeString(repoRoot.resolve(PolicyComposer.LOCAL_POLICY_FILE_NAME), yaml);
  }

  private PolicyBundle bundleWithMainRule(String id, Severity severity, int maxCount) {
    return new PolicyBundle(
        "1.0",
        Map.of(),
        null,
        List.of(new PolicyRule(id, severity, maxCount)),
        List.of(),
        List.of(),
        List.of(),
        0);
  }

  private PolicyBundle bundleWithHistoryDepth(HistoryDepth depth) {
    return new PolicyBundle("1.0", Map.of(), depth, List.of(), List.of(), List.of(), List.of(), 0);
  }

  private PolicyBundle bundleWithExcludedCategories(List<Category> categories) {
    return new PolicyBundle("1.0", Map.of(), null, List.of(), List.of(), categories, List.of(), 0);
  }

  private PolicyBundle bundleWithMaxExpiryDays(int days) {
    return new PolicyBundle(
        "1.0", Map.of(), null, List.of(), List.of(), List.of(), List.of(), days);
  }
}
