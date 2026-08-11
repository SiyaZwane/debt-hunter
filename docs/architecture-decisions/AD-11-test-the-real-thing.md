# AD-11: Test the real thing over mocking the transport

## Context

Debt Hunter integrates with several real transports: HTTP (publication, AI explanation, source-host
pull-request creation), Git (via JGit and native `git`), and an external subprocess (Code Maat).
Mocking these at the transport layer (a mocked `HttpClient`, a mocked Git provider) would make tests
fast, but would also make them structurally unable to catch a whole class of real bugs — a malformed
request, a header set on the wrong object, a status-code branch that's never actually exercised
against a real server.

## Decision

Wherever a "real" implementation is fast and reliable enough to run in a unit test, use it instead of
a mock: a real `com.sun.net.httpserver.HttpServer` bound to an ephemeral local port stands in for
every external HTTP API (`HttpExplainerTest`, `HttpResultUploaderTest`, `AC78_AutoPRCreationTest`),
and a real JGit-backed repository (`FixtureRepoBuilder`) stands in for every Git-dependent test,
including the ones exercising rename tracking, shallow-clone detection, and Code Maat's log format.
Mocks (Mockito) are reserved for genuine interface seams with no meaningful "real" implementation to
run in a fast test — a fake work-item provider, a fake rate limiter, an `Explainer` stub for testing
`FindingExplainer`'s own orchestration logic in isolation from any transport at all.

## Consequences

- `HttpExplainerTest` verifies the *actual* request Debt Hunter sends — headers, body, timeout
  behaviour, non-2xx handling — against a server that really parses HTTP, not against a mock that
  only knows to return what it was told to return.
- Test setup is slightly heavier per test class (start a server, build a fixture repo) in exchange
  for tests that fail when the real integration would fail, not only when the mocked expectations
  happen to be wrong.
- This convention appears consistently across the codebase — `AC26_FixtureSuiteTest`,
  `RealCodeMaatSmokeTest`, `AC78_AutoPRCreationTest` — rather than being a one-off choice for a
  single module, which is what makes it trustworthy as a project-wide guarantee rather than a
  spot-check.
