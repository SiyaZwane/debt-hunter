package com.debthunter.cli;

import com.debthunter.application.scan.ExitCode;
import com.debthunter.policy.ComposedPolicy;
import com.debthunter.policy.PolicyBundle;
import com.debthunter.policy.PolicyBundleParser;
import com.debthunter.policy.PolicyComposer;
import com.debthunter.policy.PolicyLoosenedException;
import com.debthunter.policy.PolicyParseException;
import com.debthunter.policy.PolicyProvenance;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Shows the effective policy for a repository: the central bundle composed with its repo-local
 * {@value com.debthunter.policy.PolicyComposer#LOCAL_POLICY_FILE_NAME} override, if any, with each
 * field's provenance — exactly what {@code scan} would actually enforce, without running a scan.
 */
@Command(name = "policy", description = "Show the effective (merged) policy for a repository.")
public final class PolicyCommand implements Callable<Integer> {

  @Option(names = "--repo", description = "Path to the repository to check.")
  private Path repoPath = Path.of(".");

  @Option(names = "--policy", description = "Path to the central policy bundle file.")
  private Path policyPath;

  private final PolicyBundleParser policyBundleParser;
  private final PolicyComposer policyComposer;
  private final PrintStream out;

  /** Constructs the command as picocli will: with the default, production collaborators. */
  public PolicyCommand() {
    this(Path.of("."), null, new PolicyBundleParser(), System.out);
  }

  /**
   * Constructs the command with explicit collaborators and options, bypassing picocli option
   * parsing entirely, for testing.
   *
   * @param repoPath repository path to check; defaults to {@code "."} if {@code null}
   * @param policyPath path to the central policy bundle file, or {@code null}
   * @param policyBundleParser parses both the central bundle and any local override
   * @param out where to print the effective policy
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "PrintStream is a system stream / test double, not caller-owned mutable data")
  PolicyCommand(
      Path repoPath, Path policyPath, PolicyBundleParser policyBundleParser, PrintStream out) {
    this.repoPath = repoPath == null ? Path.of(".") : repoPath;
    this.policyPath = policyPath;
    this.policyBundleParser = policyBundleParser;
    this.policyComposer = new PolicyComposer(policyBundleParser);
    this.out = out;
  }

  @Override
  public Integer call() {
    ComposedPolicy composed;
    try {
      PolicyBundle central = policyBundleParser.loadCentral(policyPath);
      composed = policyComposer.compose(repoPath, central);
    } catch (PolicyParseException | PolicyLoosenedException e) {
      System.err.println("Invalid policy: " + e.getMessage());
      return ExitCode.CONFIGURATION_ERROR.code();
    }

    out.println("Effective policy for " + repoPath);
    out.println("================================================");
    for (PolicyProvenance provenance : composed.provenance()) {
      out.println(
          provenance.field() + ": " + provenance.detail() + " [" + provenance.source() + "]");
    }

    return ExitCode.POLICY_SATISFIED.code();
  }
}
