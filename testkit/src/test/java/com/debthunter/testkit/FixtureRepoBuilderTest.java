package com.debthunter.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixtureRepoBuilderTest {

  @Test
  void initCreatesAFreshRepositoryThatCloseDeletes() {
    Path path;
    try (FixtureRepoBuilder fixture = FixtureRepoBuilder.init()) {
      path = fixture.path();
      assertThat(path.resolve(".git")).exists();
    }
    assertThat(path).doesNotExist();
  }

  @Test
  void initAtUsesACallerOwnedDirectoryAndLeavesItOnDisk(@TempDir Path directory) {
    try (FixtureRepoBuilder fixture = FixtureRepoBuilder.initAt(directory)) {
      assertThat(fixture.path()).isEqualTo(directory);
    }
    assertThat(directory).exists();
  }

  @Test
  void commitFileWritesAndCommitsContent() throws Exception {
    try (FixtureRepoBuilder fixture = FixtureRepoBuilder.init()) {
      fixture.commitFile("Foo.java", "class Foo {}", "add Foo");

      assertThat(fixture.path().resolve("Foo.java")).exists();
      assertThat(Files.readString(fixture.path().resolve("Foo.java"))).isEqualTo("class Foo {}");
      assertThat(commitCount(fixture)).isEqualTo(1);
    }
  }

  @Test
  void renameFileMovesTheFileAndCommitsTheRename() {
    try (FixtureRepoBuilder fixture = FixtureRepoBuilder.init()) {
      fixture.commitFile("Original.java", "class Original {}", "add Original");

      fixture.renameFile("Original.java", "Renamed.java", "rename");

      assertThat(fixture.path().resolve("Original.java")).doesNotExist();
      assertThat(fixture.path().resolve("Renamed.java")).exists();
    }
  }

  @Test
  void deleteFileRemovesTheFileAndCommitsTheDeletion() {
    try (FixtureRepoBuilder fixture = FixtureRepoBuilder.init()) {
      fixture.commitFile("Foo.java", "class Foo {}", "add Foo");

      fixture.deleteFile("Foo.java", "remove Foo");

      assertThat(fixture.path().resolve("Foo.java")).doesNotExist();
    }
  }

  @Test
  void hotspotCommitsTheSameFileRepeatedly() throws Exception {
    try (FixtureRepoBuilder fixture = FixtureRepoBuilder.init()) {
      fixture.hotspot("Hot.java", 3);

      assertThat(commitCount(fixture)).isEqualTo(3);
    }
  }

  @Test
  void commitFileCreatesParentDirectoriesAsNeeded() {
    try (FixtureRepoBuilder fixture = FixtureRepoBuilder.init()) {
      fixture.commitFile("src/main/Foo.java", "class Foo {}", "add nested Foo");

      assertThat(fixture.path().resolve("src/main/Foo.java")).exists();
    }
  }

  @Test
  void cloneShallowProducesARealShallowClone(@TempDir Path cloneDir) {
    try (FixtureRepoBuilder fixture = FixtureRepoBuilder.init()) {
      fixture
          .commitFile("Foo.java", "class Foo {}", "add Foo")
          .commitFile("Bar.java", "class Bar {}", "add Bar")
          .commitFile("Baz.java", "class Baz {}", "add Baz");

      try (FixtureRepoBuilder shallow = fixture.cloneShallow(cloneDir.resolve("clone"), 1)) {
        assertThat(shallow.path().resolve(".git/shallow")).exists();
      }
    }
  }

  @Test
  void cloneShallowRejectsANonPositiveDepth(@TempDir Path cloneDir) {
    try (FixtureRepoBuilder fixture = FixtureRepoBuilder.init()) {
      fixture.commitFile("Foo.java", "class Foo {}", "add Foo");

      // git itself rejects "--depth 0" as invalid, exercising cloneShallow's real failure path
      // rather than a contrived one.
      assertThatThrownBy(() -> fixture.cloneShallow(cloneDir.resolve("clone"), 0))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  private int commitCount(FixtureRepoBuilder fixture) throws Exception {
    int count = 0;
    for (var ignored : fixture.git().log().call()) {
      count++;
    }
    return count;
  }
}
