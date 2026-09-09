<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/logo-dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="docs/assets/favicon.svg">
  <img src="docs/assets/favicon.svg" width="40" align="left" style="margin-right: 12px;">
</picture>

# Dejavu

*Wait... didn't we just compose this?*

[![CI](https://github.com/himattm/dejavu/actions/workflows/ci.yml/badge.svg)](https://github.com/himattm/dejavu/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/me.mmckenna.dejavu/dejavu)](https://central.sonatype.com/artifact/me.mmckenna.dejavu/dejavu)
[![Compose](https://img.shields.io/badge/Compose-1.11–1.12-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/develop/ui/compose)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

###### Featured In
<a href="https://jetc.dev/issues/305.html"><img src="https://img.shields.io/badge/As_Seen_In-jetc.dev_Newsletter_Issue_%23305-blue?logo=Jetpack+Compose&amp;logoColor=white" alt="As Seen In - jetc.dev Newsletter Issue #305"></a>

**[Full Documentation](https://dejavu.mmckenna.me)**

**Guard your Compose UI efficiency. Catch recomposition regressions before your users.**

## The Problem

Compose's recomposition behavior is an implicit contract — composables should recompose when their inputs change and stay stable otherwise. But that contract breaks silently, and today's options for catching it are limited:

- **Layout Inspector** — manual, requires a running app, can't automate, can't run in CI
- **Manual tracking code** — `SideEffect` counters, `LaunchedEffect` logging, wrapper composables; invasive, doesn't scale, and ships in your production code
- Neither gives you a **testable, automatable contract** you can enforce on every PR

## What Dejavu Does

Dejavu is a test-only library that turns recomposition behavior into assertions. Tag your composables with standard `Modifier.testTag()`, write expectations against recomposition counts, and get structured diagnostics when something changes — whether from a teammate, a library upgrade, an AI agent rewriting your UI code, or a refactor that silently destabilizes a lambda.

- **Zero production code changes** — just `Modifier.testTag()`
- **One-line test setup** — `createRecompositionTrackingRule()`
- **Rich diagnostics** — source location, recomposition timeline, parameter diffs, causality analysis
- **Per-instance tracking** — multiple instances of the same composable get independent counters

## Quick Start

### 1. Add dependency

```kotlin
// app/build.gradle.kts
dependencies {
    androidTestImplementation("me.mmckenna.dejavu:dejavu:0.5.0")
}
```

### 2. Write a test

```kotlin
@get:Rule
val composeTestRule = createRecompositionTrackingRule()

@Test
fun incrementCounter_onlyValueRecomposes() {
  composeTestRule.onNodeWithTag("inc_button")
    .performClick()
    
  composeTestRule.onNodeWithTag("counter_value")
    .assertRecompositions(exactly = 1)
    
  composeTestRule.onNodeWithTag("counter_title")
    .assertStable() // stable = zero recompositions
}
```

`createRecompositionTrackingRule` wraps `createAndroidComposeRule` and resets counts before each test. For `createComposeRule()` or other rule types, see [Examples](https://dejavu.mmckenna.me/examples/).

## What a Failure Looks Like

```
dejavu.UnexpectedRecompositionsError: Recomposition assertion failed for testTag='product_header'
  Composable: demo.app.ui.ProductHeader (ProductList.kt:29)
  Expected: exactly 0 recomposition(s)
  Actual: 1 recomposition(s)

  All tracked composables:
    ProductListScreen = 1
    ProductHeader    = 1  <-- FAILED
    ProductItem      = 1

  Recomposition timeline:
    #1 at +0ms — param slots changed: [1] | parent: ProductListScreen

  Possible cause:
    1 state change(s) of type Int
    Parameter/parent change detected (dirty bits set)
```

See [Error Messages Guide](https://dejavu.mmckenna.me/error-messages/) for how to read and act on each section.

## Claude Code Skills (for AI Agents)

Dejavu ships four Claude Code skills that teach AI agents how to use it:

- **`dejavu-onboarding`** — add Dejavu to a project from scratch (gradle dependency, first `Modifier.testTag`, smallest possible passing test).
- **`dejavu-test-writer`** — author Compose UI recomposition tests using Dejavu's APIs (Android JUnit4 or KMP `commonTest`).
- **`dejavu-error-triage`** — diagnose a single failing `UnexpectedRecompositionsError`: walks the error sections, names the pattern, points at the canonical fix.
- **`dejavu-perf-loop`** — closed-loop optimization of a composable's recomposition behavior, using Dejavu as the validator. Embeds an error-pattern → fix decision tree (data class, Boolean narrowing, `derivedStateOf`, hoisted reads, `key()`, `CompositionLocal`).

Install them globally in Claude Code so they're available in any project:

```
/plugin marketplace add himattm/dejavu
/plugin install dejavu@dejavu
```

Sessions opened inside this repo also auto-load the same skills from [`.claude/skills/`](.claude/skills/) without installing the plugin.

## Use Cases

### Lock In Efficiency Gains

When you optimize a composable — extracting a lambda, adding `remember`, switching to `derivedStateOf` — Dejavu lets you write a test that captures the expected recomposition count. That improvement becomes part of your test suite: refactors, dependency upgrades, and new features all have to maintain it or explicitly update the expectation.

### Give AI Agents a Recomposition Signal

AI coding agents can refactor composables and restructure state, but they have no way to know whether their changes made recomposition better or worse. Dejavu gives them that signal. When an agent runs your tests and a Dejavu assertion fails, the structured error message tells it exactly which composable regressed, by how much, and why — turning recomposition count into an optimization metric the agent can target directly.

For Claude Code users, the bundled `dejavu-test-writer` and `dejavu-perf-loop` skills (see [Claude Code Skills](#claude-code-skills-for-ai-agents) above) teach the agent how to author Dejavu tests and run an iterative perf-optimization loop without re-deriving the API from docs.

### Guardrail Against Unexpected Changes

When AI agents or automated tooling modify your codebase, they can introduce subtle changes to recomposition behavior without touching any visible UI. Dejavu tests act as guardrails — if an agent's changes cause a composable to recompose more than expected, the test fails before the change is merged. You get the speed of automated refactoring with the confidence that recomposition behavior is preserved.

See the full [Use Cases](https://dejavu.mmckenna.me/use-cases/) guide for examples.

## API Reference

### Assertions

```kotlin
// Exact count
composeTestRule.onNodeWithTag("tag").assertRecompositions(exactly = 2)

// Bounds
composeTestRule.onNodeWithTag("tag")
  .assertRecompositions(atLeast = 1)

composeTestRule.onNodeWithTag("tag")
  .assertRecompositions(atMost = 3)

composeTestRule.onNodeWithTag("tag")
  .assertRecompositions(atLeast = 1, atMost = 5)

// Stability (alias for exactly = 0)
composeTestRule.onNodeWithTag("tag")
  .assertStable()
```

### Utilities

```kotlin
// Reset all counts to zero mid-test (Android)
composeTestRule.resetRecompositionCounts()

// Reset all counts to zero mid-test (KMP: JVM, iOS, WasmJs)
// Resets recomposition counts while preserving composition history.
// Use inside runRecompositionTrackingUiTest after initial composition,
// before the interaction whose budget is being asserted.
resetRecompositionCounts()

// Get the current recomposition count for a tag (Android)
val count: Int = composeTestRule.getRecompositionCount("tag")

// Stream recomposition events to Logcat (filter: "Dejavu")
// Useful for AI agents or external tools monitoring UI state
Dejavu.enable(app = this, logToLogcat = true)

// Disable tracking and clear all data
Dejavu.disable()
```

## How It Works

Dejavu hooks into the Compose runtime's `CompositionTracer` API (available since compose-runtime 1.2.0):

1. **Intercepts trace calls** — `Composer.setTracer()` receives callbacks for every composable enter/exit
2. **Maps testTag to composable** — walks the `CompositionData` group tree to find which composable encloses each `Modifier.testTag()`
3. **Counts recompositions** — maintains a thread-safe counter per composable, incrementing on recomposition (not initial composition)
4. **Tracks causality** — `Snapshot.registerApplyObserver` detects state changes; dirty bits detect parameter-driven recompositions
5. **Reports on failure** — assembles source location, timeline, tracked composables, and causality into a structured error

All tracking runs in the app process on the main thread, directly accessible to instrumented tests.

## Compatibility

Supported Compose range for Dejavu 0.5.x: **1.11.x–1.12.x (BOM 2026.05.00 through 2026.08.00)**.

**Minimum supported Compose: 1.11 (BOM 2026.05.00).** Dejavu 0.5.x uses the Compose testing v2 APIs introduced with this line. For Compose 1.10, use Dejavu 0.3.1; that maintenance release preserves the older Compose line instead of allowing newer transitive artifacts to mask an unsupported combination. Validated with Kotlin 2.4.0 and its Compose compiler plugin.

Dejavu 0.5.x is built and released against **Compose Multiplatform 1.12.0**. Android consumers
can use any validated BOM in the support window; they do not have to match the release BOM exactly.

| Compose BOM | Compose | Kotlin tested | Status |
|---|---|---|---|
| 2026.05.00 | 1.11.x | 2.4.0 | Minimum |
| 2026.06.01 | 1.11.x | 2.4.0 | Latest 1.11 checkpoint |
| 2026.08.00 | 1.12.x | 2.4.0 | Release baseline |

## Kotlin Multiplatform

Dejavu supports Kotlin Multiplatform with the following targets:

| Target | Status | Notes |
|--------|--------|-------|
| Android | Full support | Tag mapping via `ui-tooling-data` Group tree |
| Desktop (JVM) | Full support | Tag mapping via `CompositionGroup` + `sourceInfo` |
| iOS (arm64, simulatorArm64) | Supported | Same as JVM; Compose Multiplatform 1.11 no longer supports `iosX64` |
| WasmJs (browser) | Supported | Async result and diagnostic-message regressions verified |

### KMP Test Setup

For non-Android platforms, use `runRecompositionTrackingUiTest` with `setTrackedContent`:

```kotlin
@Test
fun myComposable_isStable() = runRecompositionTrackingUiTest {
    setTrackedContent { MyComposable() }
    waitForIdle()
    onNodeWithTag("my_tag").assertStable()
}
```

`runRecompositionTrackingUiTest` is the KMP equivalent of Android's `createRecompositionTrackingRule()`.
Return its result directly from your test so the Wasm runner waits for completion. The test body
can suspend; Dejavu keeps tracking enabled until the body finishes and then cleans up.
It handles all Dejavu lifecycle management automatically -- enabling the tracer and resetting state. `setTrackedContent` wraps `setContent` with the inspection tables
and sub-composition layout required for tag-to-function mapping.

### New Compose API Coverage

Dejavu is validated against Compose 1.11's new composables and runtime paths via the
`compose-experimental` module — a staging area for recomposition coverage of experimental /
newest-Compose APIs before they graduate into the core accuracy suite. It exercises recomposition
tracking on JVM, iOS, Wasm, and Android instrumented; Android runs every supported BOM for:

- the experimental non-lazy `Grid` and `FlexBox` layouts,
- `derivedMediaQuery` / `mediaQuery` adaptive breakpoints,
- the Styles API (`androidx.compose.foundation.style`),
- `movableContentOf`, and
- the experimental LinkBuffer composer runtime path (`ComposeRuntimeFlags.isLinkBufferComposerEnabled`).

### Compose 1.12 Coverage

The Compose 1.12 baseline additionally validates keyed `SideEffect`, shrinking vararg effect and
`remember` keys, assertions using `runWithoutImplicitWait`, and nested movable content under
LinkBuffer. Exact counters continue using unkeyed `SideEffect`, including a deliberately inefficient
fixture whose keyed callback stays quiet during four recompositions. The experimental suite runs
26 tests on 1.12 and retains 20 on the supported Android 1.11 checkpoints.

`DejavuComposeTestRule` delegates the new `hasPendingWork` and `runWithoutImplicitWait` methods on
Compose 1.12. These methods require 1.12; the existing rule API remains covered on Android 1.11.

## Known Limitations

- **Off-screen lazy items** — `LazyColumn`/`LazyRow` only compose items that are visible. Items that haven't been composed don't exist in the composition tree, so Dejavu has nothing to track. Scroll them into view before asserting.
- **Activity-owned Recomposer clock** — `createAndroidComposeRule` uses the Activity's real `Recomposer`, not a test-controlled one. This means `mainClock.advanceTimeBy()` can't drive infinite animations forward. Use `createComposeRule` (without an Activity) if you need a controllable clock.
- **Parameter change tracking precision** — parameter diffs use `Group.parameters` from the Compose tooling data API, which was designed for Layout Inspector rather than programmatic diffing. Parameter names may be unavailable, and values are compared via `hashCode`/`toString`, so custom types without meaningful `toString` show opaque values.

## Further Reading

- [Use Cases](https://dejavu.mmckenna.me/use-cases/) — locking in UI efficiency, AI agent guardrails, and CI enforcement
- [Examples](https://dejavu.mmckenna.me/examples/) — test patterns for common scenarios
- [Error Messages Guide](https://dejavu.mmckenna.me/error-messages/) — how to read and act on failure output
- [Causality Analysis](https://dejavu.mmckenna.me/causality-analysis/) — understanding why composables recompose

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for our community standards.

## License

Apache 2.0
