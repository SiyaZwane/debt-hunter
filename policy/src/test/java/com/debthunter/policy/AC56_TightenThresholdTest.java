package com.debthunter.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Severity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-56: a repo-local {@code .debt-hunter.yml} can tighten a central policy's threshold — a lower
 * {@code maxCount} and a lower (more inclusive) severity floor both take effect in the composed
 * bundle, each recorded as a tightened, local-sourced field.
 */
class AC56_TightenThresholdTest {

  private final PolicyComposer composer = new PolicyComposer(new PolicyBundleParser());

  @Test
  void ac56_aLocalOverrideTightensBothMaxCountAndSeverityFloor(@TempDir Path repoRoot)
      throws IOException {
    PolicyBundle central =
        new PolicyBundle(
            "1.0",
            Map.of(),
            null,
            List.of(new PolicyRule("no-new-debt", Severity.CRITICAL, 5)),
            List.of(),
            List.of(),
            List.of(),
            0);
    Files.writeString(
        repoRoot.resolve(PolicyComposer.LOCAL_POLICY_FILE_NAME),
        """
        version: "1.0"
        policy:
          main:
            rules:
              - id: no-new-debt
                severity: MEDIUM
                maxCount: 1
        """);

    ComposedPolicy composed = composer.compose(repoRoot, central);

    assertThat(composed.bundle().mainRules())
        .containsExactly(new PolicyRule("no-new-debt", Severity.MEDIUM, 1));
    assertThat(composed.provenance())
        .anySatisfy(
            provenance -> {
              assertThat(provenance.field()).isEqualTo("policy.main.rules[no-new-debt]");
              assertThat(provenance.source()).isEqualTo("local (tightened)");
              assertThat(provenance.detail())
                  .contains("MEDIUM")
                  .contains("was")
                  .contains("CRITICAL");
            });
  }
}
