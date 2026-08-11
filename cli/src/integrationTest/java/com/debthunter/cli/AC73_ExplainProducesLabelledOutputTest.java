package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.ai.ProvenanceLabeller;
import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.debthunter.domain.Severity;
import com.debthunter.output.JsonReporter;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * AC-73: {@code debt-hunter explain} prints an AI-authored explanation and remediation, both
 * clearly labelled as model-generated, exercised through the real CLI entry point and a real local
 * HTTP server standing in for the AI service.
 */
@Tag("integration")
class AC73_ExplainProducesLabelledOutputTest {

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void ac73_explainPrintsALabelledExplanationAndRemediation(@TempDir Path reportDir)
      throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          byte[] body =
              "This method changes often because it mixes two concerns."
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    server.start();

    Path reportFile = writeReport(reportDir);

    PrintStream originalOut = System.out;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    int exitCode;
    try {
      System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
      exitCode =
          new CommandLine(new DebtHunterCli())
              .execute(
                  "explain",
                  "--report",
                  reportFile.toString(),
                  "--finding-id",
                  "f-1",
                  "--explain-endpoint",
                  "http://127.0.0.1:" + server.getAddress().getPort());
    } finally {
      System.setOut(originalOut);
    }

    assertThat(exitCode).isZero();
    String output = buffer.toString(StandardCharsets.UTF_8);
    assertThat(output)
        .contains(ProvenanceLabeller.LABEL)
        .contains("This method changes often because it mixes two concerns.");
    long labelOccurrences =
        output.lines().filter(line -> line.contains(ProvenanceLabeller.LABEL)).count();
    assertThat(labelOccurrences).isEqualTo(2);
  }

  private Path writeReport(Path reportDir) {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0-test")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    Finding finding =
        Finding.builder()
            .id("f-1")
            .ruleId("hotspot.rule")
            .category(Category.HOTSPOT)
            .severity(Severity.HIGH)
            .path("Foo.java")
            .message("Foo.java changes often")
            .fingerprint("fp-1")
            .build();
    ScanResult scanResult =
        new ScanResult(run, List.of(finding), Map.of(), PolicyResult.passed("unversioned"));
    return new JsonReporter().write(scanResult, reportDir);
  }
}
