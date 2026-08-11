# Debt Hunter — Claude Code Development Prompt

## Role and context

You are a principal software engineer building **Debt Hunter**, a deterministic, containerised command-line technical-debt analyser. You will build this project from scratch, one functional requirement at a time, committing after each FR is complete with all its acceptance criteria met and tests passing.

**You are building a real, production-grade Java 21 application** — not scaffolding, not stubs, not a prototype. Every class compiles, every test passes, every acceptance criterion is verified by an automated test before you commit.

---

## Technology stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Build tool | Gradle (Kotlin DSL) with a multi-module project |
| CLI framework | picocli (no Spring on the CLI path) |
| Spring Boot | Control-plane module only (not built until FR-17+) |
| Git access | JGit for metadata; ProcessBuilder for native git where performance requires it |
| Serialisation | Jackson with deterministic ordering (`ORDER_MAP_ENTRIES_BY_KEYS`, `SORT_PROPERTIES_ALPHABETICALLY`) |
| Testing | JUnit 5, AssertJ, Mockito; Testcontainers for integration tests |
| Code coverage | JaCoCo — enforce 80% line coverage per module |
| Static analysis | Spotless (Google Java Format), SpotBugs |
| Container | Dockerfile with jlink-trimmed JRE, non-root user |
| Policy format | YAML parsed with SnakeYAML into validated typed model |

---

## Project structure

Create this multi-module Gradle layout:

```
debt-hunter/
├── settings.gradle.kts
├── build.gradle.kts                  # root: shared config, plugins
├── cli/                              # picocli commands — no business logic
│   ├── build.gradle.kts
│   └── src/
├── application/                      # use cases — the only orchestration layer
│   ├── build.gradle.kts
│   └── src/
├── domain/                           # pure model — no I/O, no framework
│   ├── build.gradle.kts
│   └── src/
├── engine-spi/                       # AnalysisEngine interface + types
│   ├── build.gradle.kts
│   └── src/
├── engine-codemaat/                  # Code Maat subprocess adapter
│   ├── build.gradle.kts
│   └── src/
├── engine-architecture/              # architecture conformance rules
│   ├── build.gradle.kts
│   └── src/
├── engine-static-analysis/           # consumes existing linter/SonarQube output
│   ├── build.gradle.kts
│   └── src/
├── repository/                       # Git history port and adapter
│   ├── build.gradle.kts
│   └── src/
├── policy/                           # policy parsing, evaluation, baseline comparison
│   ├── build.gradle.kts
│   └── src/
├── output/                           # reporters (JSON, SARIF, Markdown, metrics)
│   ├── build.gradle.kts
│   └── src/
├── integration/                      # work-item publishers (Jira, Azure Boards)
│   ├── build.gradle.kts
│   └── src/
├── ai/                               # explanation, remediation (optional, non-gating)
│   ├── build.gradle.kts
│   └── src/
├── testkit/                          # shared test fixtures and fixture-repo builder
│   ├── build.gradle.kts
│   └── src/
└── Dockerfile
```

---

## Global rules — apply to every step

1. **Compile and test before every commit.** Run `./gradlew build` and confirm zero failures before committing. If a test fails, fix it before proceeding.
2. **One commit per FR.** The commit message format is: `feat(FR-XX): <short title>` followed by a body listing every AC satisfied and every test class created.
3. **Tests live next to the code they verify.** Unit tests in `src/test/java`, integration tests in `src/integrationTest/java` (configure a separate source set in Gradle).
4. **Every AC becomes at least one test method.** Name the method `ac<NN>_<description>`. For example: `ac01_validRepoProducesAllOutputs()`.
5. **Every BC becomes a test method.** Name the method `bc<NN>_<description>`.
6. **No test uses @Disabled.** Every test passes or the step is not complete.
7. **Domain module has ZERO dependencies on I/O, framework, or engine libraries.** Enforce this with Gradle dependency constraints. If you find yourself importing `java.io`, `java.net`, Jackson, or Spring in the domain module, stop and refactor.
8. **Jackson ObjectMapper configuration is centralised.** Create a single `DeterministicObjectMapper` factory that sets `ORDER_MAP_ENTRIES_BY_KEYS`, `SORT_PROPERTIES_ALPHABETICALLY`, `INDENT_OUTPUT`, timezone UTC, and is reused everywhere.
9. **No `System.exit()` in library code.** Only the CLI entry point translates the result into an exit code.
10. **Every public method has Javadoc.** Not decorative — a one-line description and @param/@return.

---

## Step 0 — Repository initialisation and walking skeleton

### What to do

1. Create a new directory `debt-hunter`.
2. Run `git init`.
3. Create the Gradle multi-module structure with `settings.gradle.kts` including all modules.
4. Configure the root `build.gradle.kts`:
   - Java 21 toolchain
   - JUnit 5 platform
   - JaCoCo with 80% line-coverage enforcement
   - Spotless with Google Java Format
   - Common dependency versions (picocli, Jackson, JGit, SnakeYAML, AssertJ, Mockito)
5. Create each module's `build.gradle.kts` with correct inter-module dependencies:
   - `domain` → no project dependencies
   - `engine-spi` → depends on `domain`
   - `repository` → depends on `domain`
   - `policy` → depends on `domain`
   - `application` → depends on `domain`, `engine-spi`, `repository`, `policy`, `output`
   - `output` → depends on `domain`
   - `cli` → depends on `application`; picocli annotation processor
   - `engine-codemaat` → depends on `engine-spi`
   - `engine-architecture` → depends on `engine-spi`
   - `engine-static-analysis` → depends on `engine-spi`
   - `integration` → depends on `domain`
   - `ai` → depends on `domain`
   - `testkit` → depends on `domain`; JGit for creating fixture repos programmatically
