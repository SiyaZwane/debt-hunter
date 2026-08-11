# AD-02: Domain purity enforced at build time, not just by convention

## Context

`domain` holds the canonical model every other module speaks in terms of: `Finding`, `Severity`,
`Category`, `ScanResult`, `Fingerprinter`, and friends. If this module ever gained a dependency on
an I/O, serialisation, or engine library, every consumer would inherit that dependency transitively,
and the model would stop being safely shareable across engine adapters, the CLI, and the AI module.
Code review alone is a weak guarantee here — a single unnoticed `import com.fasterxml.jackson...`
in a new domain class would silently violate the boundary.

## Decision

Bind a Maven Enforcer `bannedDependencies` rule to `domain`'s `validate` phase, explicitly excluding
Jackson, JGit, SnakeYAML, picocli, and Spring. The build fails immediately if any of these appear on
`domain`'s classpath, before a single test even runs.

## Consequences

- The zero-I/O guarantee is a build-time fact, not a documentation claim — `domain/pom.xml` is the
  single source of truth, and it fails loudly and immediately (at `validate`, the very first phase)
  rather than surfacing as a subtle runtime coupling discovered much later.
- Every other module can depend on `domain` without pulling in framework or I/O transitively, which
  is what keeps `ai`, `engine-static-analysis`, and `cli` free to choose their own JSON/YAML
  libraries independently.
- Adding a genuinely new domain-level capability that needs, say, a UUID or time API is fine — the
  rule only bans the five specific groups that would reintroduce I/O or framework coupling, not the
  JDK itself.
