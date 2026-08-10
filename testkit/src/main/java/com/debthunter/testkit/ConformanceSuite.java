package com.debthunter.testkit;

import java.util.List;

/**
 * A small, deliberately varied set of repository shapes for {@link ConformanceRunner} to check
 * conformance against: a single file, multiple files, and a rename — shapes chosen to exercise
 * different code paths (fingerprinting, history-dependent categories) without requiring a large
 * fixture repository.
 */
public final class ConformanceSuite {

  private ConformanceSuite() {}

  /**
   * The suite's fixtures.
   *
   * @return every fixture this suite defines
   */
  public static List<ConformanceFixture> fixtures() {
    return List.of(
        new ConformanceFixture(
            "single-file", builder -> builder.commitFile("Foo.java", "class Foo {}", "add Foo")),
        new ConformanceFixture(
            "multiple-files",
            builder ->
                builder
                    .commitFile("Foo.java", "class Foo {}", "add Foo")
                    .commitFile("Bar.java", "class Bar {}", "add Bar")
                    .commitFile("Baz.java", "class Baz {}", "add Baz")),
        new ConformanceFixture(
            "renamed-file",
            builder ->
                builder
                    .commitFile("Original.java", "class Original {}", "add Original")
                    .renameFile("Original.java", "Renamed.java", "rename Original to Renamed")));
  }
}
