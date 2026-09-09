---
name: dejavu-test-writer
description: Author Compose UI recomposition tests using the Dejavu library. Use when adding a new test for a Compose composable, retrofitting recomposition assertions onto an existing UI test, deciding between Android instrumented vs KMP common tests, or when the user mentions Dejavu, recomposition counts, assertRecompositions, assertStable, or createRecompositionTrackingRule.
---

# Dejavu Test Writer

Dejavu turns Compose recomposition behavior into JUnit/kotlin.test assertions. This
skill picks the right setup, points at canonical examples, and gives you the API
surface in one screen so you can write a correct test on the first try.

This skill assumes Dejavu is already installed in the project. If it isn't,
use **`dejavu-onboarding`** first to add the gradle dependency and prove the
setup with a smallest-possible test, then come back here.

This skill teaches you how to **write a test**. Companion skills:
- **`dejavu-error-triage`** — diagnose a single failing assertion.
- **`dejavu-perf-loop`** — iteratively reduce a composable's recomposition
  count. If a count exceeds your assertion, do **not** loosen it — escalate.

## Reference docs (read first)

Read these before generating any test code:

- `docs/getting-started.md` — minimal Android setup
- `docs/examples.md` — eight canonical patterns, including LazyColumn,
  AnimatedVisibility, deep nesting, CompositionLocal, and `derivedStateOf`
- `docs/error-messages.md` — failure output anatomy (used by the perf-loop skill)
- `README.md` — KMP setup section + Known Limitations

## Canonical examples to copy from

Inside DejaVu's own repository, many samples deliberately recompose too often. Preserve their
inefficient behavior when validating the library: compare the tracer with independent `SideEffect`
counters and test expected budget failures. Optimize a fixture only when that change is part of
the user's request; the library's accuracy suite is not an app performance cleanup task.

| Style | File |
|---|---|
| Android JUnit4 | `demo/src/androidTest/java/demo/app/AssertionApiTest.kt` |
| KMP common test | `dejavu/src/commonTest/kotlin/dejavu/AssertionApiPatternTest.kt` |
| Smallest KMP example | `dejavu/src/commonTest/kotlin/dejavu/DejavuComposeUiTest.kt` |
| Tagged composable | `demo-shared/src/commonMain/kotlin/demo/app/ui/Counter.kt` |
| Optimization patterns (more) | `dejavu/src/commonTest/kotlin/dejavu/*PatternTest.kt` |

## API at a glance

### Android (JUnit4) setup

```kotlin
import dejavu.assertRecompositions
import dejavu.assertStable
import dejavu.createRecompositionTrackingRule

@get:Rule
val composeTestRule = createRecompositionTrackingRule<MyActivity>()
// or createRecompositionTrackingRule() for plain ComponentActivity host
```

`createRecompositionTrackingRule` wraps `createAndroidComposeRule`, enables Dejavu
before each test, resets counts, and disables on teardown. Drop-in replacement.

### KMP (commonTest / Desktop / iOS / Wasm) setup

```kotlin
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import dejavu.assertStable
import dejavu.runRecompositionTrackingUiTest
import dejavu.setTrackedContent
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MyTest {
    @Test
    fun myComposable_isStable() = runRecompositionTrackingUiTest {
        setTrackedContent { MyComposable() }
        waitForIdle()
        onNodeWithTag("my_tag").assertStable()
    }
}
```

`runRecompositionTrackingUiTest` is the KMP equivalent of the Android rule. It
handles tracer setup, inspection-table seeding, state reset, and teardown.
`setTrackedContent` is the tracked equivalent of `setContent` — required for tag
mapping on non-Android platforms.

### Assertions

```kotlin
onNodeWithTag(tag).assertRecompositions(exactly = N)
onNodeWithTag(tag).assertRecompositions(atLeast = N)
onNodeWithTag(tag).assertRecompositions(atMost = N)
onNodeWithTag(tag).assertRecompositions(atLeast = N, atMost = M)
onNodeWithTag(tag).assertStable()                         // alias for exactly = 0
```

