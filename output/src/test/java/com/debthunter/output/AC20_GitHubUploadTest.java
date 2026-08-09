package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-20: a generated SARIF file can be uploaded to GitHub's code-scanning API. Real, side-
 * effecting, and opt-in: this creates an actual code-scanning analysis in the target repository, so
 * it only runs when a contributor deliberately points it at a disposable test repo — never in
 * routine CI, and never against this project's own repository by accident.
 *
 * <p>To run it:
 *
 * <pre>{@code
 * export DEBTHUNTER_TEST_GITHUB_TOKEN=ghp_...        # needs security_events:write on the repo
 * export DEBTHUNTER_TEST_GITHUB_REPO=owner/repo       # a disposable test repo, not this one
 * mvn -pl output -am test -Dgroups=platform -DexcludedGroups=
 * }</pre>
 */
@Tag("platform")
class AC20_GitHubUploadTest {

  @Test
  void ac20_sarifUploadsSuccessfullyToGitHubCodeScanning(@TempDir Path outputDir) throws Exception {
    String token = System.getenv("DEBTHUNTER_TEST_GITHUB_TOKEN");
    String repo = System.getenv("DEBTHUNTER_TEST_GITHUB_REPO");
    assumeTrue(
        token != null && repo != null,
        "Set DEBTHUNTER_TEST_GITHUB_TOKEN and DEBTHUNTER_TEST_GITHUB_REPO to run this manually");

    Finding finding =
        Finding.builder()
            .id("f-1")
            .ruleId("static.rule")
            .category(Category.STATIC_ANALYSIS)
            .severity(Severity.MEDIUM)
            .path("README.md")
            .startLine(1)
            .message("Debt Hunter upload verification")
            .fingerprint("fp-upload-test")
            .build();
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0")
            .timestamp(Instant.now())
            .repository(repo)
            .commit(requireEnv("DEBTHUNTER_TEST_GITHUB_COMMIT_SHA"))
            .historyDepth(HistoryDepth.FULL)
            .build();
    ScanResult scanResult =
        new ScanResult(run, List.of(finding), Map.of(), PolicyResult.passed("b"));

    Path sarifFile = new SarifReporter().write(scanResult, outputDir);
    String base64GzippedSarif = gzipThenBase64(Files.readAllBytes(sarifFile));

    String ref = System.getenv().getOrDefault("DEBTHUNTER_TEST_GITHUB_REF", "refs/heads/main");
    String body =
        """
        {"commit_sha":"%s","ref":"%s","sarif":"%s","tool_name":"Debt Hunter"}
        """
            .formatted(run.commit(), ref, base64GzippedSarif)
            .strip();

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("https://api.github.com/repos/" + repo + "/code-scanning/sarifs"))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer " + token)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as("GitHub SARIF upload response: %s", response.body())
        .isEqualTo(202);
  }

  private String requireEnv(String name) {
    String value = System.getenv(name);
    if (value == null) {
      throw new IllegalStateException("Set " + name + " to run this test");
    }
    return value;
  }

  private String gzipThenBase64(byte[] content) throws Exception {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
      gzip.write(content);
    }
    return Base64.getEncoder().encodeToString(buffer.toByteArray());
  }
}
