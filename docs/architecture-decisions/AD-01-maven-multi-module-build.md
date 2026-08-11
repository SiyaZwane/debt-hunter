# AD-01: Maven multi-module reactor as the build tool

## Context

Debt Hunter is split into more than a dozen small, single-responsibility modules (`domain`,
`engine-spi`, three separate engine adapters, `policy`, `output`, `application`, `ai`,
`integration`, `cli`, `testkit`). The build needs to: enforce dependency direction between them
(nothing may depend "outward" toward `cli`), run a separate `integrationTest` source set per module
with its own tag-based inclusion/exclusion, apply the same static-analysis and coverage gates
uniformly, and package `cli` as a single runnable jar for the container image.

## Decision

Use a Maven multi-module reactor: one root `pom.xml` declaring shared dependency versions, plugin
configuration, and quality gates, with every module inheriting from it and declaring only its own
direct dependencies.

## Consequences

- Module boundaries are enforced structurally: a module can only use what it explicitly declares as
  a dependency, and the reactor's build order is derived automatically from those declarations —
  there's no way to accidentally create a dependency cycle or an "outward" dependency without it
  showing up immediately as a build failure.
- Cross-module concerns (Spotless formatting, SpotBugs, JaCoCo's 80% line-coverage gate, the
  `integrationTest` source set wiring) are configured once, in `pluginManagement`/`build/plugins` in
  the root `pom.xml`, and apply identically everywhere rather than needing to be copy-pasted or
  drift between modules.
- The domain-purity rule (AD-02) is expressed as a Maven Enforcer `bannedDependencies` rule scoped
  to the `domain` module alone, which would be harder to express as cleanly with a flatter build.
- `mvn -pl <module> -am <goal>` scopes a build to one module plus its dependencies, which keeps the
  inner development loop fast even though the full reactor is large.
