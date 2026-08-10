package com.debthunter.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.AnalysisRun;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.domain.PolicyResult;
import com.debthunter.domain.ScanResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Exercises {@link HttpResultUploader} against a real local HTTP server — no mocked transport. */
class ResultUploaderTest {

  private final ResultUploader uploader = new HttpResultUploader();
  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void aSuccessfulResponseIsReportedAsSuccess() throws IOException {
    AtomicReference<String> receivedBody = new AtomicReference<>();
    server = startServer(200, receivedBody);

    PublishResult result = uploader.publish(scanResult(), configFor(server.getAddress().getPort()));

    assertThat(result.success()).isTrue();
    assertThat(result.reason()).isNull();
    assertThat(receivedBody.get()).contains("\"schemaVersion\"").contains("run-1");
  }

  @Test
  void aServerErrorResponseIsReportedAsFailure() throws IOException {
    server = startServer(500, new AtomicReference<>());

    PublishResult result = uploader.publish(scanResult(), configFor(server.getAddress().getPort()));

    assertThat(result.success()).isFalse();
    assertThat(result.reason()).contains("500");
  }

  @Test
  void aClientErrorResponseIsReportedAsFailure() throws IOException {
    server = startServer(401, new AtomicReference<>());

    PublishResult result = uploader.publish(scanResult(), configFor(server.getAddress().getPort()));

    assertThat(result.success()).isFalse();
    assertThat(result.reason()).contains("401");
  }

  @Test
  void anUnreachableEndpointIsReportedAsFailureNotAnException() {
    // Nothing is listening on this port: connection refused, but still a clean PublishResult.
    PublishConfig config =
        new PublishConfig(URI.create("http://127.0.0.1:1"), null, Duration.ofSeconds(2));

    PublishResult result = uploader.publish(scanResult(), config);

    assertThat(result.success()).isFalse();
    assertThat(result.reason()).isNotBlank();
  }

  @Test
  void anApiKeyIsSentAsABearerAuthorizationHeader() throws IOException {
    AtomicReference<String> receivedAuthHeader = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          receivedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
          exchange.getRequestBody().readAllBytes();
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();

    PublishConfig config =
        new PublishConfig(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            "secret-key",
            Duration.ofSeconds(5));
    uploader.publish(scanResult(), config);

    assertThat(receivedAuthHeader.get()).isEqualTo("Bearer secret-key");
  }

  @Test
  void usesAnExplicitHttpClientAndMapperWhenProvided() throws IOException {
    AtomicReference<String> receivedBody = new AtomicReference<>();
    server = startServer(200, receivedBody);
    ResultUploader explicit =
        new HttpResultUploader(
            HttpClient.newHttpClient(), com.debthunter.output.DeterministicObjectMapper.create());

    PublishResult result = explicit.publish(scanResult(), configFor(server.getAddress().getPort()));

    assertThat(result.success()).isTrue();
  }

  private HttpServer startServer(int statusCode, AtomicReference<String> receivedBody)
      throws IOException {
    HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    httpServer.createContext(
        "/",
        exchange -> {
          byte[] body = exchange.getRequestBody().readAllBytes();
          receivedBody.set(new String(body, java.nio.charset.StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(statusCode, -1);
          try (OutputStream ignored = exchange.getResponseBody()) {
            // no body
          }
        });
    httpServer.start();
    return httpServer;
  }

  private PublishConfig configFor(int port) {
    return new PublishConfig(URI.create("http://127.0.0.1:" + port), null, Duration.ofSeconds(5));
  }

  private ScanResult scanResult() {
    AnalysisRun run =
        AnalysisRun.builder()
            .id("run-1")
            .toolVersion("0.1.0-test")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .repository("/repo")
            .commit("abc123")
            .historyDepth(HistoryDepth.FULL)
            .build();
    return new ScanResult(run, List.of(), Map.of(), PolicyResult.passed("unversioned"));
  }
}
