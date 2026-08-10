package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.application.scan.ExitCode;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The documented exit-code contract: every scenario in the spec's mapping table has a
 * corresponding, correctly-valued {@link ExitCode} constant, and nothing else.
 */
class ExitCodeMappingTest {

  @Test
  void everyDocumentedExitCodeScenarioMapsToTheCorrectValue() {
    assertThat(ExitCode.POLICY_SATISFIED.code()).isZero();
    assertThat(ExitCode.POLICY_VIOLATED.code()).isEqualTo(1);
    assertThat(ExitCode.CONFIGURATION_ERROR.code()).isEqualTo(2);
    assertThat(ExitCode.ENGINE_TOLERANCE_EXCEEDED.code()).isEqualTo(3);
    assertThat(ExitCode.INSUFFICIENT_HISTORY.code()).isEqualTo(4);
    assertThat(ExitCode.BASELINE_UNAVAILABLE.code()).isEqualTo(5);
    assertThat(ExitCode.INTERNAL_ERROR.code()).isEqualTo(10);
  }

  @Test
  void thereAreExactlySevenExitCodesAndNoDuplicateValues() {
    int[] values = Arrays.stream(ExitCode.values()).mapToInt(ExitCode::code).toArray();

    assertThat(values).hasSize(7);
    assertThat(Arrays.stream(values).distinct().count()).isEqualTo(values.length);
  }
}
