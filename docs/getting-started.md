# Getting Started

## Choose a compatible release

The latest stable release is **0.5.0**, built against Compose Multiplatform **1.12.0** and
Android BOM **2026.08.00**. Android consumers need **compile SDK 37** and **min SDK 24**.
The release was validated with Kotlin 2.4.0 and its Compose compiler plugin.

Android Compose 1.11 remains supported when you enforce its BOM in both application and
instrumentation dependencies. Use 0.4.0 for the Compose Multiplatform 1.11 / compile SDK 36
baseline, or 0.3.1 for Compose 1.10. See [versions and migration](releases/index.md).

## Android setup

Add the test dependency to your app module. The plain rule also needs Compose's test activity
manifest in the debug variant:

```kotlin
// app/build.gradle.kts
android {
    compileSdk = 37
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

composeCompiler { includeSourceInformation = true }

dependencies {
    val composeBom = enforcedPlatform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    androidTestImplementation("me.mmckenna.dejavu:dejavu:0.5.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

Compose test APIs are compile-only dependencies of DejaVu, so include them explicitly in your
test module. The Espresso version above matches the API 37 validation environment.

To retain Android Compose 1.11, select the validated BOM `2026.05.00` or `2026.06.01` in
that same enforced-platform declaration. Keep compile SDK 37 for the 0.5.0 Android artifact.

This complete test sets content, resets the budget, and then measures one state change:

```kotlin
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import dejavu.assertRecompositions
import dejavu.createRecompositionTrackingRule
import dejavu.resetRecompositionCounts
import org.junit.Rule
import org.junit.Test

class CounterRecompositionTest {
    @get:Rule
    val composeTestRule = createRecompositionTrackingRule()

    @Test
    fun counterRecomposesOnce() {
        val count = mutableIntStateOf(0)
        composeTestRule.setContent { CounterValue(count.intValue) }
        composeTestRule.waitForIdle()
        composeTestRule.resetRecompositionCounts()

        composeTestRule.runOnIdle { count.intValue++ }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("counter_value")
            .assertRecompositions(exactly = 1)
    }
}

@Composable
private fun CounterValue(value: Int) {
    BasicText("Value: $value", modifier = Modifier.testTag("counter_value"))
}
```

For a screen owned by your app activity, use `createRecompositionTrackingRule<YourActivity>()`.
The rule launches that activity and manages Dejavu's lifecycle. See [Examples](examples.md) for
larger scenarios and `assertStable()` expectations.

## Kotlin Multiplatform setup

Add the dependency to the shared test source set for JVM desktop, iOS arm64 and simulator arm64,
and WasmJs. Use the Compose Multiplatform 1.12.0 baseline for 0.5.0:

```kotlin
kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("me.mmckenna.dejavu:dejavu:0.5.0")
            implementation(compose.foundation)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}
```

Keep `composeCompiler { includeSourceInformation = true }` enabled in the shared module.
For desktop tests, also add `implementation(compose.desktop.currentOs)` to `jvmTest.dependencies`.
The Compose Multiplatform plugin supplies the `compose` dependency accessors above.

Return the helper's result directly so the Wasm test runner waits for completion:

```kotlin
import androidx.compose.ui.test.onNodeWithTag
import dejavu.assertStable
import dejavu.runRecompositionTrackingUiTest
import dejavu.setTrackedContent
import kotlin.test.Test

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class CounterMultiplatformTest {
    @Test
    fun counterStartsStable() = runRecompositionTrackingUiTest {
        setTrackedContent { CounterValue(0) }
        waitForIdle()
        onNodeWithTag("counter_value").assertStable()
    }
}
```

This uses the `CounterValue` composable above. Keep assertions inside the suspending helper body.
Call `resetRecompositionCounts()` after settling initial content and before the interaction whose
budget you want to measure. The helper manages tracking and cleanup automatically.

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

See the [Error Messages Guide](error-messages.md) for how to read and act on each section.

## Optional: Install the Claude Code skills

If you use Claude Code, you can install the bundled Dejavu skills globally so they're available in any project. The skill set covers the full lifecycle: `dejavu-onboarding` (initial install), `dejavu-test-writer` (author tests), `dejavu-error-triage` (diagnose a single failure), and `dejavu-perf-loop` (iteratively optimize a composable's recomposition behavior using Dejavu as the validator).

```
/plugin marketplace add mttmcknn/dejavu
/plugin install dejavu@dejavu
```

Sessions opened inside the [Dejavu repo](https://github.com/mttmcknn/dejavu) auto-load the same skills from `.claude/skills/` without installing the plugin. See [Use Cases → Give AI Agents a Recomposition Signal](use-cases.md#give-ai-agents-a-recomposition-signal) for what the skills do in practice.
