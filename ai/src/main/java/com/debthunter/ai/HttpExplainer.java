package com.debthunter.ai;

import com.debthunter.domain.Finding;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Requests an explanation from an AI service over HTTP: a single attempt, no retries. Any failure —
 * unreachable endpoint, non-2xx response, interruption — becomes an unavailable {@link Explanation}
 * rather than a thrown exception, since this command's outcome must never surprise a caller with an
 * uncaught error.
 */
public final class HttpExplainer implements Explainer {

  private final HttpClient httpClient;

  /** Creates an explainer using the JDK's default {@link HttpClient}. */
  public HttpExplainer() {
    this(HttpClient.newHttpClient());
  }

  /**
   * Creates an explainer using a caller-supplied client, for testing.
   *
   * @param httpClient the client to send requests with
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "HttpClient is a stateless, shared service, not owned mutable state")
  public HttpExplainer(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Explanation explain(Finding finding, ExplainConfig config) {
    String prompt =
        "Explain this technical-debt finding in plain language.\n"
            + "rule: "
            + finding.ruleId()
            + "\n"
            + "path: "
            + finding.path()
            + "\n"
            + "message: "
            + finding.message();

    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(config.endpoint())
            .timeout(config.timeout())
            .header("Content-Type", "text/plain; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(prompt, StandardCharsets.UTF_8));
    if (config.apiKey() != null) {
      requestBuilder.header("Authorization", "Bearer " + config.apiKey());
    }

    try {
      HttpResponse<String> response =
          httpClient.send(
              requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return Explanation.ofAvailable(finding.id(), response.body());
      }
      return Explanation.ofUnavailable(
          finding.id(), "Explain endpoint returned HTTP " + response.statusCode());
    } catch (IOException e) {
      return Explanation.ofUnavailable(
          finding.id(), "Failed to reach explain endpoint: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Explanation.ofUnavailable(finding.id(), "Interrupted while explaining");
    }
  }
}
