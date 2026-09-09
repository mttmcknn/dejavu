# Module dejavu

Implicit recomposition tracking for Compose UI tests.

Dejavu intercepts recomposition events at the runtime level using `CompositionTracer`,
enabling assertions like `assertRecompositions(exactly = 2)` and `assertStable()`
without per-composable setup.

## Getting started

Add Dejavu to your test dependencies. On Android, use `createRecompositionTrackingRule()` and
set the screen with the rule's `setContent`, or use the activity overload to launch your screen.
On JVM, iOS, and Wasm, return `runRecompositionTrackingUiTest` directly from your test and call
`setTrackedContent` inside it. These helpers manage tracking and cleanup automatically.
See the [Getting Started guide](https://dejavu.mmckenna.me/latest/getting-started/) for details.
