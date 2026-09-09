---
name: dejavu-error-triage
description: Diagnose a DejaVu UnexpectedRecompositionsError, missing tag mapping, or suspicious recomposition count from a failing Compose UI test. Use for explaining a failure or applying a bounded fix. Read-only diagnosis stays read-only; iterative performance optimization belongs to dejavu-perf-loop.
---

# DejaVu error triage

Explain when asked for diagnosis; make a bounded fix when authorized. Determine
whether this is an unexpected failure or an intentionally failing budget in a
tracer accuracy test. Preserve deliberately inefficient fixtures and expected errors.

## Gather evidence

Read the available error and source: expected/actual, failed tag and location,
other counts, timeline and possible-cause hints. Retrieve missing context locally
when possible. Truncated logs can support a provisional diagnosis; identify the
missing evidence instead of blocking all work.

Separate observations from hypotheses. Equal parent/child counts suggest a
cascade but do not establish causality. Dirty bits do not prove a type is unstable
or one parameter is responsible; interpret them with compiler/source context.
Same-value writes can be deliberate under a mutation policy.

| Observation | Investigate before changing code |
|---|---|
| Count exceeds budget | Interaction, scheduling, parent reads, parameter identity, mutation policy and budget intent |
| Equal-looking values written again | Equality and snapshot policy; structural equality cannot repair reference-only equality by itself |
| Parent and siblings have equal counts | Where state is read and which child inputs change |
| Zero or unmapped count | Node existence, lazy visibility, source information, tag mapping and lifecycle |
| Changed UI with zero tracked updates | Disabled/lost tracing, including mixed Android-rule/KMP-helper lifetime; stable is not proof |
| Only Wasm misses the failure | Returned `TestResult`, assertions inside helper body and awaited cleanup |
| Invalid assertion arguments | Mutually exclusive or negative bounds in the test |

Do not prescribe `@Stable` or `@Immutable` from a hint. They are contracts: mutable
fields or collections can make the promise false and hide UI updates. List keys
and `CompositionLocal` are not universal count fixes. Confirm the mechanism.

## Resolve and verify

Change the smallest demonstrated cause of a real regression. Preserve UI behavior
and existing assertions. For accuracy tests, compare DejaVu with a separate
unkeyed `SideEffect` counter; the high count may be correct. Don't raise a budget
or optimize the fixture to hide a tracer defect.

Re-run the failing actual UI test after a fix. For review-only work, explain the
proposed verification without editing or claiming a run. Report evidence,
confidence, change (if any), and result. Companions are optional, not mandatory
handoffs.

More detail: [error anatomy](https://dejavu.mmckenna.me/0.5.0/error-messages/)
and [causality limits](https://dejavu.mmckenna.me/0.5.0/causality-analysis/).
