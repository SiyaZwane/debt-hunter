package com.debthunter.engine.staticanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.debthunter.domain.Category;
import com.debthunter.domain.Finding;
import com.debthunter.domain.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class StaticAnalysisAdapterTest {

  private final StaticAnalysisAdapter adapter = new StaticAnalysisAdapter();

  @Test
  void parsesASonarQubeIssuesExportIntoCanonicalFindings() {
    String json =
        """
        {
          "total": 1,
          "issues": [
            {
              "key": "AXy1",
              "rule": "java:S1192",
              "severity": "MAJOR",
              "component": "my-project:src/main/java/com/acme/Foo.java",
              "line": 42,
              "message": "Define a constant instead of duplicating this literal.",
              "type": "CODE_SMELL"
            }
          ]
        }
        """;

    List<Finding> findings = adapter.parse(json);

    assertThat(findings).hasSize(1);
    Finding finding = findings.get(0);
    assertThat(finding.category()).isEqualTo(Category.STATIC_ANALYSIS);
    assertThat(finding.ruleId()).isEqualTo("sonar.java:S1192");
    assertThat(finding.severity()).isEqualTo(Severity.MEDIUM);
    assertThat(finding.path()).isEqualTo("src/main/java/com/acme/Foo.java");
    assertThat(finding.startLine()).isEqualTo(42);
    assertThat(finding.message())
        .isEqualTo("Define a constant instead of duplicating this literal.");
    assertThat(finding.evidence()).containsEntry("sonarRule", "java:S1192");
  }

  @Test
  void mapsSonarQubeSeverityLevelsToCanonicalSeverity() {
    assertThat(severityFor("BLOCKER")).isEqualTo(Severity.CRITICAL);
    assertThat(severityFor("CRITICAL")).isEqualTo(Severity.HIGH);
    assertThat(severityFor("MAJOR")).isEqualTo(Severity.MEDIUM);
    assertThat(severityFor("MINOR")).isEqualTo(Severity.LOW);
    assertThat(severityFor("INFO")).isEqualTo(Severity.INFO);
  }

  @Test
  void anEmptyIssuesListProducesNoFindings() {
    List<Finding> findings = adapter.parse("{\"total\": 0, \"issues\": []}");

    assertThat(findings).isEmpty();
  }

  @Test
  void anIssueWithNoLineIsFileScoped() {
    String json =
        """
        {"issues":[{"key":"AXy2","rule":"java:S1234","severity":"MINOR",
          "component":"my-project:Foo.java","message":"File-level issue"}]}
        """;

    List<Finding> findings = adapter.parse(json);

    assertThat(findings.get(0).startLine()).isZero();
  }

  @Test
  void twoIssuesForTheSameRuleAndPathProduceDistinctFingerprints() {
    String json =
        """
        {"issues":[
          {"key":"AXy1","rule":"java:S1192","severity":"MAJOR","component":"p:Foo.java","line":10,"message":"m1"},
          {"key":"AXy2","rule":"java:S1192","severity":"MAJOR","component":"p:Foo.java","line":20,"message":"m2"}
        ]}
        """;

    List<Finding> findings = adapter.parse(json);

    assertThat(findings).hasSize(2);
    assertThat(findings.get(0).fingerprint()).isNotEqualTo(findings.get(1).fingerprint());
  }

  @Test
  void malformedJsonThrowsAParseException() {
    assertThatThrownBy(() -> adapter.parse("{not valid json"))
        .isInstanceOf(StaticAnalysisParseException.class);
  }

  private Severity severityFor(String sonarSeverity) {
    String json =
        "{\"issues\":[{\"key\":\"k\",\"rule\":\"r\",\"severity\":\""
            + sonarSeverity
            + "\",\"component\":\"p:Foo.java\",\"message\":\"m\"}]}";
    return adapter.parse(json).get(0).severity();
  }
}
