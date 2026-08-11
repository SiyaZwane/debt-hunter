# AD-09: AI is advisory only, structurally excluded from the gate path

## Context

An AI service is inherently less reliable and less deterministic than the rest of this tool: it can
be slow, unreachable, rate-limited, or simply wrong. Debt Hunter's core promise — a deterministic
pass/fail exit code from `scan` — cannot depend on something with those properties. At the same time,
AI-assisted explanation and remediation is genuinely useful and worth having as a feature.

## Decision

Put every AI capability behind its own `ai` module and its own CLI command, `explain` (and the
non-gating `FixAgent` for automated fix proposals). Neither `application` nor `domain` may depend on
`ai` — checked structurally by the module graph, the same way `domain` purity is (AD-02). `explain`
shares no collaborator with `ScanCommand`/`ScanUseCase`: an unreachable AI endpoint changes only
`explain`'s own outcome, never a scan's exit code, and always resolves to
`ExitCode.POLICY_SATISFIED` even on failure — the failure is signalled on stderr, not through the
process exit code, because a scan-adjacent AI outage must never look like a build failure to a CI
pipeline that also happens to be running `explain`.

## Consequences

- `AC47_AIEndpointUnavailableNoImpactTest` can prove this directly: point `explain` at an
  unreachable endpoint, then run a real `scan` in the same process, and assert the scan is completely
  unaffected — not just "probably fine", but mechanically incapable of being affected, because the
  two commands share no collaborator.
- This constrained every later AI-related feature (FR-20's `FindingExplainer`, FR-22's `FixAgent`):
  when a later change seemed to want to alter `explain`'s exit-code behaviour on failure, the
  already-merged AC-47 test caught it immediately — an already-satisfied acceptance criterion from
  an earlier FR is a hard constraint that a later FR may never silently break.
- `FixAgent` never auto-merges anything it opens — every pull request it creates is still subject to
  the target repository's own CI and review, exactly like a human-authored one.
