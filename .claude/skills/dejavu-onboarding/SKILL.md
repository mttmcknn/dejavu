---
name: dejavu-onboarding
description: Add the Dejavu library to a project from scratch. Use when the user wants to install or set up Dejavu, add the gradle dependency, write their first recomposition test in a project that doesn't have Dejavu yet, or get Dejavu working in an existing Android or Kotlin Multiplatform codebase.
---

# Dejavu Onboarding

Add Dejavu to a project that doesn't have it yet. Covers the gradle
dependency, the test rule setup, the first `Modifier.testTag` placement,
and the smallest possible passing test that proves Dejavu is wired up.

If Dejavu is already set up and the user wants to write more tests, hand off
to **`dejavu-test-writer`**. If they're triaging a failing assertion, use
**`dejavu-error-triage`**. If they want an iterative perf-optimization loop,
use **`dejavu-perf-loop`**.

## Reference docs (read first)

- `docs/getting-started.md` — minimal Android setup (the canonical 2-step
  install).
- `README.md` — "KMP Test Setup" section for non-Android
  targets.
- `README.md` — "Compatibility" section for Compose BOM / Kotlin version
  requirements for the DejaVu version being installed.

## Workflow

### 1. Confirm prerequisites

Verify the project meets Dejavu's compatibility floor before touching anything:

- **Compose version within the chosen DejaVu release's documented range.** The historical
  `CompositionTracer` introduction in 1.2 is not the current library's support floor.
- **Kotlin matching that release's compatibility requirements**, with the Compose compiler plugin.
- **JVM 17+** for Android and Desktop targets.

If any of these are below floor, stop and tell the user — Dejavu can't be
installed without them. Don't try to upgrade Compose/Kotlin as part of
onboarding; that's a separate decision.

### 2. Determine the project shape

Ask (or infer from `build.gradle.kts`):

- **Android-only app** → add Dejavu to the `androidTest` source set.
- **KMP library or app with Compose Multiplatform** → add Dejavu to the
  `commonTest` source set so Desktop / iOS / Wasm tests can use it.
- **Both** → wire it into `androidTest` for instrumented coverage AND
  `commonTest` for KMP coverage; the same Maven artifact serves both.

### 3. Add the gradle dependency

For Android (single-platform):

```kotlin
// app/build.gradle.kts
dependencies {
    androidTestImplementation("me.mmckenna.dejavu:dejavu:0.5.0")
}
```

For Kotlin Multiplatform — pick the form that matches the project's existing
gradle DSL:

```kotlin
// shared/build.gradle.kts (Kotlin 2.0+ accessor DSL)
kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation("me.mmckenna.dejavu:dejavu:0.5.0")
        }
    }
}
```

Older builds (or projects that haven't migrated their gradle scripts) use:

```kotlin
kotlin {
    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation("me.mmckenna.dejavu:dejavu:0.5.0")
            }
        }
    }
}
```

Use the latest version from Maven Central (the README badge has the current
number) compatible with the project's Compose version. DejaVu 0.5.x builds against Compose
Multiplatform 1.12.0 and requires Android compile SDK 37 and min SDK 24. Android Compose 1.11
is supported only with its BOM enforced in both app and instrumentation dependencies.
Use 0.4.0 for Compose Multiplatform 1.11 / compile SDK 36, and 0.3.1 for Compose 1.10.
Follow the complete setup in `docs/getting-started.md`, including the plain rule's debug test
manifest dependency. Don't downgrade a compatible newer release.

### 4. Pick a target composable for the first test

Choose ONE composable that:

- Already exists in the project (don't write code under test as part of
  onboarding).
- Has a clear "should never recompose" intuition (a static title, an icon, a
  label that depends on no state).

This becomes the first `assertStable()` target — the simplest assertion to
prove Dejavu works.

### 5. Add `Modifier.testTag()` to the target

Tags must be on the outermost user-defined composable's modifier. If the
target doesn't have one yet:

```kotlin
@Composable
fun StaticTitle() {
    Text("My App", modifier = Modifier.testTag("static_title"))
}
```

Use snake_case for tag names by convention.

### 6. Write the smallest possible test

For Android (instrumented test source set, e.g.
`app/src/androidTest/java/.../MyFirstDejavuTest.kt`):

```kotlin
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dejavu.assertStable
import dejavu.createRecompositionTrackingRule
import org.junit.Rule
import org.junit.Test

class MyFirstDejavuTest {

    @get:Rule
    val composeTestRule = createRecompositionTrackingRule<MainActivity>()

    @Test
    fun staticTitle_isStable_afterAnyClick() {
        composeTestRule.onNodeWithTag("any_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("static_title").assertStable()
    }
}
```

For KMP (`commonTest`):

```kotlin
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import dejavu.assertStable
import dejavu.runRecompositionTrackingUiTest
import dejavu.setTrackedContent
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MyFirstDejavuTest {
    @Test
    fun staticTitle_isStable() = runRecompositionTrackingUiTest {
        setTrackedContent { StaticTitle() }   // the composable from step 5
        waitForIdle()
        onNodeWithTag("static_title").assertStable()
    }
}
```

### 7. Run the test

| Project shape | Command |
|---|---|
| Android instrumented | `./gradlew :app:connectedDebugAndroidTest` |
| KMP Desktop JVM | `./gradlew :shared:jvmTest` |
| KMP iOS simulator | `./gradlew :shared:iosSimulatorArm64Test` |
| KMP Wasm browser | `./gradlew :shared:wasmJsBrowserTest` |

Substitute the actual module name. If the test passes, Dejavu is wired up
correctly.

### 8. Optional: enable runtime logcat streaming (Android only)

Useful if the user wants AI agents to follow recomposition events in real
time outside of tests. Add to a debug `Application`:

```kotlin
Dejavu.enable(app = this, logToLogcat = true)
```

Filter logcat with tag `Dejavu`. See `docs/use-cases.md` "Stream Composition State to AI Agents via Logcat" for the format.

## Common gotchas

- **Test fails with "node has no testTag"** — the tag wasn't applied; check
  the modifier is on the outermost composable.
- **Test fails with `Actual: composable was never composed`** — the target
  isn't on screen during the test; for lazy items, scroll into view first.
- **Compose compiler plugin missing** — `createRecompositionTrackingRule`
  works without it, but tag-to-function mapping degrades. Add the Compose
  compiler plugin if it isn't already configured.
- **Wasm test completion** — return `runRecompositionTrackingUiTest` directly from the test.
  Put diagnostic assertions inside its body so the runner awaits their asynchronous result.
  DejaVu 0.4.0 restores lazy-grid and diagnostic-message coverage on iOS/Wasm.

## Wrap-up

Once the first test passes:

1. Tell the user Dejavu is wired up and the smallest assertion runs.
2. Hand off to **`dejavu-test-writer`** for the next test (real assertions
   on real interactions).
3. Point at `docs/getting-started.md` and `docs/examples.md` for further
   patterns the project may want to adopt.
4. Don't add more tests automatically — the onboarding job is done.
