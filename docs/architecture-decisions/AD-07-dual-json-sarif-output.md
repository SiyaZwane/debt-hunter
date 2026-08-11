# AD-07: One canonical finding model, two report formats

## Context

Debt Hunter needs to serve two different consumers from the same scan: its own native JSON report
(stable, versioned, the format baseline comparison and `explain`/`publish` read back) and SARIF
2.1.0 (so results show up natively in GitHub code scanning, IDE SARIF viewers, and any other tool in
that ecosystem). Maintaining these as two independently-computed pipelines would risk exactly the
kind of drift — two findings lists that don't agree — this project can't afford.

## Decision

`ScanUseCase` computes one `ScanResult` (one `List<Finding>`, one `PolicyResult`) and hands it to
both `JsonReporter` and `SarifReporter` unchanged. Each reporter is purely a projection: it maps the
same `Finding` records into its own output shape and writes its own file, but neither one recomputes
or reinterprets what a finding is.

## Consequences

- The two report files can never disagree about which findings exist, what their severity is, or
  which ones are new versus existing against a baseline — they're views over one shared value, not
  two separate computations.
- SARIF-specific concerns (rule metadata, per-project `runs`, its own fingerprinting requirements)
  live entirely inside `SarifReporter`; adding a third output format (the Markdown summary,
  `MarkdownReporter`; the metrics file, `MetricsReporter`) followed the identical pattern without
  touching `ScanUseCase` or either existing reporter.
- Monorepo project slicing (`--project`) slices the *shared* findings list once
  (`ProjectSlicer`), and both `SarifReporter.writeMultiProject` and the per-project policy
  evaluation consume that same sliced view — again, one computation, multiple projections.
