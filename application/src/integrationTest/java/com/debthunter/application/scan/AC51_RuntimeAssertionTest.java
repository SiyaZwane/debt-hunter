package com.debthunter.application.scan;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.application.history.HistoryDepthEnforcer;
import com.debthunter.engine.spi.AnalysisMode;
import com.debthunter.output.JsonReporter;
import com.debthunter.output.MarkdownReporter;
import com.debthunter.output.MetricsReporter;
import com.debthunter.output.SarifReporter;
import com.debthunter.policy.BaselineComparator;
import com.debthunter.policy.BaselineResolver;
import com.debthunter.policy.PolicyBundleParser;
import com.debthunter.policy.PolicyEvaluator;
import com.debthunter.repository.GitHistoryProvider;
import com.debthunter.testkit.FixtureRepoBuilder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-51: a scan's own overhead — repository inspection, policy evaluation, writing every report —
 * stays within a generous runtime target across repeated runs. The threshold is deliberately
 * generous (seconds, not milliseconds) to avoid flaking on a loaded CI runner or sandbox; its job
 * is to catch a genuine regression (an accidental O(n^2) loop, a runaway retry), not to police
 * exact latency.
 */
@Tag("integration")
class AC51_RuntimeAssertionTest {

  private static final int RUN_COUNT = 20;
  private static final long P95_TARGET_MILLIS = 2000;

  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac51_p95ScanLatencyIsWithinTheGenerousRuntimeTarget(@TempDir Path outputRoot) {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo {}", "add Foo")
            .commitFile("Bar.java", "class Bar {}", "add Bar")
            .hotspot("Hot.java", 5);

    ScanUseCase scanUseCase = defaultScanUseCase();

    List<Long> durationsMillis = new ArrayList<>();
    for (int i = 0; i < RUN_COUNT; i++) {
      Path outputDir = outputRoot.resolve("run-" + i);
      ScanRequest request =
          new ScanRequest(
              fixture.path(),
              outputDir,
              AnalysisMode.FULL,
              null,
              null,
              List.of(),
              null,
              false,
              null,
              null);

      long start = System.nanoTime();
      ScanOutcome outcome = scanUseCase.execute(request);
      long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

      assertThat(outcome.scanResult()).isNotNull();
      durationsMillis.add(elapsedMillis);
    }

    long p95 = p95(durationsMillis);
    assertThat(p95)
        .as(
            "p95 scan latency over %d runs was %dms (target %dms); durations: %s",
            RUN_COUNT, p95, P95_TARGET_MILLIS, durationsMillis)
        .isLessThan(P95_TARGET_MILLIS);
  }

  private long p95(List<Long> durationsMillis) {
    List<Long> sorted = new ArrayList<>(durationsMillis);
    Collections.sort(sorted);
    int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
    return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
  }

  private ScanUseCase defaultScanUseCase() {
    return new ScanUseCase(
        new GitHistoryProvider(),
        new JsonReporter(),
        new MarkdownReporter(),
        new MetricsReporter(),
        new SarifReporter(),
        new BaselineResolver(),
        new BaselineComparator(),
        new PolicyBundleParser(),
        new PolicyEvaluator(),
        new HistoryDepthEnforcer(),
        "0.1.0-test");
  }
}
