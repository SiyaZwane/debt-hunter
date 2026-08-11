# Configuration reference

Every file below is optional. A repository with none of them still scans successfully under a
permissive default policy — Debt Hunter is opt-in, not opt-out. This page covers what each file
does, where it lives, and its exact shape; see [`docs/examples/`](examples/) for copy-pasteable
starting points.

| File | Where | Read by | Purpose |
|---|---|---|---|
| (central policy bundle) | wherever you keep it; passed via `--policy <path>` | `PolicyBundleParser` | The organisation-wide floor `scan` gates against |
| `.debt-hunter.yml` | repo root | `PolicyComposer` | A repo-local, **tighten-only** override of the central bundle |
| `.debt-hunter-suppressions.yml` | repo root | `SuppressionRegistry` | Time-boxed, owned exemptions for specific findings |
| `.debt-hunter-arch.yml` | repo root | `ArchitectureRulesEngine` | Declarative import/layering rules |
| `sonar-report.json` | repo root | `StaticAnalysisEngine` | A pre-existing SonarQube issues-search export to adapt |
| baseline artefact | wherever you keep it; passed via `--baseline <path>`, or resolved from `.debt-hunter/baseline-cache.json` | `BaselineResolver` | What "new" is measured against |

## Policy bundle

Passed to `scan`/`policy` via `--policy <path>`. The exact same shape is also used for the
repo-local `.debt-hunter.yml` override — see [`docs/examples/debt-hunter-policy.example.yml`](examples/debt-hunter-policy.example.yml)
for a complete, valid example (verified against the real parser: `debt-hunter policy --policy
docs/examples/debt-hunter-policy.example.yml`).

```yaml
version: "1.0"
metadata:
  name: default
analysis:
  minimumHistoryDepth: FULL        # optional: FULL | PARTIAL | SHALLOW
policy:
  main:
    rules:
      - id: no-new-critical
        severity: CRITICAL          # CRITICAL | HIGH | MEDIUM | LOW | INFO
        maxCount: 0
  pullRequest:
    rules: []
exclusions:
  categories: []                    # optional: Category names
  paths: []                         # optional: exact or prefix path matches
suppressions:
  maxExpiryDays: 90                 # optional
```

- **`analysis.minimumHistoryDepth`** — a repository shallower than this fails the scan with exit
  code `4`; findings computed from commit history get reduced confidence below `FULL` even when
  they don't fail outright.
- **A rule's `severity` is a *minimum*, not an exact match**: `severity: HIGH, maxCount: 0` means
  "no more than 0 new findings at HIGH **or worse** (i.e. HIGH or CRITICAL)", not "exactly HIGH".
- **`policy.main`** applies to a full scan; **`policy.pullRequest`** applies under
  `--mode PULL_REQUEST`, typically stricter since the change under review is smaller.
- Every violated rule is reported, not just the first — `PolicyEvaluator` never short-circuits.

### The `.debt-hunter.yml` local override, and why it can only tighten

`PolicyComposer` reads `.debt-hunter.yml` from the repo root, if present, and merges it onto the
central bundle field by field. A local file may:

- raise a rule's `maxCount` down (stricter) or raise its `severity` floor down toward `CRITICAL`
  (also stricter — a *lower* severity ordinal is a *narrower*, stricter net)
- add an entirely new rule
- narrow `exclusions.categories`/`exclusions.paths` to a **subset** of the central list
- lower `suppressions.maxExpiryDays`
- raise `analysis.minimumHistoryDepth`

Attempting the opposite of any of these — loosening a threshold, excluding something the central
bundle doesn't already exclude, raising the suppression expiry cap — fails the scan immediately
with exit code `2` (`PolicyLoosenedException`), before any engine runs. Run `debt-hunter policy
--repo <path> --policy <central>` to see the composed result and each field's provenance
(`central` vs. `local (tightened)` vs. `local (new)`) before relying on it. See
[AD-08](architecture-decisions/AD-08-tighten-only-policy-composition.md) for the rationale.

## Suppressions — `.debt-hunter-suppressions.yml`

```yaml
suppressions:
  - fingerprint: fp-abc123
    owner: alice
    reason: "Tracked in JIRA-123, fix scheduled for Q3"
    expires: 2026-06-01
```

- **`fingerprint`** must match a finding's content-anchored fingerprint exactly (see
  [AD-05](architecture-decisions/AD-05-content-anchored-fingerprints.md)) — copy it from a prior
  `debt-hunter.json`, not from the finding's `id`.
