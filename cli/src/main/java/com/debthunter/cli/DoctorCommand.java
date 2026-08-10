package com.debthunter.cli;

import com.debthunter.application.scan.ExitCode;
import com.debthunter.domain.HistoryDepth;
import com.debthunter.repository.GitHistoryProvider;
import com.debthunter.repository.RepositoryHistoryProvider;
import com.debthunter.repository.RepositoryInfo;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Diagnoses a repository's readiness for analysis: history depth, shallow/graft status, and what to
 * do about it if history is incomplete.
 */
@Command(name = "doctor", description = "Diagnose a repository's readiness for analysis.")
public final class DoctorCommand implements Callable<Integer> {

  @Option(names = "--repo", description = "Path to the repository to diagnose.")
  private Path repoPath = Path.of(".");

  private final RepositoryHistoryProvider historyProvider;
  private final PrintStream out;

  /** Constructs the command as picocli will: with the default, production collaborators. */
  public DoctorCommand() {
    this(Path.of("."), new GitHistoryProvider(), System.out);
  }

  /**
   * Constructs the command with explicit collaborators and a target path, bypassing picocli option
   * parsing entirely, for testing.
   *
   * @param repoPath repository path to diagnose; defaults to {@code "."} if {@code null}
   * @param historyProvider how to inspect the repository
   * @param out where to print the diagnostic report
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "PrintStream is a system stream / test double, not caller-owned mutable data")
  DoctorCommand(Path repoPath, RepositoryHistoryProvider historyProvider, PrintStream out) {
    this.repoPath = repoPath == null ? Path.of(".") : repoPath;
    this.historyProvider = historyProvider;
    this.out = out;
  }

  @Override
  public Integer call() {
    RepositoryInfo info = historyProvider.inspect(repoPath);
    if (!info.isGitRepo()) {
      out.println("Not a Git repository: " + repoPath);
      return ExitCode.CONFIGURATION_ERROR.code();
    }

    HistoryDepth historyDepth =
        info.isShallow() || info.isGrafted() ? HistoryDepth.SHALLOW : HistoryDepth.FULL;

    out.println("Debt Hunter Doctor");
    out.println("==================");
    out.println("Repository:   " + repoPath);
    out.println("Branch:       " + info.headBranch());
    out.println("Commit:       " + info.headCommit());
    out.println("Commit count: " + info.commitCount());
    out.println("Shallow:      " + info.isShallow());
    out.println("Grafted:      " + info.isGrafted());
    out.println("History depth: " + historyDepth.displayName());
    out.println();

    if (historyDepth == HistoryDepth.FULL) {
      out.println("No issues found: full history is available.");
    } else {
      out.println("Recommendations:");
      out.println("  - Fetch full history: git fetch --unshallow");
      out.println(
          "  - In CI, use a full checkout depth (e.g. actions/checkout with fetch-depth: 0)");
      out.println(
          "  - Until full history is available, hotspot, churn, temporal-coupling, and"
              + " knowledge-concentration findings will have reduced confidence.");
    }

    return ExitCode.POLICY_SATISFIED.code();
  }
}
