package com.debthunter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Exercises {@link HttpExplainer} against a real local HTTP server — no mocked transport. */
class HttpExplainerTest {

  private final Explainer explainer = new HttpExplainer();
  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void aSuccessfulResponseIsReportedAsAnAvailableExplanation() throws IOException {
    AtomicReference<String> receivedBody = new AtomicReference<>();
    server =
        startServer(200, "This method changes often because it mixes two concerns.", receivedBody);

    Explanation explanation =
        explainer.explain(finding(), configFor(server.getAddress().getPort()));

    assertThat(explanation.available()).isTrue();
    assertThat(explanation.text())
        .isEqualTo("This method changes often because it mixes two concerns.");
    assertThat(receivedBody.get()).contains("rule: hotspot.rule").contains("path: Foo.java");
  }

  @Test
  void aServerErrorResponseIsReportedAsUnavailable() throws IOException {
    server = startServer(500, "", new AtomicReference<>());

    Explanation explanation =
        explainer.explain(finding(), configFor(server.getAddress().getPort()));

    assertThat(explanation.available()).isFalse();
    assertThat(explanation.text()).contains("500");
  }

  @Test
  void anUnreachableEndpointIsReportedAsUnavailableNotAnException() {
    ExplainConfig config =
        new ExplainConfig(URI.create("http://127.0.0.1:1"), null, Duration.ofSeconds(2));

    Explanation explanation = explainer.explain(finding(), config);

    assertThat(explanation.available()).isFalse();
    assertThat(explanation.text()).isNotBlank();
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
          byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    server.start();

    ExplainConfig config =
        new ExplainConfig(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            "secret-key",
            Duration.ofSeconds(5));
    explainer.explain(finding(), config);

    assertThat(receivedAuthHeader.get()).isEqualTo("Bearer secret-key");
  }

  @Test
  void usesAnExplicitHttpClientWhenProvided() throws IOException {
    AtomicReference<String> receivedBody = new AtomicReference<>();
    server = startServer(200, "ok", receivedBody);
    Explainer explicit = new HttpExplainer(HttpClient.newHttpClient());

    Explanation explanation = explicit.explain(finding(), configFor(server.getAddress().getPort()));

    assertThat(explanation.available()).isTrue();
  }

  private HttpServer startServer(
      int statusCode, String responseBody, AtomicReference<String> receivedBody)
      throws IOException {
    HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    httpServer.createContext(
        "/",
        exchange -> {
          byte[] requestBody = exchange.getRequestBody().readAllBytes();
          receivedBody.set(new String(requestBody, StandardCharsets.UTF_8));
          byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(statusCode, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    httpServer.start();
    return httpServer;
  }

  private ExplainConfig configFor(int port) {
    return new ExplainConfig(URI.create("http://127.0.0.1:" + port), null, Duration.ofSeconds(5));
  }

  private Finding finding() {
    return Finding.builder()
        .id("f-1")
        .ruleId("hotspot.rule")
        .category(Category.HOTSPOT)
        .severity(Severity.HIGH)
        .path("Foo.java")
        .message("Foo.java changes often")
        .fingerprint("fp-1")
        .build();
  }
}