6. Create a minimal `DebtHunterCli` entry point in `cli` with picocli `@Command` that prints the version and exits.
7. Create a single trivial test: `DebtHunterCliTest` that invokes the CLI with `--version` and asserts exit code 0.
8. Run `./gradlew build` — must pass.
9. Create a `.gitignore` (Gradle, IDE, build outputs).
10. Create a `README.md` with the project name and a one-line description.

### Commit

```
feat(M0): initialise repository and walking skeleton

- Multi-module Gradle project with Java 21 toolchain
- picocli CLI entry point with --version
- JaCoCo, Spotless, SpotBugs configured
- All modules compile; one smoke test passes
```

---

## Step 1 — FR-01: Analyse a checked-out Git repository and produce technical-debt findings

### What to build

**Domain module (`domain`):**

- `Finding` — immutable record: id, ruleId, category, severity, confidence, path, startLine, message, evidence (Map<String, Object>), score, isNew, fingerprint. Use Java 17+ records or immutable classes with builder.
- `Evidence` — typed wrapper around the evidence map with accessors for known keys (changeFrequency, authors, complexityDelta, calculation, engine).
- `DebtMetric` — name, value, scope.
- `AnalysisRun` — immutable: id, toolVersion, imageDigest, timestamp, repository, project, commit, baseCommit, branch, pullRequest, historyDepth (enum: FULL, PARTIAL, SHALLOW), engines (list of EngineStatus), degraded (boolean).
- `EngineStatus` — id, version, status (OK, DEGRADED, FAILED), durationMs, reason (nullable).
- `PolicyResult` — bundleVersion, status (PASSED, FAILED, WOULD_FAIL), reasons (list of PolicyViolation).
- `PolicyViolation` — rule, threshold, actual, findingIds.
- `ScanResult` — aggregates AnalysisRun, List<Finding>, Map<String,DebtMetric>, PolicyResult.
- `Severity` — enum: CRITICAL, HIGH, MEDIUM, LOW, INFO.
- `Category` — enum or string: HOTSPOT, TEMPORAL_COUPLING, CHURN, KNOWLEDGE_CONCENTRATION, ARCHITECTURE, STATIC_ANALYSIS, DEPENDENCY, TEST_HEALTH, CUSTOM.
- `HistoryDepth` — enum with display methods.

**Engine SPI (`engine-spi`):**

- `AnalysisEngine` interface: `EngineDescriptor descriptor()`, `boolean supports(RepositoryContext context)`, `EngineResult analyse(AnalysisRequest request, ProgressSink sink)`.
- `EngineDescriptor` — id, version, categories, costClass.
- `RepositoryContext` — repoPath, languages, vcsType, historyDepth.
- `AnalysisRequest` — repoPath, baseRef, mode, historyWindow, policy settings, timeout, memoryLimit.
- `EngineResult` — status (OK/DEGRADED/FAILED), findings (List<Finding>), metrics (List<DebtMetric>), reason (nullable), durationMs.
- `ProgressSink` — interface with `void report(String message, double progress)`.

**Repository module (`repository`):**

- `RepositoryHistoryProvider` — port interface: `RepositoryInfo inspect(Path repoPath)`, `List<CommitInfo> history(Path repoPath, HistoryWindow window)`.
- `RepositoryInfo` — isGitRepo, isShallow, isGrafted, commitCount, headCommit, headBranch.
- `GitHistoryProvider` — JGit-based implementation of the port.

**Output module (`output`):**

- `JsonReporter` — writes `debt-hunter.json` from a `ScanResult` using the deterministic ObjectMapper. Findings sorted by ruleId, then path, then startLine.
- `MarkdownReporter` — writes `summary.md`.
- `MetricsReporter` — writes `metrics.json`.

**Application module (`application`):**

- `ScanUseCase` — the orchestrator. Accepts `ScanRequest` (repoPath, outputDir, mode, baseRef, policy, engines, historyWindow, offline, failOn). Calls repository provider, invokes engines (with timeout via ExecutorService), collects results, normalises, scores, evaluates policy (stubbed for now — always PASSED), writes outputs via reporters, returns `ScanOutcome` (exitCode + scanResult).

**CLI module (`cli`):**

- `ScanCommand` — picocli `@Command` with all flags from HLD §4.2. Delegates to `ScanUseCase`. Translates `ScanOutcome.exitCode` to the process exit code.

**Testkit (`testkit`):**

