# Architecture decision records

These records capture the key architectural decisions behind Debt Hunter's design: the context
that prompted each one, the decision itself, and its consequences. They're written after the fact,
from the implementation, rather than before it — each one describes a decision that is already
load-bearing in the codebase, not a proposal.

| ADR | Decision |
|---|---|
| [AD-01](AD-01-maven-multi-module-build.md) | Maven multi-module reactor as the build tool |
| [AD-02](AD-02-domain-purity-enforcement.md) | Domain purity enforced at build time, not just by convention |
| [AD-03](AD-03-engine-plugin-architecture.md) | Analysis engines as independent, pluggable modules behind one SPI |
| [AD-04](AD-04-codemaat-subprocess-isolation.md) | Code Maat run as an isolated subprocess, never a compile-time dependency |
| [AD-05](AD-05-content-anchored-fingerprints.md) | Content-anchored fingerprints, stable across renames and reformats |
| [AD-06](AD-06-deterministic-serialisation-and-clocks.md) | Deterministic serialisation and injected clocks everywhere |
| [AD-07](AD-07-dual-json-sarif-output.md) | One canonical finding model, two report formats |
| [AD-08](AD-08-tighten-only-policy-composition.md) | Tighten-only composition between central and per-repository policy |
| [AD-09](AD-09-ai-off-the-gate-path.md) | AI is advisory only, structurally excluded from the gate path |
| [AD-10](AD-10-prompt-guard-source-redaction.md) | Source code redacted from AI prompts unless explicitly opted in |
| [AD-11](AD-11-test-the-real-thing.md) | Test the real thing over mocking the transport |
| [AD-12](AD-12-hardened-offline-container.md) | Hardened, non-root, network-isolated container runtime |
