package com.debthunter.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-21: Debt Hunter's output is consumable by an Azure DevOps pipeline. Azure DevOps has no direct
 * "push a SARIF file" API analogous to GitHub's code-scanning endpoint — pipelines ingest SARIF
 * from build artifacts via the SARIF SAST Scans Tab extension instead, which needs a live build
 * context this unit test doesn't have. What's genuinely testable here, without requiring a whole
 * pipeline run, is that the configured organization/project/PAT can actually authenticate against
 * the Azure DevOps REST API this project would need for any future artifact-publishing step — a
 * real, opt-in connectivity check, not a simulated one.
 *
 * <p>To run it:
 *
 * <pre>{@code
 * export DEBTHUNTER_TEST_AZURE_DEVOPS_ORG=my-org
 * export DEBTHUNTER_TEST_AZURE_DEVOPS_PROJECT=my-project
 * export DEBTHUNTER_TEST_AZURE_DEVOPS_PAT=...          # needs at least Project read access
 * mvn -pl output -am test -Dgroups=platform -DexcludedGroups=
 * }</pre>
 */
@Tag("platform")
class AC21_AzureDevOpsUploadTest {

  @Test
  void ac21_configuredCredentialsCanAuthenticateAgainstAzureDevOps() throws Exception {
    String org = System.getenv("DEBTHUNTER_TEST_AZURE_DEVOPS_ORG");
    String project = System.getenv("DEBTHUNTER_TEST_AZURE_DEVOPS_PROJECT");
    String pat = System.getenv("DEBTHUNTER_TEST_AZURE_DEVOPS_PAT");
    assumeTrue(
        org != null && project != null && pat != null,
        "Set DEBTHUNTER_TEST_AZURE_DEVOPS_ORG/_PROJECT/_PAT to run this manually");

    String credential =
        Base64.getEncoder().encodeToString((":" + pat).getBytes(StandardCharsets.UTF_8));
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    "https://dev.azure.com/"
                        + org
                        + "/_apis/projects/"
                        + project
                        + "?api-version=7.0"))
            .header("Authorization", "Basic " + credential)
            .GET()
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as("Azure DevOps response: %s", response.body())
        .isEqualTo(200);
  }
}
