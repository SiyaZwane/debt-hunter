package com.debthunter.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Opens pull requests against a source-hosting API over HTTP: a single attempt, no retries. Any
 * failure — unreachable endpoint, non-2xx response, interruption — becomes a rejected {@link
 * PullRequestResult} rather than a thrown exception. On success, the response body is the pull
 * request's URL.
 */
public final class HttpSourceHostClient implements SourceHostClient {

  private final URI endpoint;
  private final String apiKey;
  private final Duration timeout;
  private final HttpClient httpClient;

  /**
   * Creates a client using the JDK's default {@link HttpClient}.
   *
   * @param endpoint the source-hosting API's pull-request-creation endpoint
   * @param apiKey bearer API key, or {@code null} for none
   * @param timeout request timeout
   */
  public HttpSourceHostClient(URI endpoint, String apiKey, Duration timeout) {
    this(endpoint, apiKey, timeout, HttpClient.newHttpClient());
  }

  /**
   * Creates a client using a caller-supplied {@link HttpClient}, for testing.
   *
   * @param endpoint the source-hosting API's pull-request-creation endpoint
   * @param apiKey bearer API key, or {@code null} for none
   * @param timeout request timeout
   * @param httpClient the client to send requests with
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "HttpClient is a stateless, shared service, not owned mutable state")
  public HttpSourceHostClient(
      URI endpoint, String apiKey, Duration timeout, HttpClient httpClient) {
    this.endpoint = endpoint;
    this.apiKey = apiKey;
    this.timeout = timeout;
    this.httpClient = httpClient;
  }

  @Override
  public PullRequestResult openPullRequest(PullRequestRequest request) {
    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(endpoint)
            .timeout(timeout)
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(request), StandardCharsets.UTF_8));
    if (apiKey != null) {
      requestBuilder.header("Authorization", "Bearer " + apiKey);
    }

    try {
      HttpResponse<String> response =
          httpClient.send(
              requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return PullRequestResult.created(response.body());
      }
      return PullRequestResult.rejected("Source host returned HTTP " + response.statusCode());
    } catch (IOException e) {
      return PullRequestResult.rejected("Failed to reach source host: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return PullRequestResult.rejected("Interrupted while opening pull request");
    }
  }

  private String toJson(PullRequestRequest request) {
    String labels =
        request.labels().stream()
            .map(label -> "\"" + escape(label) + "\"")
            .collect(Collectors.joining(","));
    return "{"
        + "\"repository\":\""
        + escape(request.repository())
        + "\","
        + "\"branchName\":\""
        + escape(request.branchName())
        + "\","
        + "\"baseBranch\":\""
        + escape(request.baseBranch())
        + "\","
        + "\"title\":\""
        + escape(request.title())
        + "\","
        + "\"description\":\""
        + escape(request.description())
        + "\","
        + "\"patch\":\""
        + escape(request.patch())
        + "\","
        + "\"labels\":["
        + labels
        + "]}";
  }

  private String escape(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }
}