Validation rules (`SemanticNodeInteractions.kt:33-50`): exactly one of `exactly` or
`atLeast`/`atMost`; `exactly` cannot combine with the range params; all bounds must
be `>= 0`; `atLeast <= atMost`. Violations throw `IllegalArgumentException`.

### Mid-test reset (Android)

```kotlin
composeTestRule.resetRecompositionCounts()
```

Clears recomposition counts but preserves composition history. Use between setup
and the actual interaction under test (`docs/examples.md` Tips section).

### Programmatic read (Android only)

```kotlin
val n: Int = composeTestRule.getRecompositionCount("my_tag")
```

## First: are there existing Compose UI tests for this composable?

Before writing anything new, search the project's test source sets for tests
that already exercise the target composable:

- Grep recursively in the test source sets for `onNodeWithTag(`, e.g.
  `grep -rEn 'onNodeWithTag\(' src/test src/androidTest src/commonTest 2>/dev/null`.
  Substitute the project's actual test directory layout. Narrow with the
  specific tag name (`'onNodeWithTag\("my_tag"\)'`) once you have one.
- Look for a sibling test file (`MyScreen.kt` → `MyScreenTest.kt` /
  `MyScreenInstrumentedTest.kt`).
- Skim for tests that drive the same interaction you want to assert on
  (`performClick`, `performTextInput`, etc.).

**If matches exist, ask the user which path to take BEFORE changing code:**

> I found existing Compose UI tests that cover `<Composable>` in
> `<TestFile>.kt`. Do you want to:
> 1. **Augment** them — swap the rule for the Dejavu equivalent and add
>    `assertStable()` / `assertRecompositions(...)` calls inside the existing
>    test methods. Co-locates behavior + recomposition checks. No new files.
> 2. **Add new tests** — keep the existing tests untouched and create a
>    parallel `<Composable>RecompositionTest.kt`. Cleaner separation; some
>    setup duplication.
>
> Which would you prefer?

If no matches exist, default to a new test and continue with the workflow
below.

### Augmenting an existing test (if the user picks path 1)

Three changes; the rest of the workflow's tagging / assertion / `waitForIdle`
/ reset / run steps still apply.

1. **Swap the rule.** Branch on which factory the existing test uses — the
   wrong swap won't compile or will silently change the test host.

   | Existing rule | Swap to | Notes |
   |---|---|---|
   | `createAndroidComposeRule()` / `createAndroidComposeRule<A>()` | `createRecompositionTrackingRule()` / `createRecompositionTrackingRule<A>()` | Drop-in. Same Activity type. |
   | `createComposeRule()` (no Activity — used for `mainClock.advanceTimeBy()` or pure-Compose modules) | **No public drop-in.** Either (a) migrate the test body to `runRecompositionTrackingUiTest { setTrackedContent { … } }` (loses the Activity; gains tracer setup), or (b) keep the rule and add `Dejavu.enable(app)` in `@Before` and `Dejavu.disable()` in `@After` manually. | Don't blindly swap to `createRecompositionTrackingRule()` — it requires an Activity. |
   | `runComposeUiTest { … }` (KMP `commonTest`) | `runRecompositionTrackingUiTest { … }`, plus inside, `setContent { … }` → `setTrackedContent { … }` | Drop-in. |

2. **Add `Modifier.testTag(...)`** to any composable you want to assert on
   that doesn't already have one. Outermost user modifier; snake_case tags.
3. **Add Dejavu assertions** alongside the existing ones (don't replace
   them). For multi-phase tests, call
   `composeTestRule.resetRecompositionCounts()` between the setup actions
   and the measured interaction.

Don't add `assertStable()` / `assertRecompositions(...)` to every node — pick
the ones that have a clear "should be stable" or "should recompose exactly N"
intuition for the interaction under test, plus at least one stable-sibling
guard (see the wrap-up).

## Workflow

The numbered steps below cover writing a NEW test. If the user picked
augmentation above, the existing test's location and module are already
settled — skip step 1, skip step 2 for any composable that already has a
`testTag`, and start at step 3 (assertion mode) for the new assertions.
Steps 4 (`waitForIdle`), 5 (mid-test reset), and 6 (run) apply to both paths.

### 1. Decide where the test lives

