---
name: dejavu-test-writer
description: Write or extend DejaVu recomposition assertions in an existing Compose UI test setup, including Android instrumentation and KMP common tests. Use for regression coverage, independent counter accuracy tests, and Compose-version behavior tests. A request only to explain a failure belongs to triage; installing missing dependencies belongs to onboarding.
---

# DejaVu test writer

Establish the intended behavior before choosing an assertion. In DejaVu itself,
inefficient samples are deliberate tracer accuracy fixtures. Preserve them,
compare counts to an independent **unkeyed** `SideEffect`, and assert that an
intentionally too-tight budget throws `UnexpectedRecompositionsError`. Do not
turn test writing into app optimization or weaken expectations to get green tests.

## Extend the right test

Find existing coverage with `rg` and inspect its host, clock and source set. When
adding coverage is authorized, augment the relevant test by default; a separate
file is useful for a different lifecycle or target. No extra confirmation is
needed just because a test exists. Preserve UI assertions, activity type and
explicit timing semantics.

Read [testing.md](references/testing.md) for the API and measurement pitfalls.
This reference is bundled; repository-relative examples are optional, not files
assumed to exist in a consuming app.

## Measure an interaction

1. Use the Android tracking rule, or return `runRecompositionTrackingUiTest { … }`
   with `setTrackedContent` on KMP.
2. Verify the tagged node exists and the intended composable is tracked. Settle
   initial content and reset recomposition **counts**, preserving live history.
3. Drive the interaction and assert the visible change. Settle ordinary
   interactions before measurement. For explicit clock or no-implicit-wait
   regressions, preserve their prescribed synchronization; an unconditional
   `waitForIdle()` can erase the condition under test.
4. Choose `exactly`, `atMost`, or a range from the behavior contract.
   `assertStable()` means no recompositions after initial composition. A legitimate
   update can require a nonzero count; counting state sources does not predict it.
5. In accuracy tests, use a plain thread-safe counter (not observable Compose
   state) in unkeyed `SideEffect`, record its settled baseline, and compare its
   delta with DejaVu. Keep keyed callback counts separate. Include a nonzero
   exercise and expected budget failure to catch disabled tracking.

Keep assertions, caught-error message checks and tracer lifetime inside the KMP
helper body; return its result for Wasm. Let helpers restore prior tracing state.
Never add nested rules to work around missing tracking.

## Verify within scope

Run the relevant actual UI test and inspect counts/failures/skips. In this repo,
JVM, iOS and Wasm UI coverage lives in `commonTest`; Android UI coverage is
instrumented, not `:dejavu:testDebugUnitTest`. Runtime changes require the repo's
cross-platform checks. Prose edits don't require a UI matrix.

New Compose APIs belong in `compose-experimental` until the supported floor
includes them; retain older Android BOM coverage. Diagnose mismatches against
the oracle, mapping, lifecycle and fixture contract before choosing a fix.

Report the interaction, expected counts, independent evidence and command/result.
Public examples: [test patterns](https://dejavu.mmckenna.me/0.5.0/examples/).