- `FixtureRepoBuilder` — utility that programmatically creates a Git repository with JGit, adds commits, creates hotspot patterns, renames, etc. Returns a `Path` to the repo. This is the foundation of all subsequent test fixtures.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `FindingTest` | domain | Unit | Immutability, builder, equality |
| `ScanResultTest` | domain | Unit | Aggregation, degraded-flag logic |
| `AnalysisEngineContractTest` | engine-spi | Unit | Interface contract (null returns, status semantics) |
| `GitHistoryProviderTest` | repository | Integration | Inspect and history on a fixture repo |
| `JsonReporterTest` | output | Unit | Schema conformance, deterministic ordering, volatile-field exclusion |
| `MarkdownReporterTest` | output | Unit | Content structure |
| `ScanUseCaseTest` | application | Unit | Engine orchestration, timeout handling, degraded-flag propagation |
| `ScanCommandIntegrationTest` | cli | Integration | End-to-end: fixture repo → scan → verify all 4 output files + exit code |
| `AC01_ValidRepoProducesAllOutputsTest` | cli | Integration | AC-01 |
| `AC02_NonGitPathExitsCode2Test` | cli | Integration | AC-02 |
| `AC03_EngineTimeoutDegradedTest` | application | Unit | AC-03 (mock engine that times out) |
| `AC04_DeterministicOutputTest` | cli | Integration | AC-04 (two runs, byte-compare) |
| `BC01_EmptyRepoTest` | cli | Integration | BC-01 |
| `BC02_NoApplicableScopeTest` | cli | Integration | BC-02 |
| `BC03_ReadOnlyOutputDirTest` | cli | Integration | BC-03 |
| `BC04_AllEnginesFailTest` | application | Unit | BC-04 |
| `BC05_SymbolicLinksTest` | cli | Integration | BC-05 |

### Acceptance criteria to satisfy

- **AC-01**: Valid repo → 4 files produced, exit 0 or 1, every finding has all required fields.
- **AC-02**: Non-git path → exit 2, diagnostic on stderr, no output files.
- **AC-03**: One engine times out → run completes, degraded=true, other findings present.
- **AC-04**: Same inputs on two runs → byte-identical JSON (volatile excluded).

### Commit

```
feat(FR-01): analyse a checked-out Git repository and produce findings

Satisfies: AC-01, AC-02, AC-03, AC-04
Boundary: BC-01, BC-02, BC-03, BC-04, BC-05

- Domain model: Finding, Evidence, AnalysisRun, ScanResult, PolicyResult
- Engine SPI with AnalysisEngine interface
- GitHistoryProvider (JGit)
- JsonReporter, MarkdownReporter, MetricsReporter
- ScanUseCase orchestrator with engine timeout handling
- ScanCommand CLI with all flags
- FixtureRepoBuilder test utility
- 17 test classes, all passing
```

---

## Step 2 — FR-02: Execute as a self-contained container

### What to build

- `Dockerfile` in the project root:
  - Multi-stage build: Gradle build stage → jlink-trimmed JRE stage.
  - Non-root user (`debt-hunter`, UID 10001).
  - No privileged capabilities.
  - `ENTRYPOINT ["debt-hunter"]`.
  - Labels for OCI image spec (title, version, description, source, vendor).
- Gradle task `jlinkImage` that produces the trimmed JRE.
- Gradle task `dockerBuild` that builds and tags the image.
- Shell script `scripts/smoke-test-container.sh`:
  - Builds the image.
  - Creates a fixture repo with the FixtureRepoBuilder (via a small Java main class in testkit).
  - Runs the container with `--network none`, mounted workspace, mounted output dir.
  - Asserts exit code 0 or 1.
  - Asserts all 4 output files exist.
  - Asserts no root process (`docker inspect`).
  - Asserts no privileged capability.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `AC05_OfflineContainerScanTest` | cli | Integration | Container runs with --network none, produces outputs |
| `AC06_DigestDeterminismTest` | cli | Integration | Tag vs digest invocation produces same output |
| `AC07_NonRootImageTest` | cli | Integration | Image inspection: non-root, no caps, SBOM present |
| `AC08_MultiArchDeterminismTest` | cli | Integration | amd64 vs arm64 output equality (skip in CI if only one arch available) |
| `BC06_EmptyWorkspaceTest` | cli | Integration | Empty mount → exit 2 |
| `BC07_RestrictivePermissionsTest` | cli | Integration | Unreadable repo → exit 10 |

### Commit

```
feat(FR-02): self-contained OCI container with non-root, offline execution

Satisfies: AC-05, AC-06, AC-07, AC-08
Boundary: BC-06, BC-07, BC-08

- Multi-stage Dockerfile with jlink-trimmed JRE
- Non-root user, no privileged capabilities
- Smoke-test script verifying offline execution
- 6 test classes
```

---

## Step 3 — FR-03: Encapsulate Code Maat behind an engine adapter

### What to build

**Engine-codemaat module:**

- `CodeMaatEngine` implementing `AnalysisEngine`:
  - `descriptor()` returns id="code-maat", categories=[HOTSPOT, TEMPORAL_COUPLING, CHURN, KNOWLEDGE_CONCENTRATION].
  - `supports()` checks for Git repo with sufficient history.
  - `analyse()`:
    1. Invokes Code Maat CLI as a subprocess via ProcessBuilder.
    2. Redirects stdout to a temp file.
    3. Parses CSV output using a dedicated `CodeMaatOutputParser`.
    4. Maps CSV rows to canonical `Finding` objects with proper category, severity, confidence, evidence.
    5. Wraps in `EngineResult` with timing.
    6. On non-zero exit or unparseable output → returns FAILED with reason.
    7. On timeout → kills process, returns FAILED.
