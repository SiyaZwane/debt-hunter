package com.debthunter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * AC-79: a proposed fix is opened as a normal pull request subject to the target repository's own
 * CI pipeline — {@link FixAgent} has no capability to merge, and every pull request it opens says
 * so in its description.
 */
class AC79_CIPipelineSubjectTest {

  @Test
  void ac79_aCreatedPullRequestIsNeverMarkedMergedAndDeclaresItIsSubjectToCi() {
    AtomicReference<PullRequestRequest> captured = new AtomicReference<>();
    SourceHostClient client = mock(SourceHostClient.class);
    when(client.openPullRequest(any()))
        .thenAnswer(
            invocation -> {
              captured.set(invocation.getArgument(0));
              return PullRequestResult.created("https://example.invalid/pulls/7");
            });
    FixAgent agent = new FixAgent(client, repository -> true);

    PullRequestResult result = agent.proposeFix(finding(), "acme/widgets", "main");

    assertThat(result.merged()).isFalse();
    assertThat(captured.get().description())
        .contains("CI pipeline")
        .contains("never merged automatically");
  }

  @Test
  void ac79_aRejectedProposalIsAlsoNeverMarkedMerged() {
    SourceHostClient client = mock(SourceHostClient.class);
    when(client.openPullRequest(any())).thenReturn(PullRequestResult.rejected("HTTP 500"));
    FixAgent agent = new FixAgent(client, repository -> true);

    PullRequestResult result = agent.proposeFix(finding(), "acme/widgets", "main");

    assertThat(result.merged()).isFalse();
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
