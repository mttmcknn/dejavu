package dejavu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.atomicfu.atomic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Exact instance counts must follow executed anonymous bodies, independently of
 * shared source keys, parameter fingerprints, visible output, and query frequency.
 * Each probe owns a non-snapshot counter incremented by an unkeyed SideEffect in
 * the registered lambda itself. Baselines are captured after content settles.
 */
@OptIn(ExperimentalTestApi::class)
open class ExactLambdaInstanceTest {
    @Test
    fun registeredSiblings_countThreeAndZeroWithoutIntermediateQueries() =
        exerciseRegisteredSiblings(queryEachUpdate = false)

    @Test
    fun registeredSiblings_countDoesNotDependOnQueryFrequency() =
        exerciseRegisteredSiblings(queryEachUpdate = true)

    private fun exerciseRegisteredSiblings(queryEachUpdate: Boolean) = runRecompositionTrackingUiTest {
        val changing = mutableIntStateOf(0)
        val echo = LambdaProbe("echo", changing)
        val sibling = LambdaProbe("sibling", mutableIntStateOf(0))
        val registry = countingRegistry()
        setTrackedContent {
            Column {
                key("echo") { registry.getValue("value")(echo) }
                key("sibling") { registry.getValue("value")(sibling) }
            }
        }
        waitForIdle()
        val echoBaseline = echo.executionCount()
        val siblingBaseline = sibling.executionCount()
        resetRecompositionCounts()

        repeat(3) { index ->
            runOnIdle { changing.intValue++ }
            waitForIdle()
            if (queryEachUpdate) {
                repeat(2) {
                    assertBodyCount(echo, echoBaseline, index + 1)
                    assertBodyCount(sibling, siblingBaseline, 0)
                }
            }
        }

        onNodeWithTag("echo").assertTextEquals("echo:3")
        onNodeWithTag("sibling").assertTextEquals("sibling:0")
        // Query the unchanged sibling first so a shared function total cannot hide.
        assertBodyCount(sibling, siblingBaseline, 0)
        assertBodyCount(echo, echoBaseline, 3)
        assertFailsWith<AssertionError> { onNodeWithTag("echo").assertRecompositions(atMost = 2) }
        repeat(3) {
            assertBodyCount(echo, echoBaseline, 3)
            assertBodyCount(sibling, siblingBaseline, 0)
        }
    }

    @Test
    fun sameValueWrites_countExecutedBodiesWithIdenticalParametersAndOutput() = runRecompositionTrackingUiTest {
        // Every assignment invalidates the body even though its State reference,
        // value, arguments, and rendered text are identical at all sample points.
        val value = mutableStateOf(0, neverEqualPolicy())
        val changed = LambdaProbe("same_output", value)
        val sibling = LambdaProbe("unchanged", mutableIntStateOf(0))
        val registry = countingRegistry()
        setTrackedContent {
            Column {
                key("changed") { registry.getValue("value")(changed) }
                key("sibling") { registry.getValue("value")(sibling) }
            }
        }
        waitForIdle()
        val baseline = changed.executionCount()
        val siblingBaseline = sibling.executionCount()
        resetRecompositionCounts()

        repeat(3) {
            runOnIdle { value.value = 0 }
            waitForIdle()
        }

        onNodeWithTag("same_output").assertTextEquals("same_output:0")
        assertBodyCount(changed, baseline, 3)
        assertBodyCount(sibling, siblingBaseline, 0)
        assertFailsWith<AssertionError> { onNodeWithTag("same_output").assertStable() }
    }

    @Test
    fun unchangedDerivedValue_doesNotCountInvalidationsAsBodyExecutions() = runRecompositionTrackingUiTest {
        val source = mutableIntStateOf(0)
        val derived = derivedStateOf(structuralEqualityPolicy()) { source.intValue / 10 }
        val probe = LambdaProbe("derived", derived)
        val registry = countingRegistry()
        setTrackedContent { registry.getValue("value")(probe) }
        waitForIdle()
        val baseline = probe.executionCount()
        resetRecompositionCounts()

        repeat(3) {
            runOnIdle { source.intValue++ }
            waitForIdle()
        }
        onNodeWithTag("derived").assertTextEquals("derived:0")
        assertBodyCount(probe, baseline, 0)

        runOnIdle { source.intValue = 10 }
        waitForIdle()
        onNodeWithTag("derived").assertTextEquals("derived:1")
        assertBodyCount(probe, baseline, 1)
        assertFailsWith<AssertionError> { onNodeWithTag("derived").assertStable() }
    }

    @Test
    fun parentRecomposition_skipsAnUnchangedRegisteredBody() = runRecompositionTrackingUiTest {
        val parent = mutableIntStateOf(0)
        val child = mutableIntStateOf(0)
        val parentExecutions = atomic(0)
        val probe = LambdaProbe("child", child)
        val registry = countingRegistry()
        setTrackedContent {
            Column {
                val parentValue = parent.intValue
                SideEffect { parentExecutions.incrementAndGet() }
                BasicText("Parent:$parentValue", Modifier.testTag("parent"))
                registry.getValue("value")(probe)
            }
        }
        waitForIdle()
        val parentBaseline = parentExecutions.value
        val childBaseline = probe.executionCount()
        resetRecompositionCounts()

        repeat(3) {
            runOnIdle { parent.intValue++ }
            waitForIdle()
        }
        onNodeWithTag("parent").assertTextEquals("Parent:3")
        assertEquals(3, parentExecutions.value - parentBaseline)
        assertBodyCount(probe, childBaseline, 0)

        runOnIdle { child.intValue++ }
        waitForIdle()
        onNodeWithTag("child").assertTextEquals("child:1")
        assertBodyCount(probe, childBaseline, 1)
        assertFailsWith<AssertionError> { onNodeWithTag("child").assertStable() }
    }

