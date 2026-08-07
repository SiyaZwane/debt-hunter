package com.debthunter.testkit;

import java.nio.file.Path;

/**
 * Standalone entry point for shell scripts (e.g. {@code scripts/smoke-test-container.sh}) that need
 * a real Git fixture on disk but can't call {@link FixtureRepoBuilder} directly.
 */
public final class FixtureRepoCli {

  private FixtureRepoCli() {}

  /**
   * Creates a small fixture repository with a couple of commits at the given directory.
   *
   * @param args a single argument: the directory to create the repository in
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("Usage: FixtureRepoCli <target-directory>");
      System.exit(2);
    }
    try (FixtureRepoBuilder fixture = FixtureRepoBuilder.initAt(Path.of(args[0]))) {
      fixture
          .commitFile("Foo.java", "class Foo {}" + System.lineSeparator(), "add Foo")
          .commitFile("Bar.java", "class Bar {}" + System.lineSeparator(), "add Bar");
    }
  }
}
