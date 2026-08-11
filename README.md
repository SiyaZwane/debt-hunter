# Debt Hunter

A deterministic, containerised command-line technical-debt analyser.

Debt Hunter scans a checked-out Git repository, runs a set of pluggable analysis engines against
it (change hotspots, temporal coupling, knowledge concentration, declarative architecture rules,
adapted SonarQube/linter output), and reports the result as native JSON, SARIF 2.1.0, a Markdown
summary, and a metrics file. A policy bundle gates the result into a process exit code, so the
same invocation works equally as a local developer command or a CI/CD quality gate. Two runs of
the same commit, on any supported platform, produce byte-identical output.

## Why

- **Deterministic.** No wall-clock dependence, fixed time zone/locale, sorted serialisation — the
  same input always produces the same output, regardless of when or where it runs.
- **Containerised and offline-capable.** Ships as a non-root, network-isolated OCI image with a
  jlink-trimmed JRE; `--network none` is a supported, tested mode.
- **Policy-gated, not just informational.** A YAML policy bundle turns findings into a pass/fail
  exit code, with tighten-only composition between a central and a per-repository policy.
- **AI is advisory, never load-bearing.** Explanation and remediation suggestions are a separate,
  optional command (`explain`) that never touches the `scan` gate path — an unreachable AI endpoint
  never changes a scan's exit code (see [AD-09](docs/architecture-decisions/AD-09-ai-off-the-gate-path.md)).

## Project structure

A Maven multi-module reactor. Each module has one clear responsibility, and dependencies only ever
point inward toward `domain`:

| Module | Responsibility |
|---|---|
| `domain` | Pure model (`Finding`, `Severity`, `Category`, ...) — zero I/O, zero framework, zero engine dependencies |
| `engine-spi` | The `AnalysisEngine` plugin interface and its supporting types |
| `engine-codemaat` | Runs Code Maat as an isolated subprocess; hotspot/churn/coupling/knowledge-concentration findings |
| `engine-architecture` | Declarative import/layering rules read from `.debt-hunter-arch.yml` |
| `engine-static-analysis` | Adapts a pre-existing SonarQube issues export into canonical findings |
| `repository` | Git history access (JGit for metadata, native `git` where it matters) |
| `policy` | Policy parsing, composition, evaluation, baseline comparison, suppressions |
| `output` | Reporters: native JSON, SARIF 2.1.0, Markdown summary, metrics |
| `application` | Use cases — the only orchestration layer (`ScanUseCase`, `PublishUseCase`, ...) |
| `integration` | Optional async result publication, work-item sync (Jira/Azure Boards) |
| `ai` | Explanation, remediation, and automated-fix proposals — optional, never on the gate path |
| `cli` | picocli commands — no business logic of its own |
| `testkit` | Shared test fixtures, including a real-Git `FixtureRepoBuilder` and the conformance suite |

## Quick start

Build and run the container image (no registry publication is configured yet, so build locally):

```bash
docker build -t debt-hunter .
docker run --rm --network none \
  -v "$(pwd):/workspace/repo:ro" \
  -v "$(pwd)/debt-hunter-output:/output" \
  debt-hunter scan --repo /workspace/repo --output-dir /output
```

This writes `debt-hunter.json`, `debt-hunter.sarif`, `summary.md`, and `metrics.json` into
`./debt-hunter-output`, and exits `0` (policy satisfied) or `1` (policy violated). `--network none`
works because Debt Hunter never needs the network for a scan — see
[AD-12](docs/architecture-decisions/AD-12-hardened-offline-container.md).

To run against the JVM directly instead (e.g. for local development), see
[CONTRIBUTING.md](CONTRIBUTING.md).

## CLI reference

```
debt-hunter [-hV] [COMMAND]
```

| Command | Purpose |
|---|---|
| `scan` | Analyse a repository and write the report files. This is the one command with a process exit code that means something (see below). |
| `doctor` | Diagnose a repository's readiness for analysis (history depth, shallow/grafted clones) and print recommendations. |
| `publish` | Publish a previously written `debt-hunter.json` to a results endpoint, independent of the scan that produced it. |
| `explain` | Ask an AI service to explain and propose a remediation for one finding from a previously written report. Never affects `scan`'s exit code. |
| `policy` | Print the effective (merged) policy for a repository — what `scan` would actually enforce, and where each setting came from. |

