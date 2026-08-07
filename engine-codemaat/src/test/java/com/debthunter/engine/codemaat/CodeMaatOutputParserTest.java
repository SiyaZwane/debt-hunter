package com.debthunter.engine.codemaat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CodeMaatOutputParserTest {

  private final CodeMaatOutputParser parser = new CodeMaatOutputParser();

  @Test
  void parsesRevisions() {
    String csv = "entity,n-revs\nsrc/Foo.java,15\nsrc/Bar.java,3\n";

    var rows = parser.parseRevisions(csv);

    assertThat(rows)
        .containsExactly(new RevisionsRow("src/Foo.java", 15), new RevisionsRow("src/Bar.java", 3));
  }

  @Test
  void parsesCoupling() {
    String csv = "entity,coupled,degree,average-revs\nsrc/Foo.java,src/Bar.java,75,10\n";

    var rows = parser.parseCoupling(csv);

    assertThat(rows).containsExactly(new CouplingRow("src/Foo.java", "src/Bar.java", 75, 10));
  }

  @Test
  void parsesAge() {
    String csv = "entity,age-months\nsrc/Foo.java,2\n";

    var rows = parser.parseAge(csv);

    assertThat(rows).containsExactly(new AgeRow("src/Foo.java", 2));
  }

  @Test
  void parsesAuthors() {
    String csv = "entity,n-authors,n-revs\nsrc/Foo.java,1,5\n";

    var rows = parser.parseAuthors(csv);

    assertThat(rows).containsExactly(new AuthorsRow("src/Foo.java", 1, 5));
  }

  @Test
  void ignoresBlankLines() {
    String csv = "entity,n-revs\n\nsrc/Foo.java,15\n\n";

    var rows = parser.parseRevisions(csv);

    assertThat(rows).containsExactly(new RevisionsRow("src/Foo.java", 15));
  }

  @Test
  void emptyBodyProducesEmptyList() {
    String csv = "entity,n-revs\n";

    assertThat(parser.parseRevisions(csv)).isEmpty();
  }

  @Test
  void rejectsWrongHeader() {
    String csv = "path,revisions\nsrc/Foo.java,15\n";

    assertThatThrownBy(() -> parser.parseRevisions(csv))
        .isInstanceOf(CodeMaatParseException.class)
        .hasMessageContaining("entity,n-revs");
  }

  @Test
  void rejectsWrongColumnCount() {
    String csv = "entity,n-revs\nsrc/Foo.java,15,extra\n";

    assertThatThrownBy(() -> parser.parseRevisions(csv)).isInstanceOf(CodeMaatParseException.class);
  }

  @Test
  void rejectsNonNumericColumn() {
    String csv = "entity,n-revs\nsrc/Foo.java,not-a-number\n";

    assertThatThrownBy(() -> parser.parseRevisions(csv)).isInstanceOf(CodeMaatParseException.class);
  }

  @Test
  void rejectsEmptyOutput() {
    assertThatThrownBy(() -> parser.parseRevisions("")).isInstanceOf(CodeMaatParseException.class);
  }
}
