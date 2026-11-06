# Dejavu: Error Messages Guide

When a recomposition assertion fails, Dejavu throws an `UnexpectedRecompositionsError` with a structured, multi-section diagnostic. This guide explains how to read each section and what to do about common failures.

---

## Full Error Message Structure

A complete failure message contains up to seven sections:

```
dejavu.UnexpectedRecompositionsError: Recomposition assertion failed for testTag='product_header'
  Composable: demo.app.ui.ProductHeader (ProductList.kt:29)
  Expected: exactly 0 recomposition(s)
  Actual: 1 recomposition(s)

  All tracked composables:
    ProductListScreen = 1
    ProductHeader    = 1  <-- FAILED
    ProductItem      = 1
    CartBanner       = 1
    TotalDisplay     = 0
    ProductFooter    = 0

  Recomposition timeline:
    #1 at +0ms — param slots changed: [1] | parent: ProductListScreen

  Possible cause:
    1 state change(s) of type Int
    Parameter/parent change detected (dirty bits set)

  Node:
    Node #42 [TestTag='product_header', Text='Products (1 selected)']

  Semantic tree:
    Node #1
      Node #10 [TestTag='product_header', Text='Products (1 selected)']  <-- THIS NODE
      Node #20 [TestTag='product_item']
      ...
```

---

## Section-by-Section Breakdown

### 1. Error Header

```
dejavu.UnexpectedRecompositionsError: Recomposition assertion failed for testTag='product_header'
```

Identifies which test tag triggered the failure. If the tag could not be mapped to a composable function, the assertion fails with an unavailable-count diagnostic.

### 2. Composable Name and Source Location

```
  Composable: demo.app.ui.ProductHeader (ProductList.kt:29)
```

The fully qualified composable function name and its source file location. This comes from the Compose compiler's source info embedded in the composition trace. Use this to jump directly to the composable in your IDE.

If the tag cannot be resolved to a composable, this section shows:

```
  testTag 'my_tag' could not be mapped to a composable function
  Actual: unavailable — no tag-to-composable mapping
```

This can mean inspection tables or compiler source information are missing, or the tag is on a
node that Dejavu cannot associate with a user-defined composable. Use
`createRecompositionTrackingRule()`, or pair `runRecompositionTrackingUiTest` with
`setTrackedContent`. Ensure the Compose compiler includes source information.

An unavailable count is never treated as zero: `assertStable()` and all recomposition bounds
fail until the tag can be mapped. Android's `getRecompositionCount(tag)` throws
`IllegalStateException` for the same condition. A test tag that happens to match a function name
does not bypass this requirement.

### 3. Expected vs Actual

```
  Expected: exactly 0 recomposition(s)
  Actual: 1 recomposition(s)
```

Shows what you asserted and what actually happened. The description matches the assertion parameters:

| Assertion | Description |
|-----------|-------------|
| `assertRecompositions(exactly = 0)` | `exactly 0` |
| `assertRecompositions(atLeast = 1)` | `at least 1` |
| `assertRecompositions(atMost = 3)` | `at most 3` |
| `assertRecompositions(atLeast = 1, atMost = 5)` | `between 1 and 5` |
| `assertStable()` | `exactly 0` |

A special actual value indicates the composable was never tracked:

```
  Actual: composable was never composed or isn't being tracked
```

### 4. All Tracked Composables

```
  All tracked composables:
    ProductListScreen = 1
    ProductHeader    = 1  <-- FAILED
    ProductItem      = 1
    CartBanner       = 1
    TotalDisplay     = 0
    ProductFooter    = 0
```

Lists every composable Dejavu is currently tracking, with its recomposition count. The `<-- FAILED` marker highlights the composable that triggered the assertion failure.

**How to use this section:**

- **Check for typos** -- if your tag is not in the list, you may have a name mismatch between `Modifier.testTag()` and your assertion
- **Spot cascade patterns** -- if the parent and child both recomposed the same number of times, the child is likely being invalidated by the parent rather than by its own state
- **Find the source** -- look for the composable with the highest count to identify where the cascade originates

### 5. Recomposition Timeline

```
  Recomposition timeline:
    #1 at +0ms — param slots changed: [1] | parent: ProductListScreen
```

Shows each individual recomposition event in chronological order:

