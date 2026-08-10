package com.debthunter.application.scan;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.application.scan.ProjectSlicer.ProjectSpec;
import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectSlicerTest {

  private final ProjectSlicer slicer = new ProjectSlicer();

  @Test
  void noConfiguredProjectsProducesAnEmptyMap() {
    Map<String, List<Finding>> sliced =
        slicer.slice(List.of(finding("f-1", "frontend/App.java")), List.of());

    assertThat(sliced).isEmpty();
  }

  @Test
  void findingsAreGroupedByTheirMatchingProjectPrefix() {
    Finding frontendFinding = finding("f-1", "frontend/App.java");
    Finding backendFinding = finding("f-2", "backend/Server.java");

    Map<String, List<Finding>> sliced =
        slicer.slice(
            List.of(frontendFinding, backendFinding),
            List.of(
                new ProjectSpec("frontend", "frontend"), new ProjectSpec("backend", "backend")));

    assertThat(sliced.get("frontend")).containsExactly(frontendFinding);
    assertThat(sliced.get("backend")).containsExactly(backendFinding);
    assertThat(sliced).doesNotContainKey(ProjectSlicer.DEFAULT_PROJECT);
  }

  @Test
  void aConfiguredProjectWithNoMatchingFindingsStillAppearsWithAnEmptyList() {
    Map<String, List<Finding>> sliced =
        slicer.slice(List.of(), List.of(new ProjectSpec("frontend", "frontend")));

    assertThat(sliced).containsKey("frontend");
    assertThat(sliced.get("frontend")).isEmpty();
  }

  @Test
  void aFindingMatchingNoProjectIsAttributedToTheDefaultProject() {
    Finding unmatched = finding("f-1", "shared/Common.java");

    Map<String, List<Finding>> sliced =
        slicer.slice(List.of(unmatched), List.of(new ProjectSpec("frontend", "frontend")));

    assertThat(sliced.get(ProjectSlicer.DEFAULT_PROJECT)).containsExactly(unmatched);
  }

  @Test
  void theDefaultProjectIsAbsentWhenEveryFindingMatchesAConfiguredProject() {
    Finding frontendFinding = finding("f-1", "frontend/App.java");

    Map<String, List<Finding>> sliced =
        slicer.slice(List.of(frontendFinding), List.of(new ProjectSpec("frontend", "frontend")));

    assertThat(sliced).doesNotContainKey(ProjectSlicer.DEFAULT_PROJECT);
  }

  @Test
  void aFileNamedExactlyLikeAProjectPrefixMatchesThatProject() {
    Finding exactMatch = finding("f-1", "frontend");

    Map<String, List<Finding>> sliced =
        slicer.slice(List.of(exactMatch), List.of(new ProjectSpec("frontend", "frontend")));

    assertThat(sliced.get("frontend")).containsExactly(exactMatch);
  }

  @Test
  void aPatternContainingAWildcardIsMatchedAsAGlob() {
    Finding matchingJava = finding("f-1", "frontend/src/App.java");
    Finding nonMatchingText = finding("f-2", "frontend/README.md");

    Map<String, List<Finding>> sliced =
        slicer.slice(
            List.of(matchingJava, nonMatchingText),
            List.of(new ProjectSpec("frontend-java", "frontend/src/*.java")));

    assertThat(sliced.get("frontend-java")).containsExactly(matchingJava);
    assertThat(sliced.get(ProjectSlicer.DEFAULT_PROJECT)).containsExactly(nonMatchingText);
  }

  @Test
  void projectsAreCheckedInDeclarationOrderSoAnEarlierPatternWins() {
    Finding finding = finding("f-1", "monorepo/frontend/App.java");

    Map<String, List<Finding>> sliced =
        slicer.slice(
            List.of(finding),
            List.of(
                new ProjectSpec("monorepo", "monorepo"),
                new ProjectSpec("frontend", "monorepo/frontend")));

    assertThat(sliced.get("monorepo")).containsExactly(finding);
    assertThat(sliced.get("frontend")).isEmpty();
  }

  private Finding finding(String id, String path) {
    return Finding.builder()
        .id(id)
        .ruleId("rule")
        .category(Category.STATIC_ANALYSIS)
        .severity(Severity.MEDIUM)
        .path(path)
        .message("msg")
        .fingerprint("fp-" + id)
        .build();
  }
}
