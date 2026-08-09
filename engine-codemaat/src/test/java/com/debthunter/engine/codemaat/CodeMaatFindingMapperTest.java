package com.debthunter.engine.codemaat;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeMaatFindingMapperTest {

  private final CodeMaatFindingMapper mapper = new CodeMaatFindingMapper();

  // Not a Git repository, so RenameTracker falls back to returning entities unchanged — exactly
  // what these pure mapping tests want, without paying for a real git subprocess per row.
  @TempDir private Path repoPath;

  @Test
  void revisionsBelowThresholdProduceNoFindings() {
    var findings = mapper.mapRevisions(repoPath, List.of(new RevisionsRow("Foo.java", 1)));

    assertThat(findings).isEmpty();
  }

  @Test
  void revisionsAtChurnThresholdProduceOnlyChurnFinding() {
    var findings =
        mapper.mapRevisions(
            repoPath,
            List.of(new RevisionsRow("Foo.java", CodeMaatFindingMapper.CHURN_MIN_REVISIONS)));

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).category()).isEqualTo(Category.CHURN);
    assertThat(findings.get(0).path()).isEqualTo("Foo.java");
  }

  @Test
  void revisionsAtHotspotThresholdProduceBothChurnAndHotspotFindings() {
    var findings =
        mapper.mapRevisions(
            repoPath,
            List.of(new RevisionsRow("Foo.java", CodeMaatFindingMapper.HOTSPOT_MIN_REVISIONS)));

    assertThat(findings).hasSize(2);
    assertThat(findings)
        .extracting(Finding::category)
        .containsExactlyInAnyOrder(Category.CHURN, Category.HOTSPOT);
  }

  @Test
  void hotspotSeverityEscalatesAtHighRevisionCount() {
    var findings = mapper.mapRevisions(repoPath, List.of(new RevisionsRow("Foo.java", 30)));
    Finding hotspot =
        findings.stream().filter(f -> f.category() == Category.HOTSPOT).findFirst().orElseThrow();

    assertThat(hotspot.severity()).isEqualTo(Severity.CRITICAL);
  }

  @Test
  void couplingBelowThresholdProducesNoFindings() {
    var findings =
        mapper.mapCoupling(repoPath, List.of(new CouplingRow("A.java", "B.java", 10, 5)));

    assertThat(findings).isEmpty();
  }

  @Test
  void couplingAtThresholdProducesFinding() {
    var findings =
        mapper.mapCoupling(
            repoPath,
            List.of(
                new CouplingRow("A.java", "B.java", CodeMaatFindingMapper.COUPLING_MIN_DEGREE, 8)));

    assertThat(findings).hasSize(1);
    Finding finding = findings.get(0);
    assertThat(finding.category()).isEqualTo(Category.TEMPORAL_COUPLING);
    assertThat(finding.path()).isEqualTo("A.java");
    assertThat(finding.evidence()).containsEntry("coupled", "B.java");
  }

  @Test
  void couplingSeverityEscalatesAtHighDegree() {
    var findings =
        mapper.mapCoupling(repoPath, List.of(new CouplingRow("A.java", "B.java", 90, 8)));

    assertThat(findings.get(0).severity()).isEqualTo(Severity.HIGH);
  }

  @Test
  void authorsWithManyContributorsProduceNoFindings() {
    var findings = mapper.mapAuthors(repoPath, List.of(new AuthorsRow("Foo.java", 5, 20)));

    assertThat(findings).isEmpty();
  }

  @Test
  void authorsWithLowActivityProduceNoFindingsDespiteSingleAuthor() {
    var findings = mapper.mapAuthors(repoPath, List.of(new AuthorsRow("Foo.java", 1, 1)));

    assertThat(findings).isEmpty();
  }

  @Test
  void singleAuthorWithSufficientActivityProducesKnowledgeConcentrationFinding() {
    var findings =
        mapper.mapAuthors(
            repoPath,
            List.of(
                new AuthorsRow(
                    "Foo.java",
                    CodeMaatFindingMapper.KNOWLEDGE_MAX_AUTHORS,
                    CodeMaatFindingMapper.KNOWLEDGE_MIN_REVISIONS)));

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).category()).isEqualTo(Category.KNOWLEDGE_CONCENTRATION);
  }

  @Test
  void ageRowsProduceMetricsNotFindings() {
    var metrics = mapper.mapAge(List.of(new AgeRow("Foo.java", 6)));

    assertThat(metrics).hasSize(1);
    assertThat(metrics.get("age:Foo.java").value()).isEqualTo(6.0);
  }

  @Test
  void fingerprintsAreDeterministicForTheSameInput() {
    var first = mapper.mapRevisions(repoPath, List.of(new RevisionsRow("Foo.java", 5))).get(0);
    var second = mapper.mapRevisions(repoPath, List.of(new RevisionsRow("Foo.java", 5))).get(0);

    assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    assertThat(first.id()).isEqualTo(second.id());
  }
}