- **#N** -- the sequence number (1-indexed)
- **at +Xms** -- milliseconds relative to the first recomposition event for this composable
- **param slots changed: [...]** -- which parameter slot indices had their dirty bits set (2 bits per parameter in the Compose compiler's dirty mask; slot 0 = first parameter, slot 1 = second, etc.)
- **parent: Name** -- the parent composable that was active in the composition stack when this recomposition occurred

**Interpreting param slots:**

Each composable function's parameters occupy 2-bit slots in a dirty bitmask. When Compose recomposes a function, it marks which parameter slots are "dirty" (changed). The slot list tells you which parameters triggered the recomposition:

- `[0]` -- first parameter changed
- `[0, 1]` -- first and second parameters changed
- `[1]` -- second parameter changed, first was unchanged
- `[]` -- no parameter slots marked dirty (rare; usually means state-driven)

### 6. Possible Cause

```
  Possible cause:
    1 state change(s) of type Int
    Parameter/parent change detected (dirty bits set)
```

Causality analysis from Dejavu's `Snapshot.registerApplyObserver` and dirty bit tracking:

- **State changes** -- how many `Snapshot` state objects changed, and their types (e.g., `Int`, `String`, `SnapshotStateList`)
- **Same-value writes** -- state was written but the new value equaled the old value (this triggers unnecessary recompositions)
- **Parameter/parent change** -- the recomposition was driven by dirty bits (parameter values changed or parent recomposed)

See [Causality Analysis](causality-analysis.md) for a deep dive into causality analysis.

### 7. Node and Semantic Tree

```
  Node:
    Node #42 [TestTag='product_header', Text='Products (1 selected)']

  Semantic tree:
    Node #1
      Node #10 [TestTag='product_header', Text='Products (1 selected)']  <-- THIS NODE
      Node #20 [TestTag='product_item']
```

The failing semantics node and a dump of the full semantic tree. The `<-- THIS NODE` marker highlights the asserted node in the tree. This helps you understand the node's position in the UI hierarchy and verify you are asserting on the correct element.

---

## Common Failure Patterns

### Pattern 1: Stable component recomposed (parent cascade)

```
  Expected: exactly 0 recomposition(s)
  Actual: 1 recomposition(s)

  All tracked composables:
    ParentScreen     = 1
    HeaderComponent  = 1  <-- FAILED
    ContentArea      = 1

  Recomposition timeline:
    #1 at +0ms — param slots changed: [0] | parent: ParentScreen

  Possible cause:
    1 state change(s) of type Int
    Parameter/parent change detected (dirty bits set)
```

**Diagnosis:** The parent recomposed and passed a new parameter value (or an unstable reference) to the child. The child's parameter slot 0 was marked dirty.

**Fixes:**
- Make the parameter type stable (`data class`, `@Stable`, `@Immutable`)
- Narrow the parameter (e.g., pass `Boolean` instead of `Int` if only a threshold matters)
- Hoist state reads closer to where they are consumed

### Pattern 2: Same-value write

```
  Expected: exactly 1 recomposition(s)
  Actual: 3 recomposition(s)

  Possible cause:
    3 state change(s) of type Product (2 same-value write(s)!)
    Parameter/parent change detected (dirty bits set)
```

**Diagnosis:** State was written 3 times, but 2 of those writes set the same value that was already there. Each write still triggered a recomposition because the state object did not use structural equality.

**Fixes:**
- Use `data class` so `equals()` is structural
- Use `mutableStateOf(value, policy = structuralEqualityPolicy())`
- Guard writes: `if (newValue != currentValue) state.value = newValue`

### Pattern 3: Tag not mapped to composable

```
  Recomposition assertion failed for testTag='my_component'
  testTag 'my_component' could not be mapped to a composable function
  Expected: exactly 0 recomposition(s)
  Actual: unavailable — no tag-to-composable mapping
```

**Diagnosis:** Dejavu found the semantics node (the tag exists in the tree) but could not map it back to a user-defined composable function. Check inspection-table setup and source information before interpreting this as a recomposition count.

**Fixes:**
- Use `setTrackedContent` inside `runRecompositionTrackingUiTest`, or the Android tracking rule
- Enable Compose compiler source information
- Move `Modifier.testTag()` to the outermost composable in your function
- Verify the composable is a user-defined function (not a framework primitive)

### Pattern 4: Cumulative recomposition from multiple state sources

```
  Expected: at most 1 recomposition(s)
  Actual: 3 recomposition(s)

  Recomposition timeline:
    #1 at +0ms — param slots changed: [0] | parent: Screen
    #2 at +16ms — param slots changed: [1] | parent: Screen
    #3 at +32ms — param slots changed: [0, 1] | parent: Screen
```

**Diagnosis:** Three separate state changes caused three separate recompositions. The timeline shows different parameter slots being marked dirty at different times, suggesting multiple independent state sources.

**Fixes:**
- Batch state updates so they occur in the same snapshot (e.g., `Snapshot.withMutableSnapshot { }`)
- Consolidate related state into a single state object
- Use `derivedStateOf` to coalesce fine-grained state into coarser signals

### Pattern 5: Off-screen lazy item not found

```
java.lang.AssertionError: Failed to get semantics node for recomposition assertion
```

**Diagnosis:** The semantics node does not exist because the lazy item is not composed (off-screen).

**Fix:** Scroll the item into view before asserting, or only assert on items you know are visible.

---

## Reading the Error Quickly

For fast triage, read in this order:

1. **Expected vs Actual** -- understand the gap
2. **All tracked composables** -- find the `<-- FAILED` marker and check if the parent has the same count (cascade pattern)
3. **Possible cause** -- check for same-value writes (immediate bug) or parameter-driven (needs investigation)
4. **Recomposition timeline** -- see which parameter slots changed and when
5. **Composable source location** -- jump to the code to investigate
