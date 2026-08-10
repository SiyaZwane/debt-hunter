package com.debthunter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.testkit.FixtureRepoBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A real {@code git clone --depth 1} is detected as shallow, and a full clone is not. Uses a real
 * native {@code git} subprocess to produce a genuinely truncated {@code .git/shallow} marker,
 * rather than synthesising one — proving the actual detection mechanism, not a stand-in for it.
 */
@Tag("integration")
class ShallowDetectionTest {

  private final GitHistoryProvider provider = new GitHistoryProvider();

  private FixtureRepoBuilder source;
  private FixtureRepoBuilder clone;

  @AfterEach
  void cleanup() {
    if (clone != null) {
      clone.close();
    }
    if (source != null) {
      source.close();
    }
  }

  @Test
  void aDepthOneCloneIsDetectedAsShallow(@TempDir Path cloneDir) {
    source =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo {}", "add Foo")
            .commitFile("Foo.java", "class Foo { void a() {} }", "touch Foo")
            .commitFile("Foo.java", "class Foo { void a() {} void b() {} }", "touch Foo again");

    clone = source.cloneShallow(cloneDir.resolve("clone"), 1);

    RepositoryInfo info = provider.inspect(clone.path());

    assertThat(info.isGitRepo()).isTrue();
    assertThat(info.isShallow()).isTrue();
    assertThat(info.commitCount()).isEqualTo(1);
  }

  @Test
  void aRepositoryWithNoDepthLimitIsNotShallow() {
    source =
        FixtureRepoBuilder.init()
            .commitFile("Foo.java", "class Foo {}", "add Foo")
            .commitFile("Foo.java", "class Foo { void a() {} }", "touch Foo");

    RepositoryInfo info = provider.inspect(source.path());

    assertThat(info.isShallow()).isFalse();
    assertThat(info.commitCount()).isEqualTo(2);
  }
}
