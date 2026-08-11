# AD-03: Analysis engines as independent, pluggable modules behind one SPI

## Context

Debt Hunter needs to combine fundamentally different kinds of analysis — Git-history mining (Code
Maat), declarative static conformance checks (architecture rules), and adapted third-party tool
output (SonarQube) — into one scan, with the ability to add a fourth kind later without touching
the first three or the orchestration logic that runs them.

## Decision

Define a small `engine-spi` module with one interface, `AnalysisEngine` (`descriptor()`,
`supports(RepositoryContext)`, `analyse(AnalysisRequest, ProgressSink)`), and give every engine its
own Maven module (`engine-codemaat`, `engine-architecture`, `engine-static-analysis`) depending only
on `engine-spi` (and transitively `domain`) plus whatever engine-specific libraries it needs.
`ScanUseCase` accepts a `List<AnalysisEngine>` it knows nothing about beyond the interface, checks
`supports()` before calling `analyse()`, and aggregates whatever comes back.

## Consequences

- Each engine's dependencies stay isolated: `engine-architecture` depends on SnakeYAML,
  `engine-static-analysis` on Jackson, `engine-codemaat` on JGit — none of that leaks into the
  others or into `application`/`domain`.
- `ScanUseCase` never grows an `if instanceof CodeMaatEngine` branch; every engine is exercised
  identically, including timeout enforcement and health-status recording (`EngineResult`'s
  `OK`/`DEGRADED`/`FAILED`), which is what lets one engine fail (e.g. a missing Code Maat binary)
  without affecting the others or aborting the scan.
- `supports(RepositoryContext)` is the extension point for engines that don't apply everywhere (Code
  Maat needs Git and non-shallow history; the architecture and static-analysis engines need neither
  and always return `true`).
- Wiring a new engine into the CLI is a one-line addition to the engines list passed into
  `ScanRequest` — no change to `ScanUseCase`, `PolicyEvaluator`, or any reporter.
