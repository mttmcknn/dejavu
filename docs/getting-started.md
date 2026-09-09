# Getting Started

## 1. Add dependency

```kotlin
// app/build.gradle.kts
dependencies {
    androidTestImplementation("me.mmckenna.dejavu:dejavu:0.5.0")
}
```

## 2. Write a test

```kotlin
import dejavu.assertRecompositions
import dejavu.assertStable
import dejavu.createRecompositionTrackingRule

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

`createRecompositionTrackingRule` wraps `createAndroidComposeRule` and resets counts before each test. For `createComposeRule()` or other rule types, see [Examples](examples.md).

To reset counts mid-test — after initial composition but before the interaction you want to measure — call `composeTestRule.resetRecompositionCounts()`. On non-Android targets (JVM, iOS, WasmJs), the same helper is available as `resetRecompositionCounts()` inside `runRecompositionTrackingUiTest`.

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
/plugin marketplace add himattm/dejavu
/plugin install dejavu@dejavu
```

Sessions opened inside the [Dejavu repo](https://github.com/himattm/dejavu) auto-load the same skills from `.claude/skills/` without installing the plugin. See [Use Cases → Give AI Agents a Recomposition Signal](use-cases.md#give-ai-agents-a-recomposition-signal) for what the skills do in practice.
