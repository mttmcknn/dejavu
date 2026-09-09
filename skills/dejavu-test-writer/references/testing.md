# DejaVu test API and accuracy contract

## Public API

Android uses `createRecompositionTrackingRule()` or
`createRecompositionTrackingRule<Activity>()` with JUnit4 `@get:Rule`. Preserve
content ownership and activity lifecycle when replacing an existing rule. Do not
assume clock control requires changing the test host.

KMP tests use `@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)` and:

```kotlin
@Test
fun recompositionContract() = runRecompositionTrackingUiTest {
    setTrackedContent { /* target content */ }
    waitForIdle()
    resetRecompositionCounts()
    // Drive the interaction and assert its visible result.
    waitForIdle()
    onNodeWithTag("target").assertRecompositions(exactly = 1)
}
```

Import the helpers and assertions from `dejavu`, `onNodeWithTag` from Compose UI
test, and `Test` from `kotlin.test`. Return the helper result and keep all
assertions inside the body, including captured-error message checks.

```kotlin
onNodeWithTag(tag).assertStable() // exactly zero after initial composition
onNodeWithTag(tag).assertRecompositions(exactly = n)
onNodeWithTag(tag).assertRecompositions(atMost = n)
onNodeWithTag(tag).assertRecompositions(atLeast = n, atMost = m)
```

Bounds are nonnegative; `exactly` cannot combine with range bounds and a lower
bound cannot exceed the upper bound. Android rules and KMP helpers expose
`resetRecompositionCounts()`; this preserves composition history. A full tracer
`reset()` during a live composition changes what counts as initial composition.
Android rules also expose `getRecompositionCount(tag)`.

## Independent measurement

An unkeyed `SideEffect { counter.incrementAndGet() }` records committed executions
of its composable. Use a thread-safe, non-snapshot counter. After initial content
settles, record the counter baseline and reset DejaVu's counts. After an interaction
settles, compare the counter delta with the tracked recompositions. Subtracting
one is only valid when exactly one initial execution was established.

Keyed `SideEffect(key)` can skip its callback despite a recomposition; it is not
an oracle. Track keyed callbacks separately when testing Compose 1.12 behavior.
A deliberately inefficient fixture can correctly have four recompositions and
zero keyed callbacks. Keep both the expected count and a caught too-tight budget
failure; zero/zero agreement alone cannot prove a working tracer.

## Mapping and lifecycle

Tag the relevant user-composable boundary. Check node existence and mapping;
scroll lazy items into view. Multi-instance fallback counts may share a function
count, so avoid overclaiming instance accuracy without its independent counter.
Compiler source information must be present.

Helpers own enable/reset/restore/cleanup. A rule and helper used sequentially in
one process must restore prior tracing state. Adding a second enclosing rule can
produce a false zero or nested test-environment error.

No-implicit-wait and explicit-clock tests must retain their timing contract.
Compose 1.12-only APIs stay in `compose-experimental` until the supported runtime
floor includes them. Do not copy old Wasm skips into supported diagnostic tests.
