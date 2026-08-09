package com.debthunter.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisRunTest {

  @Test
  void builderPopulatesEveryField() {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0")
            .imageDigest("sha256:abc")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .project("my-project")
            .commit("abc123")
            .baseCommit("def456")
            .branch("main")
            .pullRequest("42")
            .historyDepth(HistoryDepth.FULL)
            .engines(List.of(new EngineStatus("code-maat", "1.0", EngineHealth.OK, 100, null)))
            .baselineProvenance("EXPLICIT")
            .build();

    assertThat(run.id()).isEqualTo("run-1");
    assertThat(run.toolVersion()).isEqualTo("0.1.0");
    assertThat(run.imageDigest()).isEqualTo("sha256:abc");
    assertThat(run.repository()).isEqualTo("/repo");
    assertThat(run.project()).isEqualTo("my-project");
    assertThat(run.commit()).isEqualTo("abc123");
    assertThat(run.baseCommit()).isEqualTo("def456");
    assertThat(run.branch()).isEqualTo("main");
    assertThat(run.pullRequest()).isEqualTo("42");
    assertThat(run.historyDepth()).isEqualTo(HistoryDepth.FULL);
    assertThat(run.baselineProvenance()).isEqualTo("EXPLICIT");
    assertThat(run.degraded()).isFalse();
  }

  @Test
  void baselineProvenanceDefaultsToNullWhenNeverSet() {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();

    assertThat(run.baselineProvenance()).isNull();
  }
}
