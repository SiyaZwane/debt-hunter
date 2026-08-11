# AD-04: Code Maat run as an isolated subprocess, never a compile-time dependency

## Context

Code Maat is a Clojure JVM tool. Depending on it directly from `engine-codemaat` would pull the
Clojure runtime onto every classpath that includes that module, complicate the container image (two
JVM ecosystems instead of one), and make it impossible to build or test a "core" variant of Debt
Hunter with no Code Maat available at all — a real deployment scenario for environments that don't
want to install it.

## Decision

`CodeMaatEngine` never imports a Code Maat or Clojure class. It resolves an external executable
path (`CODEMAAT_EXECUTABLE`, defaulting to `/opt/code-maat/code-maat`), builds the git2-format log
Code Maat expects itself (`CodeMaatLogWriter`, using JGit — not native `git` — so it doesn't need a
`git` binary either), runs Code Maat as a `ProcessBuilder` subprocess with a hard timeout, and parses
its CSV stdout. A missing binary is reported as `EngineResult.failed(...)`, not a startup crash.

## Consequences

- `AC10_CoreVariantNoCodeMaatTest` can assert, directly, that no Code Maat or Clojure class exists on
  `engine-codemaat`'s classpath — the isolation is a checkable fact, not just an intention.
- Concurrent stdout/stderr draining (`CodeMaatLogWriter`'s stream readers) is necessary specifically
  because Code Maat is an external process communicating over pipes with bounded buffers — a problem
  that wouldn't exist with an in-process library call, but is fully contained inside
  `CodeMaatEngine` and invisible to every other module.
- The engine degrades gracefully (per analysis type: `revisions`, `coupling`, `age`, `authors`) if
  Code Maat's output changes shape or one analysis type fails, rather than failing the whole engine
  over a single malformed CSV column.
