# AD-12: Hardened, non-root, network-isolated container runtime

## Context

Debt Hunter is meant to run inside CI pipelines and, potentially, against untrusted or sensitive
source code. A container that needs network access to function, or that runs as root, is a larger
attack surface than necessary and a harder sell for security-conscious adopters — and a scan
genuinely never needs the network at all (publication is optional and explicit; AI explanation is a
separate command, not part of `scan`).

## Decision

Build a three-stage Dockerfile: compile in a full Maven/JDK image, `jlink` a minimal custom JRE
containing only the modules the jar actually uses (via `jdeps --print-module-deps`), then assemble
the runtime image on `debian:12-slim` with a dedicated non-root user (`debt-hunter`, uid 10001), no
elevated capabilities, and `git` as the only extra runtime dependency (needed for
`RenameTracker`'s `git log --follow`, not for any network access). `--network none` is a
first-class, tested mode, not an afterthought.

## Consequences

- `AC05_OfflineContainerScanTest` and the `scripts/smoke-test-container.sh` smoke test both run the
  built image with `--network none` and assert a normal, successful scan — proving the "never needs
  the network" claim rather than just asserting it in documentation.
- `AC07_NonRootImageTest`, `BC06_EmptyWorkspaceTest`, `BC07_RestrictivePermissionsTest`, and
  `BC08_ReadOnlyOutputDirContainerTest` each verify one specific container-boundary guarantee (image
  user, empty/unreadable/unwritable mounts) directly against the real built image via a real Docker
  daemon, not by inspecting the Dockerfile's text.
- The jlink stage keeps the final image's Java footprint to only what's actually reachable from the
  `cli` jar's dependency graph, rather than shipping a full JDK — smaller attack surface and smaller
  image, for free, as a consequence of how the build stages are structured rather than a separate
  optimisation effort.
