package com.debthunter.integration;

import com.debthunter.domain.ScanResult;
import com.debthunter.output.DeterministicObjectMapper;
import com.debthunter.output.JsonReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Publishes a scan result over HTTP: a single fire-and-forget {@code POST}, no retry loop. A
 * failure — network error, timeout, or a non-2xx response — is reported back as a {@link
 * PublishResult}, never thrown; publication is never allowed to be more disruptive than the scan
 * it's reporting on.
 */
public final class HttpResultUploader implements ResultUploader {

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  /** Creates an uploader using a default {@link HttpClient} and the shared deterministic mapper. */
  public HttpResultUploader() {
    this(HttpClient.newHttpClient(), DeterministicObjectMapper.create());
  }

  /**
   * Creates an uploader with explicit collaborators, for testing.
   *
   * @param httpClient the client to send requests with
   * @param objectMapper the mapper to serialise the result with
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "collaborators are stateless services, shared by reference intentionally")
  public HttpResultUploader(HttpClient httpClient, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public PublishResult publish(ScanResult result, PublishConfig config) {
    String body;
    try {
      body = objectMapper.writeValueAsString(JsonReport.of(result));
    } catch (JsonProcessingException e) {
      return PublishResult.ofFailure("Failed to serialise result: " + e.getMessage());
    }

    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(config.endpoint())
            .timeout(config.timeout())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (config.apiKey() != null) {
      requestBuilder.header("Authorization", "Bearer " + config.apiKey());
    }

    try {
      HttpResponse<Void> response =
          httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return PublishResult.ofSuccess();
      }
      return PublishResult.ofFailure("Publish endpoint returned HTTP " + response.statusCode());
    } catch (IOException e) {
      return PublishResult.ofFailure("Failed to publish: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return PublishResult.ofFailure("Interrupted while publishing");
    }
  }
}
