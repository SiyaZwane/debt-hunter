package com.debthunter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.testkit.FixtureRepoBuilder;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
class GitHistoryProviderTest {

  private final GitHistoryProvider provider = new GitHistoryProvider();
  private FixtureRepoBuilder fixture;

  @AfterEach
  void cleanup() {
    if (fixture != null) {
      fixture.close();
    }
  }

  @Test
  void ac01_inspectReportsGitRepoWithCommitCountAndHead() {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("a.txt", "one", "first commit")
            .commitFile("b.txt", "two", "second commit")
            .commitFile("c.txt", "three", "third commit");

    RepositoryInfo info = provider.inspect(fixture.path());

    assertThat(info.isGitRepo()).isTrue();
    assertThat(info.isShallow()).isFalse();
    assertThat(info.isGrafted()).isFalse();
    assertThat(info.commitCount()).isEqualTo(3);
    assertThat(info.headCommit()).isNotBlank();
    assertThat(info.headBranch()).isEqualTo("main");
  }

  @Test
  void inspectOnNonGitDirectoryReturnsNotAGitRepository(@TempDir Path plainDirectory) {
    RepositoryInfo info = provider.inspect(plainDirectory);

    assertThat(info).isEqualTo(RepositoryInfo.notAGitRepository());
  }

  @Test
  void inspectOnFreshlyInitialisedRepoWithNoCommitsIsGracefullyHandled() {
    fixture = FixtureRepoBuilder.init();

    RepositoryInfo info = provider.inspect(fixture.path());

    assertThat(info.isGitRepo()).isTrue();
    assertThat(info.commitCount()).isZero();
    assertThat(info.headCommit()).isNull();
    assertThat(info.headBranch()).isEqualTo("main");
  }

  @Test
  void historyReturnsCommitsMostRecentFirst() {
    fixture =
        FixtureRepoBuilder.init()
            .commitFile("a.txt", "one", "first commit")
            .commitFile("a.txt", "two", "second commit")
            .commitFile("a.txt", "three", "third commit");

    List<CommitInfo> history = provider.history(fixture.path(), HistoryWindow.all());

    assertThat(history).hasSize(3);
    assertThat(history.get(0).message()).isEqualTo("third commit");
    assertThat(history.get(2).message()).isEqualTo("first commit");
    assertThat(history.get(0).authorEmail()).isEqualTo("fixture@example.com");
  }

  @Test
  void historyRespectsMaxCommitsBound() {
    fixture = FixtureRepoBuilder.init().hotspot("hot.txt", 5);

    List<CommitInfo> history = provider.history(fixture.path(), new HistoryWindow(null, 2));

    assertThat(history).hasSize(2);
  }

  @Test
  void historyOnNonGitDirectoryReturnsEmptyList(@TempDir Path plainDirectory) {
    List<CommitInfo> history = provider.history(plainDirectory, HistoryWindow.all());

    assertThat(history).isEmpty();
  }
}
