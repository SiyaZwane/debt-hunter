# AD-06: Deterministic serialisation and injected clocks everywhere

## Context

"Byte-identical output across runs" is a hard project requirement, checked by a real conformance
suite (`ConformanceRunner`/`ConformanceSuite`) that scans the same fixture through two different
environments (time zone, locale, or CPU architecture) and diffs the result. Two ordinary sources of
nondeterminism threaten this: JSON serialisation order (`HashMap` iteration order, unsorted object
properties) and any direct read of the wall clock in production logic.

## Decision

Centralise Jackson configuration in one `DeterministicObjectMapper` factory
(`ORDER_MAP_ENTRIES_BY_KEYS`, `SORT_PROPERTIES_ALPHABETICALLY`, fixed UTC time zone) reused by every
reporter, and forbid direct `Instant.now()`/`Clock.systemUTC()` calls in production logic — every
class that needs "now" (`ScanUseCase`, `TransactionalOutbox`, `PerRepositoryRateLimiter`) takes a
`java.time.Clock` as a constructor parameter, defaulting to `Clock.systemUTC()` only in the
production (no-arg) constructor.

## Consequences

- Tests can advance a `MutableClock` deterministically instead of sleeping real time to exercise
  backoff, rate-limit-window expiry, or suppression-expiry logic — this is what makes
  `TransactionalOutboxTest` and `AC80_RateLimitTest` fast and non-flaky.
- Two scans of the same commit, run seconds apart or on different machines, produce byte-identical
  `debt-hunter.json`/`debt-hunter.sarif` output, because no timestamp in the output was ever read
  from the actual wall clock at serialisation time — the one exception, `AnalysisRun.timestamp`, is
  masked out by `VolatileFieldMasker` before the conformance suite compares two runs, since it's
  expected to differ and is documented as such rather than silently ignored.
- `DeterminismEnforcer` (invoked only from `DebtHunterCli.main`, never from code under test) forces
  the JVM's default time zone and locale at process start, so environment leakage is caught at the
  one place it could actually happen in production, without constraining how tests set those same
  properties themselves for `AC-14`-style cross-platform checks.