- `CodeMaatOutputParser` — parses Code Maat CSV format into domain types. Zero references to Code Maat types on classpath.
- `CodeMaatFindingMapper` — maps Code Maat concepts to canonical categories, severities, and evidence structures.
- Golden-file test resources: `src/test/resources/golden/` with known Code Maat CSV outputs and expected canonical Finding JSON.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `CodeMaatOutputParserTest` | engine-codemaat | Unit | Parsing of every supported analysis type (revisions, coupling, age, authors) |
| `CodeMaatFindingMapperTest` | engine-codemaat | Unit | Correct category, severity, evidence mapping |
| `CodeMaatGoldenFileTest` | engine-codemaat | Unit | Every golden-file CSV produces expected canonical JSON |
| `CodeMaatEngineTest` | engine-codemaat | Unit | Subprocess invocation, timeout, failure handling (mocked process) |
| `AC09_HotspotFindingsTest` | engine-codemaat | Integration | Known hotspots produce correct findings |
| `AC10_CoreVariantNoCodeMaatTest` | engine-codemaat | Unit | Classpath inspection: no Code Maat classes in core |
| `AC11_TimeoutHandlingTest` | engine-codemaat | Unit | Hanging process terminated, FAILED status, run completes |
| `AC12_GoldenFileRegressionTest` | engine-codemaat | Unit | Full golden-file suite |
| `BC09_MissingBinaryTest` | engine-codemaat | Unit | Missing binary → FAILED with "engine binary not found" |
| `BC10_UnexpectedColumnsTest` | engine-codemaat | Unit | Changed CSV format → DEGRADED with parse error |
| `BC11_SingleCommitRepoTest` | engine-codemaat | Integration | One-commit repo → OK with zero history findings |

### Commit

```
feat(FR-03): Code Maat engine adapter with subprocess isolation

Satisfies: AC-09, AC-10, AC-11, AC-12
Boundary: BC-09, BC-10, BC-11

- CodeMaatEngine: subprocess adapter, never linked
- CodeMaatOutputParser: CSV → canonical domain model
- Golden-file regression suite
- Timeout and failure handling
- 11 test classes
```

---

## Step 4 — FR-04: Stable versioned native JSON findings report

### What to build

- Harden `JsonReporter`:
  - Schema version field: `"schemaVersion": "1.0"`.
  - Enforce all required fields present on every Finding (throw if missing).
  - `policy.reasons` always present (empty array if policy passed).
  - `run.engines` lists every invoked engine.
  - `run.degraded` computed correctly.
  - `run.historyDepth` always present.
- `JsonSchemaValidator` in testkit — validates debt-hunter.json against a JSON Schema file at `testkit/src/main/resources/schemas/debt-hunter-v1.schema.json`.
- Create the JSON Schema file defining the full v1 schema.
- `DeterministicObjectMapper` factory — centralised configuration enforcing key ordering, property sorting, UTC timezone, indentation.
- Determinism integration test: two runs producing byte-identical output.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `JsonSchemaValidationTest` | output | Unit | Every produced JSON passes schema validation |
| `AC13_OutputPresentAndValidTest` | output | Unit | AC-13 |
| `AC14_CrossPlatformDeterminismTest` | cli | Integration | AC-14: two runs byte-identical |
| `AC15_PolicyViolationReasonsTest` | output | Unit | AC-15: failed status with populated reasons |
| `AC16_DegradedEngineStatusTest` | output | Unit | AC-16 |
| `AC17_ZeroFindingsValidSchemaTest` | output | Unit | AC-17 |
| `DeterministicObjectMapperTest` | output | Unit | Key ordering, sorting, UTC |

### Commit

```
feat(FR-04): stable versioned native JSON findings report

Satisfies: AC-13, AC-14, AC-15, AC-16, AC-17

- JSON Schema v1 definition
- DeterministicObjectMapper factory
- JsonSchemaValidator test utility
- Schema validation on every output
- 7 test classes
```

---

## Step 5 — FR-05: Emit SARIF 2.1.0

### What to build

- `SarifReporter` in the output module:
  - Produces `debt-hunter.sarif` conforming to SARIF 2.1.0.
  - One `run` per project slice (or one for the whole repo).
  - Stable `automationDetails.id`.
  - `tool.driver.rules` array with rule descriptors.
  - `partialFingerprints` populated from canonical fingerprints.
  - Category naming: `debt-hunter` or `debt-hunter/<project>`.
  - Excludes hotspot rankings, coupling graphs, knowledge concentration.
- SARIF 2.1.0 JSON Schema in testkit resources for validation.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `SarifReporterTest` | output | Unit | Valid SARIF structure |
| `SarifSchemaValidationTest` | output | Unit | Validates against SARIF 2.1.0 schema |
| `AC18_SarifPresentAndValidTest` | output | Unit | AC-18 |
| `AC19_MultiProjectSarifTest` | output | Unit | AC-19 |
| `AC22_ExcludedCategoriesTest` | output | Unit | AC-22 |

Note: AC-20 and AC-21 (GitHub/Azure DevOps upload) are platform integration tests — create them as `@Tag("platform")` tests that are skipped in normal CI but runnable manually.

### Commit

```
feat(FR-05): SARIF 2.1.0 output with per-project runs and fingerprints

Satisfies: AC-18, AC-19, AC-20 (platform), AC-21 (platform), AC-22

- SarifReporter with SARIF 2.1.0 conformance
- Schema validation in tests
- Multi-project SARIF run support
- Category naming convention
- 5 test classes (+ 2 platform-tagged)
```

---

## Step 6 — FR-06: Generate stable finding fingerprints

### What to build

- `Fingerprinter` in the domain module (pure, no I/O):
  - `String fingerprint(String ruleId, String normalisedPath, String symbolAnchor, String normalisedEvidenceKey)`.
  - SHA-256 hash.
  - Null-safe: missing symbolAnchor uses empty string.
- `RenameTracker` in the repository module:
  - Uses `git log --follow` to resolve rename chains.
  - Returns the canonical path identity for a file.
- `SymbolAnchorExtractor` in the engine-spi:
  - Interface for engines to provide the enclosing type/method for a finding location.
  - Default implementation returns empty (used when no symbol is derivable).
