# AD-08: Tighten-only composition between central and per-repository policy

## Context

An organisation typically wants a central policy bundle that sets an organisation-wide floor (e.g.
"no new critical findings anywhere"), while individual repositories may reasonably want *stricter*
local rules for their own context. What a repository must never be able to do is quietly loosen the
central policy — that would turn "central policy" into a suggestion, defeating the point of having
one.

## Decision

`PolicyComposer` merges a repository-local policy override on top of a parsed central `PolicyBundle`
rule by rule. If a local rule would raise a `maxCount` threshold, weaken a severity requirement, or
otherwise loosen anything the central policy already constrains, composition throws
`PolicyLoosenedException` and the scan fails with a configuration error — it does not silently fall
back to the central value, and it does not silently apply the loosened one.

## Consequences

- A repository can always add *new*, stricter rules of its own, or tighten an existing central
  threshold, but a loosening attempt is a hard, loud failure at scan time, not something that could
  pass silently in a code review of the local policy file.
- `PolicyCommand` (`debt-hunter policy`) exists specifically so a repository owner can see the
  *effective* merged policy and its provenance per field before relying on it — composition is
  deterministic and inspectable, not a black box.
- The same composer is reused for both `FULL` and `PULL_REQUEST` policy modes and for per-project
  (monorepo) evaluation, so the tighten-only guarantee applies uniformly regardless of how the scan
  is sliced.