- **Android-only feature, needs an Activity / hardware** →
  `demo/src/androidTest/...` (or your project's instrumented test source set)
- **Cross-platform composable** → `dejavu/src/commonTest/...` (or your KMP
  module's `commonTest`)
- See `CLAUDE.md` "Project Structure" for module roles.

### 2. Tag every assertion target

Add `Modifier.testTag("my_tag")` to the composable you want to assert on. The tag
must be on a user-defined composable's modifier — framework-internal nodes cannot
be mapped (`docs/error-messages.md` Pattern 3). Convention: snake_case strings.

### 3. Pick the assertion mode

- `assertStable()` — "this should never recompose" (clearest intent)
- `assertRecompositions(exactly = N)` — known count, regression guard
- `assertRecompositions(atMost = N)` — recomposition budget; tighten this over
  time using the perf-loop skill
- `assertRecompositions(atLeast = N)` — sanity check that an interaction did
  trigger recomposition

### 4. Drive UI → waitForIdle → assert

```kotlin
composeTestRule.onNodeWithTag("inc_button").performClick()
composeTestRule.waitForIdle()                              // ALWAYS
composeTestRule.onNodeWithTag("counter_value").assertRecompositions(exactly = 1)
```

Skip `waitForIdle()` and counts become racy. Same applies to `performTextInput`
and any other interaction.

### 5. Reset between phases of multi-step tests

When the test has setup actions plus a measured interaction, reset between them
so the assertion only counts the interaction under test:

```kotlin
// Setup phase: get the UI into the right state, then forget the counts.
composeTestRule.onNodeWithTag("setup_action").performClick()
composeTestRule.waitForIdle()
composeTestRule.resetRecompositionCounts()

// Measurement phase: only this interaction's recompositions are counted.
composeTestRule.onNodeWithTag("measured_action").performClick()
composeTestRule.waitForIdle()
composeTestRule.onNodeWithTag("target").assertStable()
```

### 6. Run the test

Substitute `<module>` with the gradle module that holds the test (`:app:`,
`:shared:`, `:feature-foo:`, etc.). In the Dejavu repo itself, that's
`:dejavu:` for KMP common tests and `:demo:` for Android instrumented tests
(`CLAUDE.md` "Gradle"; pass `-q --console=plain` by convention there).

```bash
./gradlew :<module>:jvmTest                   # Desktop JVM (KMP common)
./gradlew :<module>:testDebugUnitTest          # Android unit tests (Robolectric)
./gradlew :<module>:iosSimulatorArm64Test      # iOS sim (KMP common)
./gradlew :<module>:wasmJsBrowserTest          # Wasm headless browser
./gradlew :<module>:connectedDebugAndroidTest  # Android instrumented
./gradlew apiCheck                             # API compat
```

## Common gotchas

- **Off-screen lazy items** — scroll into view before asserting; off-screen items have no composition (`README.md` Known Limitations).
- **`exactly` + range mutual exclusion** — `exactly` cannot combine with `atLeast`/`atMost`; throws `IllegalArgumentException`.
- **Negative bounds** — all bounds must be `>= 0`.
- **`assertStable()` vs `exactly = 0`** — identical; prefer `assertStable()` for intent.
- **Wasm asynchronous tests** — return the `TestResult` from `runRecompositionTrackingUiTest`
  directly. Inspect caught assertion messages inside the test body. From DejaVu 0.4.0 onward,
  diagnostic-message and lazy-grid tests run on Wasm; do not copy the old skip guards.
- **`testTag` placement** — put the tag on the outermost user-defined composable's modifier, not buried inside framework primitives.
- **Activity-owned clock** — `createAndroidComposeRule` uses the real Recomposer; use plain `createComposeRule()` for clock control.

## Wrap-up

After writing or editing a test:

1. Run the matching gradle command above and fix failures.
2. **If the count exceeds your assertion, do NOT loosen it** — invoke the
   `dejavu-perf-loop` skill to diagnose and fix the underlying recomposition
   cause, then come back with a tightened assertion.
3. Leave at least one `assertStable()` on a sibling composable that should not
   be affected — it locks in the scoping guarantee and catches setup mistakes.