- Fixture repository in testkit with: renames, reformats, line additions, method moves, file splits.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `FingerprinterTest` | domain | Unit | Deterministic hashing, component exclusion |
| `RenameTrackerTest` | repository | Integration | Rename detection on fixture repo |
| `AC23_ReformatStabilityTest` | cli | Integration | AC-23 |
| `AC24_RenameStabilityTest` | cli | Integration | AC-24 |
| `AC25_MethodMoveChangesTest` | cli | Integration | AC-25 |
| `AC26_FixtureSuiteTest` | cli | Integration | AC-26: 10 scenarios |
| `AC27_DistinctRulesDistinctFingerprintsTest` | domain | Unit | AC-27 |

### Commit

```
feat(FR-06): content-anchored fingerprints stable across renames and reformats

Satisfies: AC-23, AC-24, AC-25, AC-26, AC-27

- Fingerprinter (pure domain, SHA-256)
- RenameTracker (git log --follow)
- Fixture repository with 10 fingerprint scenarios
- 7 test classes
```

---

## Step 7 — FR-07: Compare results against a baseline

### What to build

- `BaselineComparator` in the policy module:
  - Loads a baseline (serialised ScanResult).
  - Compares current findings against baseline by fingerprint.
  - Classifies each finding: NEW, EXISTING, REGRESSED, RESOLVED.
  - Sets `isNew` on each finding.
- `BaselineResolver` in the policy module:
  - Resolution chain: explicit → pipeline cache → control plane → none.
  - Validates tool version compatibility (major version match).
  - Validates signature where present.
  - Records provenance in run metadata.
- `BaselineWriter` in the output module:
  - Serialises a baseline artefact from a ScanResult.
  - Signs it with a configurable key (or skips if no key configured).
- Integration into `ScanUseCase`: load baseline before scoring, run comparison, set isNew flags.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `BaselineComparatorTest` | policy | Unit | NEW/EXISTING/REGRESSED/RESOLVED classification |
| `BaselineResolverTest` | policy | Unit | Resolution chain, version validation, signature check |
| `BaselineWriterTest` | output | Unit | Serialisation round-trip |
| `AC28_NewFindingsGatingTest` | cli | Integration | AC-28 |
| `AC29_IncompatibleBaselineTest` | cli | Integration | AC-29 |
| `AC30_NoBaselineObserveModeTest` | cli | Integration | AC-30 |
| `AC31_RegressionDetectionTest` | policy | Unit | AC-31 |
| `AC32_ResolutionDetectionTest` | policy | Unit | AC-32 |

### Commit

```
feat(FR-07): baseline comparison with delta gating

Satisfies: AC-28, AC-29, AC-30, AC-31, AC-32

- BaselineComparator: fingerprint-based comparison
- BaselineResolver: resolution chain with version validation
- BaselineWriter: serialisation with signing
- ScanUseCase integration
- 8 test classes
```

---

## Step 8 — FR-08: Deterministic policy evaluation and exit codes

### What to build

- `PolicyEvaluator` in the policy module:
  - Loads and validates the policy bundle (YAML → typed model).
  - Evaluates thresholds against new findings only.
  - Returns `PolicyResult` with ALL violated rules (no short-circuit).
  - Pure function: same findings + same policy = same result, no network, no clock (suppression expiry uses commit date).
- `PolicyBundle` — typed model: version, metadata, analysis config, policy rules (pullRequest, main), exclusions, suppressions config.
- `PolicyValidator` — validates YAML before analysis begins. Invalid → exit code 2.
- Exit-code mapping in `ScanCommand`:
  - 0: policy satisfied.
  - 1: policy violated.
  - 2: configuration/policy error.
  - 3: engine failure beyond tolerance.
  - 4: insufficient history.
  - 5: baseline unavailable/incompatible.
  - 10: internal error.
- Pre-analysis validation order: config errors (2) → history check (4) → baseline check (5) → analysis → engine tolerance (3) → policy (0/1).
- Observe mode: when no baseline and stage 1, exit 0 but policy.status = "would_fail".

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `PolicyEvaluatorTest` | policy | Unit | Threshold evaluation, all-rules-reported, determinism |
| `PolicyBundleParserTest` | policy | Unit | YAML parsing, validation |
| `PolicyValidatorTest` | policy | Unit | Invalid YAML detection |
| `ExitCodeMappingTest` | cli | Unit | Every exit code scenario |
| `AC33_PolicyViolationExitCode1Test` | cli | Integration | AC-33 |
| `AC34_InvalidPolicyExitCode2Test` | cli | Integration | AC-34 |
| `AC35_ShallowHistoryExitCode4Test` | cli | Integration | AC-35 |
| `AC36_MultipleViolationsTest` | policy | Unit | AC-36 |
| `AC37_ObserveModeTest` | cli | Integration | AC-37 |
| `AC38_DeterministicEvaluationTest` | policy | Unit | AC-38 |

### Commit

```
feat(FR-08): deterministic policy evaluation with documented exit codes

Satisfies: AC-33, AC-34, AC-35, AC-36, AC-37, AC-38

- PolicyEvaluator: pure, deterministic, no short-circuit
- PolicyBundle typed model with YAML parser
- Exit-code contract (0-10) with pre-analysis validation order
- Observe mode with would_fail status
- 10 test classes
```

---

## Step 9 — FR-09: Detect shallow or incomplete history

### What to build

