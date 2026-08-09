package com.debthunter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.testkit.FixtureRepoBuilder;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises {@link RenameTracker} against real repositories via a real {@code git} subprocess. */
@Tag("integration")
class RenameTrackerTest {

  private final RenameTracker renameTracker = new RenameTracker();
  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void aFileWithNoRenamesResolvesToItself() {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    String canonical = renameTracker.canonicalPath(fixture.path(), "Foo.java");

    assertThat(canonical).isEqualTo("Foo.java");
  }

  @Test
  void aRenamedFileResolvesToItsOriginalName() {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo {}", "add Foo")
            .renameFile("Foo.java", "Bar.java", "rename Foo to Bar");

    String canonical = renameTracker.canonicalPath(fixture.path(), "Bar.java");

    assertThat(canonical).isEqualTo("Foo.java");
  }

  @Test
  void aChainOfRenamesResolvesToTheOriginalName() {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo {}", "add Foo")
            .renameFile("Foo.java", "Bar.java", "rename Foo to Bar")
            .renameFile("Bar.java", "src/Baz.java", "move Bar into src/ as Baz");

    String canonical = renameTracker.canonicalPath(fixture.path(), "src/Baz.java");

    assertThat(canonical).isEqualTo("Foo.java");
  }

  @Test
  void aFileWithNoHistoryFallsBackToItself() {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");

    String canonical = renameTracker.canonicalPath(fixture.path(), "NeverCommitted.java");

    assertThat(canonical).isEqualTo("NeverCommitted.java");
  }

  @Test
  void aPathOutsideAnyGitRepositoryFallsBackToItself(@TempDir Path notARepo) {
    String canonical = renameTracker.canonicalPath(notARepo, "Foo.java");

    assertThat(canonical).isEqualTo("Foo.java");
  }

  @Test
  void aTimedOutProcessFallsBackToItself() {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");
    RenameTracker impatientTracker = new RenameTracker(Duration.ZERO);

    String canonical = impatientTracker.canonicalPath(fixture.path(), "Foo.java");

    assertThat(canonical).isEqualTo("Foo.java");
  }

  @Test
  void beingInterruptedWhileWaitingFallsBackToTheInputPath() {
    fixture = FixtureRepoBuilder.init().commitFile("Foo.java", "class Foo {}", "add Foo");
    Thread.currentThread().interrupt();

    try {
      String canonical = renameTracker.canonicalPath(fixture.path(), "Foo.java");
      assertThat(canonical).isEqualTo("Foo.java");
    } finally {
      // Clear the flag so the interrupt doesn't leak into whichever test runs next.
      Thread.interrupted();
    }
  }
}