    @Test
    fun keyedReorder_keepsCountsWithTheExistingInstances() = runRecompositionTrackingUiTest {
        val firstValue = mutableIntStateOf(0)
        val secondValue = mutableIntStateOf(0)
        val first = LambdaProbe("first", firstValue)
        val second = LambdaProbe("second", secondValue)
        val order = mutableStateOf(listOf(first, second))
        val registry = countingRegistry()
        setTrackedContent {
            Column {
                for (probe in order.value) {
                    key(probe.tag) { registry.getValue("value")(probe) }
                }
            }
        }
        waitForIdle()
        val firstBaseline = first.executionCount()
        val secondBaseline = second.executionCount()
        resetRecompositionCounts()

        repeat(2) {
            runOnIdle { firstValue.intValue++ }
            waitForIdle()
        }
        runOnIdle { order.value = listOf(second, first) }
        waitForIdle()
        // Moving keyed groups must not exchange their counters or execute skipped bodies.
        assertBodyCount(first, firstBaseline, 2)
        assertBodyCount(second, secondBaseline, 0)

        runOnIdle { secondValue.intValue++ }
        waitForIdle()
        onNodeWithTag("first").assertTextEquals("first:2")
        onNodeWithTag("second").assertTextEquals("second:1")
        assertBodyCount(first, firstBaseline, 2)
        assertBodyCount(second, secondBaseline, 1)
    }

    @Test
    fun disposedTag_rebindsToTheReplacementScopeAfterCountReset() = runRecompositionTrackingUiTest {
        val oldValue = mutableIntStateOf(0)
        val newValue = mutableIntStateOf(0)
        val oldProbe = LambdaProbe("reused", oldValue)
        val newProbe = LambdaProbe("reused", newValue)
        val current = mutableStateOf(oldProbe)
        val visible = mutableStateOf(true)
        val registry = countingRegistry()
        setTrackedContent {
            if (visible.value) {
                key(current.value) { registry.getValue("value")(current.value) }
            }
        }
        waitForIdle()
        val oldBaseline = oldProbe.executionCount()
        resetRecompositionCounts()
        repeat(2) {
            runOnIdle { oldValue.intValue++ }
            waitForIdle()
        }
        assertBodyCount(oldProbe, oldBaseline, 2)

        runOnIdle { visible.value = false }
        waitForIdle()
        onNodeWithTag("reused").assertDoesNotExist()
        val disposedCount = oldProbe.executionCount()
        runOnIdle {
            current.value = newProbe
            visible.value = true
        }
        waitForIdle()
        onNodeWithTag("reused").assertTextEquals("reused:0")
        val newBaseline = newProbe.executionCount()
        // A fresh measurement window avoids imposing a contract on whether
        // replacement initial composition contributes to the previous window.
        resetRecompositionCounts()
        runOnIdle { oldValue.intValue++ }
        waitForIdle()
        assertEquals(disposedCount, oldProbe.executionCount())
        assertBodyCount(newProbe, newBaseline, 0)

        repeat(3) {
            runOnIdle { newValue.intValue++ }
            waitForIdle()
        }
        onNodeWithTag("reused").assertTextEquals("reused:3")
        assertBodyCount(newProbe, newBaseline, 3)
        assertEquals(disposedCount, oldProbe.executionCount())
        assertFailsWith<AssertionError> { onNodeWithTag("reused").assertRecompositions(exactly = 5) }
    }

    @Test
    fun countReset_keepsLiveInstanceIdentityAndStartsANewWindow() = runRecompositionTrackingUiTest {
        val changedValue = mutableIntStateOf(0)
        val siblingValue = mutableIntStateOf(0)
        val changed = LambdaProbe("changed", changedValue)
        val sibling = LambdaProbe("sibling", siblingValue)
        val registry = countingRegistry()
        setTrackedContent {
            Column {
                key("changed") { registry.getValue("value")(changed) }
                key("sibling") { registry.getValue("value")(sibling) }
            }
        }
        waitForIdle()
        repeat(2) {
            runOnIdle { changedValue.intValue++ }
            waitForIdle()
        }
        val changedBaseline = changed.executionCount()
        val siblingBaseline = sibling.executionCount()
        resetRecompositionCounts()
        assertBodyCount(changed, changedBaseline, 0)
        assertBodyCount(sibling, siblingBaseline, 0)

        runOnIdle { siblingValue.intValue++ }
        waitForIdle()
        onNodeWithTag("changed").assertTextEquals("changed:2")
        onNodeWithTag("sibling").assertTextEquals("sibling:1")
        assertBodyCount(changed, changedBaseline, 0)
        assertBodyCount(sibling, siblingBaseline, 1)
        assertFailsWith<AssertionError> { onNodeWithTag("sibling").assertStable() }
    }
}

// The only public properties are stable inputs. Keeping the non-snapshot oracle
// private makes the skip contract explicit without treating AtomicInt as stable.
@Stable
private class LambdaProbe(
    val tag: String,
    val state: State<Int>,
) {
    private val executions = atomic(0)

    fun recordExecution() { executions.incrementAndGet() }
    fun executionCount(): Int = executions.value
}

private fun countingRegistry(): Map<String, @Composable (LambdaProbe) -> Unit> = mapOf(
    "value" to { probe ->
        val value = probe.state.value
        SideEffect { probe.recordExecution() }
        BasicText("${probe.tag}:$value", Modifier.testTag(probe.tag))
    },
)

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertBodyCount(probe: LambdaProbe, baseline: Int, expected: Int) {
    val actual = probe.executionCount() - baseline
    assertEquals(expected, actual, "Committed body executions for ${probe.tag}")
    onNodeWithTag(probe.tag).assertRecompositions(exactly = actual)
}