- Enhance `GitHistoryProvider.inspect()`:
  - Detect shallow via `git rev-parse --is-shallow-repository`.
  - Detect grafts.
  - Return `RepositoryInfo` with `historyDepth`.
- `HistoryDepthEnforcer` in the application module:
  - Checks `historyDepth` against policy `minimumHistoryDepth`.
  - When shallow: reduces confidence on history-dependent findings.
  - When shallow + policy requires full: returns exit code 4.
- `DoctorCommand` in CLI:
  - Reports history depth, graft status, recommendations.
- Enhance `MarkdownReporter` to include history warnings.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `ShallowDetectionTest` | repository | Integration | Fixture repo with --depth 1 detected as shallow |
| `HistoryDepthEnforcerTest` | application | Unit | Confidence reduction, exit-code logic |
| `DoctorCommandTest` | cli | Integration | Doctor output on shallow repo |
| `AC39_ShallowHistoryDetectionTest` | cli | Integration | AC-39 |
| `AC40_ShallowWithStrictPolicyTest` | cli | Integration | AC-40 |
| `AC41_ShallowWithPermissivePolicyTest` | cli | Integration | AC-41 |
| `AC42_DoctorShallowRecommendationTest` | cli | Integration | AC-42 |

### Commit

```
feat(FR-09): detect and report shallow or incomplete repository history

Satisfies: AC-39, AC-40, AC-41, AC-42

- Shallow/graft detection in GitHistoryProvider
- HistoryDepthEnforcer with confidence reduction
- DoctorCommand with recommendations
- 7 test classes
```

---

## Step 10 — FR-10: Optional asynchronous publication

### What to build

- `ResultUploader` in the integration module:
  - Interface: `PublishResult publish(ScanResult result, PublishConfig config)`.
  - HTTP-based implementation: fire-and-forget POST, no retry loop.
  - On failure: returns failure result with reason.
- `PublishUseCase` in application:
  - Invoked by ScanCommand after policy evaluation.
  - If not configured: no-op.
  - If configured and fails: logs warning, does not alter exit code.
  - If --offline: skips publication entirely.
- `PublishCommand` in CLI: separate command for manual/deferred publication.
- Enhance `MarkdownReporter` to include publication-failure warnings.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `ResultUploaderTest` | integration | Unit | Success and failure paths |
| `PublishUseCaseTest` | application | Unit | No-op when unconfigured, warning on failure |
| `AC43_NoPubConfigNoNetworkTest` | cli | Integration | AC-43 |
| `AC44_PubFailureWarningOnlyTest` | cli | Integration | AC-44 |
| `AC45_OfflineEnforcementTest` | cli | Integration | AC-45 |

### Commit

```
feat(FR-10): optional asynchronous publication, never affects verdict

Satisfies: AC-43, AC-44, AC-45

- ResultUploader with fire-and-forget semantics
- PublishUseCase: warning-only on failure
- --offline enforcement
- 5 test classes
```

---

## Step 11 — FR-11: No LLM on the gate path

### What to build

This is primarily a verification step — asserting an architectural constraint.

- `ArchitecturalConstraintsTest` in cli module:
  - Uses reflection/classpath scanning to verify no AI/LLM SDK class is reachable from the scan execution path.
  - Verifies the `ai` module is not a dependency of `application`, `domain`, `engine-spi`, `policy`, or `output`.
- Ensure the Gradle dependency graph enforces this: `ai` module depends on `domain` but nothing in the scan path depends on `ai`.
- `ExplainCommand` in CLI (stub for now): separate command that WILL use AI but is not part of scan.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `AC46_OfflineScanNoAIDependencyTest` | cli | Integration | AC-46 |
| `AC47_AIEndpointUnavailableNoImpactTest` | cli | Integration | AC-47 |
| `AC48_ClasspathInspectionTest` | cli | Unit | AC-48 |
| `GradleDependencyConstraintTest` | cli | Unit | ai module not in scan dependency tree |

### Commit

```
feat(FR-11): verify no LLM dependency on the blocking analysis path

Satisfies: AC-46, AC-47, AC-48

- Architectural constraint tests via classpath inspection
- Gradle dependency graph enforcement
- ExplainCommand stub (separate from scan)
- 4 test classes
```

---

## Step 12 — FR-12: Cross-platform reproducibility

### What to build

- `ConformanceSuite` in testkit:
  - Defines fixture repositories with known expected outputs.
  - `ConformanceRunner` that scans each fixture and compares against the golden output.
  - `VolatileFieldMasker` that strips volatile fields for comparison.
  - Runtime assertion that p95 is within target.
- `DeterminismEnforcer` utility:
  - TZ=UTC, LC_ALL=C set in the container entrypoint.
  - Verified in test.
- Enhance Dockerfile:
  - Set `ENV TZ=UTC LC_ALL=C` explicitly.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `ConformanceSuiteTest` | testkit | Integration | All fixtures produce expected output |
| `VolatileFieldMaskerTest` | testkit | Unit | Correct field masking |
| `AC49_CrossPlatformConformanceTest` | testkit | Integration | AC-49 |
| `AC50_MultiArchDeterminismTest` | testkit | Integration | AC-50 |
| `AC51_RuntimeAssertionTest` | testkit | Integration | AC-51 |

### Commit

```
feat(FR-12): cross-platform conformance suite as a release gate

Satisfies: AC-49, AC-50, AC-51

- ConformanceSuite with fixture repos and golden outputs
- VolatileFieldMasker
- TZ/locale enforcement in Dockerfile
- Runtime assertion
- 5 test classes
```

---