### `scan`

| Option | Description |
|---|---|
| `--repo <path>` | Repository to scan. Default: `.` |
| `--output-dir`, `--out <path>` | **Required.** Directory to write report files into. |
| `--mode <FULL\|PULL_REQUEST>` | Analysis mode. Default: `FULL` |
| `--base-ref <ref>` | Base ref to compare against, for `PULL_REQUEST` mode. |
| `--policy <path>` | Path to a policy bundle file. |
| `--baseline <path>` | Path to a baseline artefact to compare against. |
| `--fail-on <SEVERITY>` | Severity threshold that fails the build, in addition to any configured policy. |
| `--history-window-since <ISO-8601 instant>` | Only consider commits at or after this point. |
| `--project <name>=<pathPrefixOrGlob>` | Repeatable. Slices a monorepo scan into named projects. |
| `--offline` | Skip any network-dependent steps (e.g. publication). |
| `--publish-endpoint <uri>`, `--publish-api-key`, `--publish-timeout` | Optional asynchronous result publication after the scan completes. |

### `doctor`

| Option | Description |
|---|---|
| `--repo <path>` | Repository to diagnose. Default: `.` |

### `publish`

| Option | Description |
|---|---|
| `--report <path>` | **Required.** Path to a previously written `debt-hunter.json`. |
| `--publish-endpoint <uri>` | **Required.** Endpoint to publish the result to. |
| `--publish-api-key <key>` | Bearer API key for `--publish-endpoint`. |
| `--publish-timeout <duration>` | Request timeout, e.g. `PT30S`. Default: `PT30S` |

### `explain`

| Option | Description |
|---|---|
| `--report <path>` | **Required.** Path to a previously written `debt-hunter.json`. |
| `--finding-id <id>` | **Required.** Id of the finding to explain. |
| `--explain-endpoint <uri>` | **Required.** AI service endpoint. |
| `--explain-api-key <key>` | Bearer API key for `--explain-endpoint`. |
| `--explain-timeout <duration>` | Request timeout, e.g. `PT30S`. Default: `PT30S` |

### `policy`

| Option | Description |
|---|---|
| `--repo <path>` | Repository to check. Default: `.` |
| `--policy <path>` | Path to the central policy bundle file. |

## Configuration

Every config file is optional — a repository with none of them scans successfully under a
permissive default policy. What's available:

| File | Purpose |
|---|---|
| central policy bundle (via `--policy`) | The organisation-wide floor `scan` gates against |
| `.debt-hunter.yml` | A repo-local, tighten-only override of the central policy |
| `.debt-hunter-suppressions.yml` | Time-boxed, owned exemptions for specific findings |
| `.debt-hunter-arch.yml` | Declarative import/layering rules, checked by `ArchitectureRulesEngine` |
| `sonar-report.json` | A pre-existing SonarQube issues-search export, adapted by `StaticAnalysisEngine` |

See [`docs/configuration.md`](docs/configuration.md) for the full reference (exact shape, field
semantics, tighten-only composition rules) and [`docs/examples/`](docs/examples/) for two complete,
verified starting points: [a policy bundle](docs/examples/debt-hunter-policy.example.yml) and
[an architecture rules file](docs/examples/debt-hunter-arch.example.yml).

## Exit codes

Every command returns one of the same process-level exit codes (`ExitCode`); `scan` is the only
one where every value below is actually reachable — the other commands only ever return `0` or `2`.

| Code | Meaning |
|---|---|
| `0` | Policy satisfied (or observe mode overrode a failure — see the policy bundle's provenance). |
| `1` | At least one policy rule was violated. |
| `2` | The target path is not usable, or a policy/suppressions file is invalid. |
| `3` | Reserved: more engines failed than the policy tolerates. Not yet triggered by any condition. |
| `4` | The repository's history is shallower than the policy's minimum requirement. |
| `5` | An explicit or cached baseline was found but cannot be used. |
| `10` | Something failed that the caller cannot fix by changing their input (internal error). |

## Design documentation

- [`docs/architecture-decisions/`](docs/architecture-decisions/) — the key architectural decisions
  (AD-01 through AD-12) behind this design, with context and consequences.
- [`docs/functional-specification.md`](docs/functional-specification.md) — the functional
  specification this project was built against, FR by FR, with every acceptance and boundary
  criterion.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, build commands, and testing
conventions.
