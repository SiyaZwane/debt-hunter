# AD-10: Source code redacted from AI prompts unless explicitly opted in

## Context

`explain` sends a prompt to an external AI service. Without a deliberate guard, it would be easy for
a future change to start including a finding's actual source snippet "to make the explanation
better" — and just as easy for that to happen for a repository whose owner never agreed to send
their source code to a third party.

## Decision

`PromptGuard` is the only place a prompt is assembled, and it takes an explicit `sourceOptIn`
boolean alongside the finding and any available source snippet. The prompt always includes the
finding's rule, path, message, and evidence (data Debt Hunter already computed itself), but a source
snippet is included verbatim only when `sourceOptIn` is `true`; otherwise, even if a snippet was
supplied, the prompt either omits any source section entirely or includes an explicit
`source: [redacted — ...]` marker. `HttpExplainer` itself no longer constructs any part of the
prompt — it only transmits the string `PromptGuard` gave it, so redaction can't be bypassed by
calling the transport directly.

## Consequences

- `AC74_NoSourceWithoutOptInTest` asserts the negative directly: build a prompt from a finding
  carrying a real, identifiable source snippet, with `sourceOptIn=false`, and assert the snippet's
  content is absent from the resulting prompt — not just "we didn't call the method that would add
  it", but "the string genuinely isn't there".
- Every response `FindingExplainer` gets back — explanation and remediation both — is labelled with
  a `[AI-generated]` marker (`ProvenanceLabeller`) before it reaches the CLI's output, so a reader
  can never mistake AI-authored text for Debt Hunter's own deterministic findings.
- Extending `explain` to send more context in the future (e.g. a wider code excerpt) means widening
  `PromptGuard`'s contract, not adding a second, competing way to assemble a prompt.
