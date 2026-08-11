# Contributing to Debt Hunter

## Prerequisites

- Java 21 (the reactor targets `--release 21`)
- No local Maven install needed — use the bundled `./mvnw` wrapper
- Docker, only if you want to run the `docker`-tagged container tests or build the image locally

## Building

```bash
./mvnw clean verify
```

Runs every module's unit tests, Spotless (Google Java Format) formatting check, SpotBugs static
analysis, the domain-purity dependency check (via maven-enforcer), and JaCoCo's 80%-line-coverage
gate — all with zero failures, across the whole reactor. This is the same command CI runs and the
one you should run before opening a PR.

To auto-fix formatting instead of just checking it:

```bash
./mvnw spotless:apply
```

## Running the CLI locally

For the day-to-day edit/test loop, run the packaged jar directly rather than rebuilding the Docker
image — it's a plain JVM process, seconds instead of minutes, and skips `jdeps`/`jlink`/image
layering entirely:

```bash
./mvnw -pl cli -am package -DskipTests
java -jar cli/target/debt-hunter.jar scan --repo . --output-dir ./out
```

To scan a repository other than this one, point `--repo` at any other local checkout — Debt Hunter
never clones or fetches, so it must already exist on disk — and `--output-dir` wherever you want the
reports written; neither has to be under this project's directory:

```bash
java -jar /path/to/debt-hunter/cli/target/debt-hunter.jar scan \
  --repo /path/to/other-project \
  --output-dir /path/to/other-project/debt-hunter-output
```

For a shorter command, add a shell function so `debt-hunter` runs from anywhere (adjust the jar
path to where you cloned this repo, and re-run the `package` command above whenever you rebuild):

```bash
# ~/.zshrc or ~/.bashrc
debt-hunter() { java -jar /path/to/debt-hunter/cli/target/debt-hunter.jar "$@"; }
```

On Windows PowerShell, add a function to your `$PROFILE` instead:

```powershell
# $PROFILE
function debt-hunter { java -jar C:\path\to\debt-hunter\cli\target\debt-hunter.jar @args }
```

Either way, the call itself is the same:

```
debt-hunter scan --repo /path/to/other-project --output-dir /path/to/other-project/debt-hunter-output
```

Reach for the Docker image (see "Building and testing the container image" below) only when you
need to verify something the jar genuinely can't from a plain JVM process: non-root execution,
`--network none`, or bind-mount permission behaviour under the image's baked-in uid 10001. These
can differ from a `java -jar` run in ways that only show up on a real container — a bind-mount
permissions bug once passed locally on macOS's Docker Desktop and only surfaced in CI on a real
Linux Docker daemon. Use the jar for fast iteration, the image as a slower pre-merge check, not one
in place of the other.

## Test suites

Tests live in three source sets, each with a different cost and a different default:

| Source set | Runs by default? | What belongs here |
|---|---|---|
| `src/test/java` | Yes, via `verify` | Fast unit tests |
| `src/integrationTest/java`, tag `integration` | Yes, via `verify -Dfailsafe.groups=integration` | Slower tests exercising real collaborators (real Git fixtures, local HTTP servers) but no external services |
| `src/integrationTest/java`, tag `docker`/`platform`/`codemaat-real` | No — excluded by default | Tests needing a real Docker daemon, real GitHub/Azure DevOps credentials, or a real Code Maat install |

Run the integration suite:

```bash
./mvnw verify -Dfailsafe.groups=integration
```

Run the container-dependent suite (needs a working Docker daemon; builds the image once and reuses
it across the docker-tagged test classes):

```bash
./mvnw -pl cli -am verify -Dfailsafe.groups=docker
```

### Naming and coverage conventions

- Every acceptance criterion (AC-NN) and boundary condition (BC-NN) from the functional
  specification gets its own test class, named `AC<NN>_<Description>Test` /
  `BC<NN>_<Description>Test`, with a test method named `ac<nn>_<description>()` /
  `bc<nn>_<description>()`.
- No test may use `@Disabled`. If a test can't run in a given environment, gate it with
  `Assumptions.assumeTrue(...)` or `@DisabledOnOs(...)` instead, so it's still discoverable and
  still runs everywhere it can.
- Prefer testing the real thing over mocking it: a real local `HttpServer` instead of a mocked HTTP
  client, a real JGit-backed repository (`FixtureRepoBuilder`) instead of a mocked Git provider.
  Reach for a test double only for a collaborator that is itself an interface with no meaningful
  "real" implementation to exercise in a fast test (e.g. a fake work-item provider).

## Code conventions

- **The `domain` module has zero I/O, framework, or engine dependencies.** This is enforced at
  build time by a maven-enforcer banned-dependencies rule (see `domain/pom.xml`) — the build fails
  if you add one, rather than relying on review to catch it.
- **The `ai` module never sits between `cli` and `application`/`domain`.** AI/LLM behaviour is
  reachable only through the standalone `explain` command; it must never become a dependency of
  `ScanUseCase` or anything the `scan` gate path calls.
- **No wall-clock reads in production logic.** Use an injected `java.time.Clock` wherever you need
  "now" — see `ScanUseCase`, `TransactionalOutbox`, `PerRepositoryRateLimiter` for the pattern. This
  is what makes deterministic-output tests possible at all.
- **Jackson serialisation goes through the shared `DeterministicObjectMapper`.** It sorts map keys
  and properties and fixes the time zone to UTC, so two runs of the same scan never differ only in
  key ordering.
- Every public method gets a one-line Javadoc description plus `@param`/`@return` where applicable
  — not decorative, an actual description of the contract.
- No `System.exit()` outside `DebtHunterCli` — every other component reports its outcome as a
  return value, never a process side effect.

## Commit and PR conventions

- One logical unit of work per commit; commit messages follow `feat(FR-XX): <short title>` (or
  `feat(engines): ...` / `feat(release): ...` for cross-cutting steps), with a body listing every
  acceptance criterion the commit satisfies.
- Run `./mvnw clean verify` and `./mvnw verify -Dfailsafe.groups=integration` before opening a PR —
  both must pass with zero failures.
- New engines, commands, or reporters need their own AC/BC-numbered tests if the functional
  specification defines one for the behaviour you're adding; otherwise, a descriptively-named unit
  test is enough.

## Building and testing the container image

```bash
docker build -t debt-hunter .
scripts/smoke-test-container.sh debt-hunter
```

The smoke test builds the image, runs it offline (`--network none`) against a real fixture
repository, and asserts: it exits `0` or `1`, all four report files are produced, the container
never runs as root, and it requests no elevated Linux capabilities.