## Step 13 — FR-13: Monorepo project slicing

### What to build

- Enhance `ScanCommand` to accept repeatable `--project` flag.
- `ProjectSlicer` in application: splits findings by project path prefix/glob, attributes unmatched files to a default project.
- Enhance `SarifReporter` for multi-project runs.
- Enhance `BaselineComparator` for per-project comparison.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `ProjectSlicerTest` | application | Unit | Slicing, default project |
| `AC52_MultiProjectSlicingTest` | cli | Integration | AC-52 |
| `AC53_DefaultProjectTest` | cli | Integration | AC-53 |

### Commit

```
feat(FR-13): monorepo project slicing with per-project gating

Satisfies: AC-52, AC-53
```

---

## Step 14 — FR-14: Local developer execution

### What to build

- Verify existing contract works locally (should already, by design).
- Implement `--fail-on` override in `PolicyEvaluator`.
- Verify `doctor` command works outside CI.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `AC54_LocalExecutionDeterminismTest` | cli | Integration | AC-54 |
| `AC55_FailOnOverrideTest` | cli | Integration | AC-55 |

### Commit

```
feat(FR-14): local developer execution with --fail-on override

Satisfies: AC-54, AC-55
```

---

## Step 15 — FR-15: Composable tighten-only policy

### What to build

- `PolicyComposer` in the policy module:
  - Loads central bundle + `.debt-hunter.yml` from repo root.
  - Merges with tighten-only rule.
  - Rejects loosening with exit code 2.
- `PolicyCommand` show-effective: displays merged policy with provenance.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `PolicyComposerTest` | policy | Unit | Tightening, rejection of loosening |
| `AC56_TightenThresholdTest` | policy | Unit | AC-56 |
| `AC57_LoosenRejectionTest` | cli | Integration | AC-57 |
| `AC58_ShowEffectiveTest` | cli | Integration | AC-58 |

### Commit

```
feat(FR-15): composable tighten-only policy with provenance

Satisfies: AC-56, AC-57, AC-58
```

---

## Step 16 — FR-16: Time-windowed history analysis

### What to build

- `--history-window` flag processing in ScanUseCase.
- Pass window to engines via AnalysisRequest.
- Record in `run.historyDepth` as `partial(since=<date>)`.
- Validate against policy minimumHistoryDepth.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `AC59_WindowedAnalysisTest` | cli | Integration | AC-59 |
| `AC60_WindowBelowMinimumTest` | cli | Integration | AC-60 |

### Commit

```
feat(FR-16): time-windowed history analysis

Satisfies: AC-59, AC-60
```

---

## Step 17 — FR-19: Suppression workflow

### What to build

- `SuppressionRegistry` in the policy module:
  - Loads `.debt-hunter-suppressions.yml` from the repo.
  - Validates owner, reason, expiry.
  - Checks maxExpiryDays from policy.
  - Evaluates expiry against commit date (not wall clock).
- Integrate into `PolicyEvaluator`: suppressed findings excluded from gating.
- Enhance `MarkdownReporter` with active-suppression listing.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `SuppressionRegistryTest` | policy | Unit | Loading, validation, expiry |
| `AC61_SuppressedFindingExcludedTest` | cli | Integration | AC-61 |
| `AC62_ExpiredSuppressionTest` | cli | Integration | AC-62 |
| `AC63_ExcessiveExpiryRejectionTest` | cli | Integration | AC-63 |
| `AC64_SummaryListsActiveSuppressionsTest` | cli | Integration | AC-64 |

### Commit

```
feat(FR-19): suppression workflow with owner, reason, and expiry

Satisfies: AC-61, AC-62, AC-63, AC-64
```

---

## Step 18 — FR-17: Publish findings to Jira and Azure Boards

### What to build

- `WorkItemOrchestrator` in the integration module:
  - One item per fingerprint.
  - Creation gated by policy and rollout stage.
  - Resolution closes the item.
  - Regression reopens.
- `JiraPublisher`, `AzureBoardsPublisher` — narrow provider interfaces.
- `TransactionalOutbox` — at-least-once delivery with backoff.
- These run in the control plane, not in the pipeline.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `WorkItemOrchestratorTest` | integration | Unit | One-per-fingerprint, resolution, regression |
| `TransactionalOutboxTest` | integration | Unit | At-least-once, no duplicates |
| `AC65_NewCriticalCreatesItemTest` | integration | Unit | AC-65 |
| `AC66_NoDuplicatesTest` | integration | Unit | AC-66 |
| `AC67_ResolutionClosesItemTest` | integration | Unit | AC-67 |
| `AC68_RegressionReopensTest` | integration | Unit | AC-68 |
| `AC69_OutageRecoveryTest` | integration | Unit | AC-69 |

### Commit

```
feat(FR-17): work-item publication with one-per-fingerprint lifecycle

Satisfies: AC-65, AC-66, AC-67, AC-68, AC-69
```

---

## Step 19 — FR-18: Pull-request summaries

### What to build

- Enhance `MarkdownReporter` to produce a complete summary.md:
  - Verdict, new findings by severity, existing count, history depth.
  - Breached thresholds with rule/threshold/actual.
  - Active suppressions.
  - Degraded engines.
  - Publication failures.
  - Top 3 new findings by score.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `AC70_SummaryContentTest` | output | Unit | AC-70 |
| `AC71_DegradedRunSummaryTest` | output | Unit | AC-71 |
| `AC72_MarkdownRenderingTest` | output | Unit | AC-72: valid Markdown |

### Commit