- **`owner`** and **`reason`** are both required and both free text; there's no enforced format
  beyond "non-blank".
- **`expires`** is checked against the **scanned commit's date**, never the wall clock — the same
  commit always evaluates the same way regardless of which day you happen to run the scan — and, if
  the policy bundle sets a non-zero `suppressions.maxExpiryDays`, must not be more than that many
  days after that commit date, or the scan fails with exit code `2` rather than silently accepting
  an over-long exemption. An absent or explicit `0` means no ceiling is enforced.
- An active suppression excludes that finding from policy gating entirely (it still appears in the
  report, just doesn't count toward any `maxCount`) and is listed under "Active suppressions" in
  `summary.md`.

## Architecture rules — `.debt-hunter-arch.yml`

Read by `ArchitectureRulesEngine`; absent means the engine is a no-op (`OK`, zero findings), not an
error. See [`docs/examples/debt-hunter-arch.example.yml`](examples/debt-hunter-arch.example.yml) for
a complete example built from this project's own module boundaries.

```yaml
rules:
  - id: domain-no-application-dependency
    appliesTo: "**/domain/**/*.java"
    deniedImports:
      - "com.acme.application.**"
    allowedImports: []              # optional; see below
    severity: HIGH                  # optional, defaults to HIGH
```

- **`appliesTo`** is a slash-separated glob matched against each file's path relative to the repo
  root. `**` matches zero or more *whole path segments*, including zero — `"**/domain/**/*.java"`
  matches a file directly inside `domain/`, not only one nested further down (this is deliberately
  more forgiving than Java's built-in `java.nio.file` glob matcher, which requires at least one
  intermediate segment for that pattern). A single `*` matches within one segment only.
- **`deniedImports`**: any import in a matched file equal to, or matching, one of these patterns is
  a violation. A pattern ending in `.**` matches that package and everything under it (prefix
  match); anything else must match an import exactly.
- **`allowedImports`**, if non-empty, makes the rule a whitelist instead: any import *not* matching
  one of these patterns is a violation — including JDK imports, so list `"java.util.**"` etc.
  explicitly if you want to allow them. A rule may combine both: denied imports are checked first,
  then anything left over must be on the allowed list.
- Each violation becomes an `ARCHITECTURE`-category finding, fingerprinted on the rule, the file's
  path, and the specific import — so two different violating imports in the same file get distinct
  fingerprints, not one.

## Static analysis adapter — `sonar-report.json`

Read by `StaticAnalysisEngine`; absent means the engine is a no-op. This is an **adapter, not a
re-analyser** — it maps a report SonarQube (or a compatible tool) already produced into canonical
findings; it never invokes SonarQube itself. Expected shape (SonarQube's issues-search export):

```json
{
  "issues": [
    {
      "key": "AXy1",
      "rule": "java:S1192",
      "severity": "MAJOR",
      "component": "my-project:src/main/java/com/acme/Foo.java",
      "line": 42,
      "message": "Define a constant instead of duplicating this literal."
    }
  ]
}
```

- **`component`**'s `"<projectKey>:"` prefix is stripped to get the finding's `path`.
- **`severity`** maps five-to-five onto Debt Hunter's scale: `BLOCKER`→`CRITICAL`,
  `CRITICAL`→`HIGH`, `MAJOR`→`MEDIUM`, `MINOR`→`LOW`, `INFO`→`INFO`.
- **`line`** is optional; a file-scoped issue with no `line` becomes a finding with `startLine: 0`.
- Generate this file with a `sonar-scanner`/API-export step in CI **before** running `debt-hunter
  scan`, pointed at `sonar-report.json` in the repo root.

## Baseline

No fixed filename. Point `scan` at one explicitly with `--baseline <path>`, or let
`BaselineResolver` fall back to `.debt-hunter/baseline-cache.json` if you're using the
pipeline-cache resolution step. With no baseline at all, the first scan runs in **observe mode**:
a policy violation reports `would_fail` instead of actually failing, since there's nothing yet to
compare "new" against.

## Everything else needs no file at all

Code Maat's hotspot/churn/coupling/knowledge-concentration analysis needs no configuration file —
just enough Git history. Run `debt-hunter doctor --repo <path>` to check whether your checkout's
history is deep enough, and what to do if it isn't.
