# Use Cases

Dejavu turns recomposition behavior into a testable contract. Here are the key scenarios where that matters most.

## Lock In Efficiency Gains

When you optimize a composable — extracting a lambda, adding `remember`, switching to `derivedStateOf` — you get a measurable improvement: fewer recompositions. But without a test to enforce it, that improvement can silently regress on the next PR.

Dejavu lets you write a test that captures the expected recomposition count and fails if it changes:

```kotlin
@Test
fun stableProductList_recompositionCountIsLocked() {
    composeTestRule.onNodeWithTag("inc_button").performClick()

    // After optimization, only the counter value recomposes.
    // This test locks that in — any regression will fail CI.
    composeTestRule.onNodeWithTag("counter_value").assertRecompositions(exactly = 1)
    composeTestRule.onNodeWithTag("counter_title").assertStable()
    composeTestRule.onNodeWithTag("counter_layout").assertStable()
}
```

Now the efficiency improvement is part of your test suite. Refactors, dependency upgrades, and new features all have to maintain it or explicitly update the expectation.

## Give AI Agents a Recomposition Signal

AI coding agents can refactor composables, extract components, and restructure state — but they have no way to know whether their changes made recomposition better or worse. Dejavu gives them that signal.

When an agent runs your test suite and a Dejavu assertion fails, the error message tells it exactly what happened:

```
dejavu.UnexpectedRecompositionsError: Recomposition assertion failed for testTag='product_header'
  Composable: demo.app.ui.ProductHeader (ProductList.kt:29)
  Expected: exactly 0 recomposition(s)
  Actual: 3 recomposition(s)

  Possible cause:
    Parameter/parent change detected (dirty bits set)
```

The agent gets a clear, structured signal: which composable regressed, by how much, and why. It can use this to iterate — adjusting its approach until the assertion passes. This turns recomposition count into an optimization metric that an AI agent can target directly, rather than relying on vague heuristics about Compose best practices.

### Claude Code skills

For Claude Code users, Dejavu ships four skills as a Claude Code plugin so the agent knows how to adopt Dejavu, write tests, triage failures, and run an iterative perf-optimization loop without re-deriving the API from docs:

- **`dejavu-onboarding`** — add Dejavu to a project from scratch (gradle dependency, first `Modifier.testTag`, smallest possible passing test).
- **`dejavu-test-writer`** — author Compose UI recomposition tests using Dejavu's APIs (Android JUnit4 or KMP `commonTest`).
- **`dejavu-error-triage`** — diagnose a single failing `UnexpectedRecompositionsError`: walks the error sections, names the pattern, points at the canonical fix.
- **`dejavu-perf-loop`** — closed-loop optimization of a composable's recomposition behavior, using Dejavu as the validator. Embeds an error-pattern → fix decision tree (data class, Boolean narrowing, `derivedStateOf`, hoisted reads, `key()`, `CompositionLocal`).

Install:

```
/plugin marketplace add mttmcknn/dejavu
/plugin install dejavu@dejavu
```

Sessions opened inside the [Dejavu repo](https://github.com/mttmcknn/dejavu) auto-load the same skills from `.claude/skills/` without installing the plugin.

### Programmatic checks

You can also use `getRecompositionCount()` to let agents make programmatic decisions:

```kotlin
@Test
fun agentCanCheckRecompositionBudget() {
    composeTestRule.onNodeWithTag("inc_button").performClick()

    val count = composeTestRule.getRecompositionCount("product_list")
    // Agent can read this value and decide whether its changes improved things
    assertThat(count).isAtMost(2)
}
```

## Stream Composition State to AI Agents via Logcat

Beyond test assertions, you can give AI agents real-time visibility into what your UI is doing by enabling Dejavu's logcat output. When `logToLogcat = true`, every recomposition event is streamed under the `"Dejavu"` tag — agents monitoring logcat can follow along with composition changes as they happen.

```kotlin
// In your debug Application class
Dejavu.enable(app = this, logToLogcat = true)
```

The agent sees a structured stream:

```
D/Dejavu: Dejavu enabled — streaming recomposition events (filter: "Dejavu")
D/Dejavu: Composition changed (1 snapshot(s), roots=3)
D/Dejavu: Recomposition #1: [counter_value] demo.app.ui.CounterValue (Counter.kt:29), parent=demo.app.ui.CounterScreen
D/Dejavu: Recomposition #2: [counter_value] demo.app.ui.CounterValue (Counter.kt:29), parent=demo.app.ui.CounterScreen
```

Each line tells the agent which composable recomposed, where it lives in source, and which parent triggered it. An agent running `adb logcat -s Dejavu` gets a live feed of UI state it can use to:

- **Verify its own changes** — after refactoring a composable, watch whether recompositions increase or decrease without needing a full test cycle
- **Diagnose recomposition inefficiencies** — spot cascading recompositions in real time by following the parent chain (`parent=demo.app.ui.CounterScreen`)
- **Build context about UI behavior** — understand how user interactions map to composition changes before deciding where unnecessary recompositions occur

This turns logcat into a lightweight observability layer for composition, giving agents the same kind of real-time signal that a developer would get from Layout Inspector — but in a format they can parse and reason about.

## Guardrail Against Unexpected UI Changes

When AI agents — or any automated tooling — modify your codebase, they can introduce subtle changes to recomposition behavior without touching any visible UI. A refactor that moves a state read higher in the tree, or inlines a composable that was previously skippable, can cause cascading recompositions that reduce UI efficiency silently.

Dejavu tests act as guardrails. If an agent's changes cause a composable to recompose more than expected, the test fails before the change is ever merged:

```kotlin
@Test
fun headerRemainsStable_whenListScrolls() {
    // Scroll the list
    composeTestRule.onNodeWithTag("product_list").performScrollToIndex(10)

    // Header must not recompose during scroll — lock this expectation in
    composeTestRule.onNodeWithTag("product_header").assertStable()

    // Each visible item recomposes at most once during scroll
    composeTestRule.onNodeWithTag("product_item_0").assertRecompositions(atMost = 1)
}
```

This is particularly valuable in codebases where AI agents operate with broad permissions. The agents can move fast and make structural changes, but they can't silently introduce recomposition regressions — the test suite catches it. You get the speed of automated refactoring with the confidence that recomposition behavior is preserved.

## Enforce Recomposition Budgets in CI

Combine Dejavu with your CI pipeline to enforce recomposition budgets across your entire app. Every PR that increases recomposition counts beyond the expected values will fail the build:

```kotlin
@Test
fun criticalPath_meetsRecompositionBudget() {
    // Simulate the critical user flow
    composeTestRule.onNodeWithTag("search_input").performTextInput("shoes")
    composeTestRule.waitForIdle()

    // Enforce budget: search results render efficiently
    composeTestRule.onNodeWithTag("search_results").assertRecompositions(atMost = 2)
    composeTestRule.onNodeWithTag("search_header").assertStable()
    composeTestRule.onNodeWithTag("filter_bar").assertStable()
}
```

This works the same way you'd use snapshot tests for visual regressions — except instead of pixels, you're locking in recomposition behavior. The test documents the expected recomposition contract and enforces it automatically.
