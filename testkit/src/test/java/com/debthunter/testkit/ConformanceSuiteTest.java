package com.debthunter.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies {@link ConformanceRunner} and {@link ConformanceSuite} themselves: every fixture, when
 * scanned twice by invokers that each report a fresh random run id but otherwise identical,
 * deterministic content, is judged conforming once {@link VolatileFieldMasker} strips that run id
 * out — and judged non-conforming when the underlying content genuinely differs. This test uses a
 * small JGit-based invoker rather than a real Debt Hunter scan, since testkit sits underneath
 * application and cli in the module graph and cannot depend on either.
 */
@Tag("integration")
class ConformanceSuiteTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void everyFixtureInTheSuiteConformsBetweenTwoEquivalentInvokers(@TempDir Path workDir)
      throws Exception {
    ConformanceRunner runner =
        new ConformanceRunner(this::repositorySummary, this::repositorySummary);

    List<ConformanceResult> results = runner.runAll(ConformanceSuite.fixtures(), workDir);

    assertThat(results).hasSameSizeAs(ConformanceSuite.fixtures());
    assertThat(results)
        .allSatisfy(result -> assertThat(result.matches()).as(result.describe()).isTrue());
  }

  @Test
  void aGenuineContentDifferenceIsReportedAsNonConforming(@TempDir Path workDir) throws Exception {
    ScanInvoker candidateWithExtraCommit =
        (repoPath, outputDir) -> {
          try (Git git = Git.open(repoPath.toFile())) {
            Files.writeString(repoPath.resolve("Extra.java"), "class Extra {}");
            git.add().addFilepattern("Extra.java").call();
            git.commit().setMessage("add an extra file the reference never saw").call();
          }
          return repositorySummary(repoPath, outputDir);
        };
    ConformanceRunner runner =
        new ConformanceRunner(this::repositorySummary, candidateWithExtraCommit);
    ConformanceFixture fixture =
        new ConformanceFixture(
            "single-file", builder -> builder.commitFile("Foo.java", "class Foo {}", "add Foo"));

    ConformanceResult result = runner.run(fixture, workDir);

    assertThat(result.matches()).isFalse();
    assertThat(result.describe()).contains("MISMATCH");
  }

  /**
   * A deterministic-except-for-run-id JSON summary of a repository, standing in for a real scan.
   */
  private com.fasterxml.jackson.databind.JsonNode repositorySummary(Path repoPath, Path outputDir)
      throws Exception {
    int commitCount = 0;
    try (Git git = Git.open(repoPath.toFile())) {
      for (var ignored : git.log().call()) {
        commitCount++;
      }
    }
    long fileCount;
    try (var paths = Files.walk(repoPath)) {
      fileCount =
          paths.filter(Files::isRegularFile).filter(p -> !p.toString().contains("/.git/")).count();
    }

    ObjectNode run = objectMapper.createObjectNode();
    run.put("id", UUID.randomUUID().toString());
    run.put("timestamp", java.time.Instant.now().toString());
    run.put("commitCount", commitCount);
    run.put("fileCount", fileCount);

    ObjectNode root = objectMapper.createObjectNode();
    root.set("run", run);
    return root;
  }
}
