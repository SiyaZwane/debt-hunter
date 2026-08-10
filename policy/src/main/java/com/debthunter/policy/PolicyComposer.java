package com.debthunter.policy;

import com.debthunter.domain.HistoryDepth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composes a central policy bundle with an optional repo-local {@value #LOCAL_POLICY_FILE_NAME}
 * override, enforcing a tighten-only rule: the local override may only make thresholds stricter,
 * never looser. This lets an organisation set a mandatory floor centrally while letting individual
 * repositories opt into stricter gating for their own code, without being able to carve out
 * exemptions from what's centrally required.
 *
 * <p>The policy YAML schema has no way to distinguish "this field was omitted" from "this field was
 * explicitly set to its emptiest value" — an absent {@code exclusions.categories:} key and an
 * explicit {@code exclusions.categories: []} both parse to an empty list; an absent {@code
 * suppressions.maxExpiryDays:} and an explicit {@code 0} both parse to {@code 0}. This composer
 * therefore treats each field's emptiest value as "the local file has no opinion here, inherit the
 * central bundle's value" rather than as an explicit override. A local override can narrow these
 * down to a genuine non-empty value, but cannot use the emptiest value to wipe out every centrally
 * granted allowance.
 */
public final class PolicyComposer {

  /** The repo-local override file this composer looks for, relative to the repository root. */
  public static final String LOCAL_POLICY_FILE_NAME = ".debt-hunter.yml";

  private final PolicyBundleParser policyBundleParser;

  /**
   * Creates a composer.
   *
   * @param policyBundleParser parses the repo-local override file, if one exists
   */
  public PolicyComposer(PolicyBundleParser policyBundleParser) {
    this.policyBundleParser = Objects.requireNonNull(policyBundleParser, "policyBundleParser");
  }

  /**
   * Composes {@code central} with {@code repoRoot}'s {@value #LOCAL_POLICY_FILE_NAME}, if one
   * exists.
   *
   * @param repoRoot the repository root to look for a local override in
   * @param central the central policy bundle
   * @return {@code central} unchanged, with central-only provenance, if no local override file
   *     exists; otherwise the tighten-only merge of the two
   * @throws PolicyParseException if the local override file exists but its YAML is malformed
   * @throws PolicyLoosenedException if the local override loosens any central threshold
   */
  public ComposedPolicy compose(Path repoRoot, PolicyBundle central) {
    Path localPath = repoRoot.resolve(LOCAL_POLICY_FILE_NAME);
    if (!Files.exists(localPath)) {
      return centralOnly(central);
    }
    String yaml;
    try {
      yaml = Files.readString(localPath);
    } catch (IOException e) {
      throw new PolicyParseException("Could not read " + localPath + ": " + e.getMessage(), e);
    }
    PolicyBundle local = policyBundleParser.parse(yaml);
    return merge(central, local);
  }

  private ComposedPolicy centralOnly(PolicyBundle central) {
    List<PolicyProvenance> provenance = new ArrayList<>();
    provenance.add(
        new PolicyProvenance(
            "analysis.minimumHistoryDepth", "central", describe(central.minimumHistoryDepth())));
    for (PolicyRule rule : central.mainRules()) {
      provenance.add(
          new PolicyProvenance("policy.main.rules[" + rule.id() + "]", "central", describe(rule)));
    }
    for (PolicyRule rule : central.pullRequestRules()) {
      provenance.add(
          new PolicyProvenance(
              "policy.pullRequest.rules[" + rule.id() + "]", "central", describe(rule)));
    }
    provenance.add(
        new PolicyProvenance(
            "exclusions.categories", "central", central.excludedCategories().toString()));
    provenance.add(
        new PolicyProvenance("exclusions.paths", "central", central.excludedPaths().toString()));
    provenance.add(
        new PolicyProvenance(
            "suppressions.maxExpiryDays",
            "central",
            String.valueOf(central.suppressionsMaxExpiryDays())));
    return new ComposedPolicy(central, provenance);
  }

  private ComposedPolicy merge(PolicyBundle central, PolicyBundle local) {
    List<PolicyProvenance> provenance = new ArrayList<>();

    HistoryDepth mergedDepth =
        mergeHistoryDepth(central.minimumHistoryDepth(), local.minimumHistoryDepth(), provenance);
    List<PolicyRule> mergedMain =
        mergeRules("policy.main.rules", central.mainRules(), local.mainRules(), provenance);
    List<PolicyRule> mergedPullRequest =
        mergeRules(
            "policy.pullRequest.rules",
            central.pullRequestRules(),
            local.pullRequestRules(),
            provenance);
    var mergedCategories =
        mergeSubset(
            "exclusions.categories",
            central.excludedCategories(),
            local.excludedCategories(),
            provenance);
    var mergedPaths =
        mergeSubset("exclusions.paths", central.excludedPaths(), local.excludedPaths(), provenance);
    int mergedExpiry =
        mergeMaxExpiry(
            central.suppressionsMaxExpiryDays(), local.suppressionsMaxExpiryDays(), provenance);

    PolicyBundle merged =
        new PolicyBundle(
            central.version(),
            central.metadata(),
            mergedDepth,
            mergedMain,
            mergedPullRequest,
            mergedCategories,
            mergedPaths,
            mergedExpiry);
    return new ComposedPolicy(merged, provenance);
  }

  private HistoryDepth mergeHistoryDepth(
      HistoryDepth central, HistoryDepth local, List<PolicyProvenance> provenance) {
    String field = "analysis.minimumHistoryDepth";
    if (local == null) {
      provenance.add(new PolicyProvenance(field, "central", describe(central)));
      return central;
    }
    if (central != null && local.ordinal() > central.ordinal()) {
      throw new PolicyLoosenedException(
          field + ": local value " + local + " loosens central's " + central);
    }
    String detail =
        central == null ? describe(local) : describe(local) + ", was " + describe(central);
    provenance.add(new PolicyProvenance(field, "local (tightened)", detail));
    return local;
  }

  private List<PolicyRule> mergeRules(
      String fieldPrefix,
      List<PolicyRule> central,
      List<PolicyRule> local,
      List<PolicyProvenance> provenance) {
    Map<String, PolicyRule> centralById = new LinkedHashMap<>();
    for (PolicyRule rule : central) {
      centralById.put(rule.id(), rule);
    }
    Map<String, PolicyRule> localById = new LinkedHashMap<>();
    for (PolicyRule rule : local) {
      localById.put(rule.id(), rule);
    }

    List<PolicyRule> merged = new ArrayList<>();
    for (PolicyRule centralRule : central) {
      String field = fieldPrefix + "[" + centralRule.id() + "]";
      PolicyRule localRule = localById.get(centralRule.id());
      if (localRule == null) {
        merged.add(centralRule);
        provenance.add(new PolicyProvenance(field, "central", describe(centralRule)));
        continue;
      }
      if (localRule.minSeverity().ordinal() < centralRule.minSeverity().ordinal()) {
        // A lower severity ordinal is a narrower net (fewer findings qualify), so moving toward
        // one — e.g. CRITICAL-only instead of LOW-and-worse — loosens the rule, not tightens it.
        throw new PolicyLoosenedException(
            field
                + ".severity: local value "
                + localRule.minSeverity()
                + " loosens central's "
                + centralRule.minSeverity());
      }
      if (localRule.maxCount() > centralRule.maxCount()) {
        throw new PolicyLoosenedException(
            field
                + ".maxCount: local value "
                + localRule.maxCount()
                + " loosens central's "
                + centralRule.maxCount());
      }
      merged.add(localRule);
      provenance.add(
          new PolicyProvenance(
              field, "local (tightened)", describe(localRule) + ", was " + describe(centralRule)));
    }
    for (PolicyRule localRule : local) {
      if (!centralById.containsKey(localRule.id())) {
        merged.add(localRule);
        provenance.add(
            new PolicyProvenance(
                fieldPrefix + "[" + localRule.id() + "]", "local (new)", describe(localRule)));
      }
    }
    return merged;
  }

  private <T> List<T> mergeSubset(
      String field, List<T> central, List<T> local, List<PolicyProvenance> provenance) {
    if (local.isEmpty()) {
      provenance.add(new PolicyProvenance(field, "central", central.toString()));
      return central;
    }
    if (!central.containsAll(local)) {
      throw new PolicyLoosenedException(
          field + ": local value " + local + " is not a subset of central's " + central);
    }
    provenance.add(new PolicyProvenance(field, "local (tightened)", local + ", was " + central));
    return local;
  }

  private int mergeMaxExpiry(int central, int local, List<PolicyProvenance> provenance) {
    String field = "suppressions.maxExpiryDays";
    if (local == 0) {
      provenance.add(new PolicyProvenance(field, "central", String.valueOf(central)));
      return central;
    }
    if (local > central) {
      throw new PolicyLoosenedException(
          field + ": local value " + local + " loosens central's " + central);
    }
    provenance.add(new PolicyProvenance(field, "local (tightened)", local + ", was " + central));
    return local;
  }

  private String describe(HistoryDepth depth) {
    return depth == null ? "none" : depth.name();
  }

  private String describe(PolicyRule rule) {
    return "severity=" + rule.minSeverity() + ", maxCount=" + rule.maxCount();
  }
}
