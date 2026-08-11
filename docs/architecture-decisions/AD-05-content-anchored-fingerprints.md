# AD-05: Content-anchored fingerprints, stable across renames and reformats

## Context

A finding's identity needs to survive the ordinary churn of a codebase — a file gets renamed, a
formatter reflows some lines, a method moves — without the finding disappearing from a baseline
comparison and reappearing as spuriously "new". A fingerprint keyed on file path and line number
alone breaks under all three of those changes.

## Decision

`Fingerprinter` computes a SHA-256 digest over exactly four components: the rule id, a
*canonical* path (resolved across renames via `RenameTracker`'s `git log --follow`, not the raw
current path), a symbol anchor (the enclosing type or method, when an engine can derive one via
`SymbolAnchorExtractor`) instead of a line number, and a normalised evidence key for anything else
that's genuinely part of the finding's identity (e.g. a coupled file, for temporal-coupling
findings). Volatile magnitudes — revision counts, scores, messages — are deliberately excluded.

## Consequences

- A finding survives a pure rename (fingerprint unchanged, because the canonical path is unchanged)
  and survives a reformat (fingerprint unchanged, because line number was never part of the input).
- A finding whose enclosing method is genuinely extracted or moved *does* get a new fingerprint —
  the identity is anchored to the symbol, not falsely preserved across a real structural change.
- Baseline comparison (`BaselineComparator`) and suppression matching both key off this fingerprint,
  so this one decision is what makes "new vs. existing" and "still suppressed vs. no longer
  applicable" meaningful across scans, rather than noisy.
- Every engine adapter is responsible for computing its own canonical path and symbol anchor before
  calling `Fingerprinter` — the algorithm itself doesn't know about Git or ASTs, keeping it a pure,
  five-line, easily-tested function in `domain`.