```
feat(FR-18): pull-request summary with verdict, findings, and diagnostics

Satisfies: AC-70, AC-71, AC-72
```

---

## Step 20 — FR-20: AI explanation and remediation proposals

### What to build

**AI module:**

- `FindingExplainer` — calls a model endpoint with finding + evidence, returns labelled explanation.
- `RemediationAdvisor` — proposes remediation actions.
- `PromptGuard` — redacts source code unless repo opted in; enforces findings-and-evidence-only.
- `ProvenanceLabeller` — marks all generated text.
- `ExplainCommand` in CLI — invokes FindingExplainer for a given finding ID.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `FindingExplainerTest` | ai | Unit | Labelled output, model-authored marker |
| `PromptGuardTest` | ai | Unit | Source redaction, opt-in enforcement |
| `AC73_ExplainProducesLabelledOutputTest` | cli | Integration | AC-73 |
| `AC74_NoSourceWithoutOptInTest` | ai | Unit | AC-74 |
| `AC75_ModelUnavailableErrorTest` | cli | Integration | AC-75 |

### Commit

```
feat(FR-20): AI explanation and remediation with prompt guard

Satisfies: AC-73, AC-74, AC-75
```

---

## Step 21 — FR-21: Cross-repository analysis

### What to build

- `CrossRepositoryAnalyser` in the application module (control-plane scope):
  - Operates on aggregated, pseudonymised data.
  - Produces coupling maps at team level.
  - Advisory only, no gating influence.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `CrossRepositoryAnalyserTest` | application | Unit | Team-level output, no individual identity |
| `AC76_CouplingMapTest` | application | Unit | AC-76 |
| `AC77_NoIndividualIdentityTest` | application | Unit | AC-77 |

### Commit

```
feat(FR-21): cross-repository coupling analysis at team level

Satisfies: AC-76, AC-77
```

---

## Step 22 — FR-22: Automated pull-request generation

### What to build

- `FixAgent` in the ai module:
  - Generates a patch or branch.
  - Opens a pull request via source-hosting API.
  - Description cites finding ID, labels as auto-generated.
  - Rate-limited per repository.
  - Never auto-merges.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `FixAgentTest` | ai | Unit | Patch generation, labelling, rate limiting |
| `AC78_AutoPRCreationTest` | ai | Unit | AC-78 |
| `AC79_CIPipelineSubjectTest` | ai | Unit | AC-79 |
| `AC80_RateLimitTest` | ai | Unit | AC-80 |

### Commit

```
feat(FR-22): automated pull-request generation with rate limiting

Satisfies: AC-78, AC-79, AC-80
```

---

## Step 23 — Architecture rules engine and static-analysis adapter

### What to build

These engines were scoped for v1 but are not tied to a specific FR — they support FR-01's engine pluggability.

**Engine-architecture module:**

- `ArchitectureRulesEngine` implementing `AnalysisEngine`:
  - Reads declarative layer/dependency rules from `.debt-hunter-arch.yml`.
  - Checks import statements against allowed/denied patterns.
  - Produces ARCHITECTURE category findings.

**Engine-static-analysis module:**

- `StaticAnalysisEngine` implementing `AnalysisEngine`:
  - Consumes existing SonarQube or linter JSON/XML output.
  - Maps external findings to canonical model.
  - Adapter, not a re-analyser.

### Tests to write

| Test class | Module | Type | Verifies |
|---|---|---|---|
| `ArchitectureRulesEngineTest` | engine-architecture | Unit | Rule loading, violation detection |
| `StaticAnalysisAdapterTest` | engine-static-analysis | Unit | SonarQube output parsing |
| `MultiEngineIntegrationTest` | cli | Integration | All 3 engines running together |

### Commit

```
feat(engines): architecture rules and static-analysis adapter engines

- ArchitectureRulesEngine: declarative conformance
- StaticAnalysisEngine: SonarQube/linter output adapter
- Multi-engine integration test
```

---

## Step 24 — Final integration, documentation, and release readiness

### What to do

1. Run the full conformance suite across all fixture repositories.
2. Run `./gradlew build` — every module, every test, zero failures.
3. Run `./gradlew jacocoTestReport` — verify 80% line coverage per module.
4. Generate a CI workflow file (`.github/workflows/ci.yml`) that runs the full build on push.
5. Update `README.md` with:
   - Project overview.
   - Quick start (container invocation).
   - CLI reference.
   - Exit-code table.
   - Link to the HLD and functional specification.
6. Create `CONTRIBUTING.md` with development setup instructions.
7. Create `docs/architecture-decisions/` with ADR files for each AD-01 through AD-12.

### Commit

```
feat(release): CI workflow, documentation, and release readiness

- GitHub Actions CI workflow
- README with quick start and CLI reference
- CONTRIBUTING.md
- Architecture decision records
- All 80 ACs verified, all tests passing
```

---

## Summary checklist

Before declaring the project complete, verify:

- [ ] `./gradlew build` passes with zero failures
- [ ] Every AC (AC-01 through AC-80) has at least one passing test method
- [ ] Every BC (BC-01 through BC-11) has a passing test method
- [ ] JaCoCo reports ≥80% line coverage per module
- [ ] The domain module has zero I/O dependencies
- [ ] The ai module is not in the dependency tree of cli → application → domain
- [ ] The Dockerfile produces a non-root image that runs with --network none
- [ ] The conformance suite produces byte-identical output across runs
- [ ] Every commit message references its FR and lists satisfied ACs
- [ ] There are zero `@Disabled` tests
