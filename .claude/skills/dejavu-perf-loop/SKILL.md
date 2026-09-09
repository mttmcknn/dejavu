---
name: dejavu-perf-loop
description: Measure and reduce unnecessary recompositions in application code using DejaVu tests, preserving UI behavior and an agreed performance budget. Use when asked to optimize or iteratively tighten a recomposition budget. Do not optimize deliberate tracer accuracy fixtures or turn a diagnosis-only request into edits.
---

# DejaVu performance loop

Optimize one interaction using measured evidence. Respect the user's scope and
budget. DejaVu's own excessive-recomposition samples often validate the tracer:
retain those behaviors and expected failures unless changing the fixture itself
is explicitly requested.

## Establish a valid baseline

Reuse relevant tests and recent valid measurements; don't create parallel tests
or repeatedly lower arbitrary budgets to discover an available count. On Android,
`getRecompositionCount(tag)` reads it. A failure also reports it; for accuracy
investigations use an independent unkeyed `SideEffect` counter.

Confirm the interaction occurred, initial content settled, counts were reset
without clearing history, and tracking remained active. Zero with changing UI is
suspicious. Let test helpers own lifecycle; on KMP return `TestResult` and keep
assertions inside the helper body.

## Choose a justified change

Recompositions count executions, not state variables. Several writes can coalesce
into one pass; one source can cause several passes. Derive expected behavior from
the interaction, scheduling and measurement. There is no general "N state sources
implies N recompositions" floor.

Inspect source before applying a hint. Consider narrower child input when only a
coarse value matters, a deferred read when a parent needlessly subscribes, or
`derivedStateOf` when the result changes less often than its inputs. Keys address
item identity, not every cascade. `@Stable`/`@Immutable` require truthful contracts
and must never conceal mutable state. Same-value writes and equal counts alone
don't prove bugs.

Apply a small coherent change and re-run the relevant UI test. Preserve visible
behavior and existing assertions. Compare before/after under the same Compose
version and timing conditions. If the measured behavior already meets the
requested contract, no optimization is needed.

## Stop with a useful regression guard

Use `exactly = N` for deterministic accuracy contracts, `assertStable()` for zero,
and `atMost = N` when the user's contract is an upper budget. Don't force a budget
into exact counts or silently relax it. Retain a stable-sibling check when the
interaction actually promises sibling stability.

Stop when the agreed contract passes, further work is outside scope, or there is
no useful next change. Report before/after counts, UI correctness, retained budget
and verification. Distinguish unrun tests from passes. Use companion test-writing
or triage skills when available and helpful, without mandatory handoffs or new
confirmation gates for authorized work.

Public patterns: [examples](https://dejavu.mmckenna.me/0.5.0/examples/).
