package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.PolicyStatus;
import com.debthunter.domain.PolicyViolation;
import com.debthunter.domain.ScanResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-15: a failed policy always carries populated reasons; a passed one always has an empty array.
 */
class AC15_PolicyViolationReasonsTest {

  private final JsonReporter reporter = new JsonReporter();
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void ac15_failedPolicyReportsEveryViolationReason(@TempDir Path outputDir) throws Exception {
    List<PolicyViolation> violations =
        List.of(
            new PolicyViolation("no-critical-findings", "0", "1", List.of("f-1")),
            new PolicyViolation("max-churn", "10", "15", List.of("f-2", "f-3")));
    PolicyResult failed = new PolicyResult("bundle-1", PolicyStatus.FAILED, violations);

    Path written = reporter.write(scanResultWithPolicy(failed), outputDir);

    JsonNode reasons = json.readTree(written.toFile()).get("policy").get("reasons");
    assertThat(reasons).hasSize(2);
    assertThat(reasons.get(0).get("rule").asText()).isEqualTo("no-critical-findings");
    assertThat(reasons.get(0).get("findingIds")).hasSize(1);
    assertThat(reasons.get(1).get("rule").asText()).isEqualTo("max-churn");
    assertThat(reasons.get(1).get("findingIds")).hasSize(2);
  }

  @Test
  void ac15_passedPolicyStillHasAnEmptyReasonsArrayNotAMissingField(@TempDir Path outputDir)
      throws Exception {
    Path written = reporter.write(scanResultWithPolicy(PolicyResult.passed("bundle-1")), outputDir);

    JsonNode policy = json.readTree(written.toFile()).get("policy");
    assertThat(policy.has("reasons")).isTrue();
    assertThat(policy.get("reasons").isArray()).isTrue();
    assertThat(policy.get("reasons")).isEmpty();
  }

  private ScanResult scanResultWithPolicy(PolicyResult policy) {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    return new ScanResult(run, List.of(), Map.of(), policy);
  }
}
