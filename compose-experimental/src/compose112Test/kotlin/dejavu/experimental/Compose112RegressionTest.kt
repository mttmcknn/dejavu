@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.runtime.ExperimentalComposeApi::class)

package dejavu.experimental

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dejavu.assertRecompositions
import dejavu.assertStable
import dejavu.resetRecompositionCounts
import dejavu.runRecompositionTrackingUiTest
import dejavu.setTrackedContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Tests compiled only against Compose 1.12, with unkeyed effects as the count oracle. */
class Compose112RegressionTest {
    @Test
    fun stableEffectKeyDoesNotHideIntentionalOverRecomposition() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        var tick by mutableIntStateOf(0)
        setTrackedContent { KeyedEffectNode(tick, "same") }
        snapshotCounts()

        // tick is deliberately redundant work. A keyed SideEffect must NOT be our count oracle.
        repeat(4) { runOnIdle { tick++ }; waitForIdle() }
        assertEquals(4, GroundTruth.delta("keyed_node"))
        assertEquals(0, GroundTruth.delta("keyed_callback"))
        onNodeWithTag("keyed_node").assertRecompositions(exactly = GroundTruth.delta("keyed_node"))
        assertFailsWith<AssertionError> { onNodeWithTag("keyed_node").assertStable() }
    }

    @Test
    fun changedEffectKeyRunsOnceAndEqualStateWritesStayStable() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        var key by mutableStateOf("first")
        setTrackedContent { KeyedEffectNode(0, key) }
        snapshotCounts()
        runOnIdle { key = "second" }
        waitForIdle()
        assertEquals(1, GroundTruth.delta("keyed_callback"))
        assertEquals(1, GroundTruth.delta("keyed_node"))
        onNodeWithTag("keyed_node").assertRecompositions(exactly = GroundTruth.delta("keyed_node"))

        snapshotCounts()
        runOnIdle { key = "second" }
        waitForIdle()
        assertEquals(0, GroundTruth.delta("keyed_callback"))
        assertEquals(0, GroundTruth.delta("keyed_node"))
        onNodeWithTag("keyed_node").assertStable()
    }

    @Test
    fun shrinkingSideEffectKeysSchedulesEffectEvenWithTheSamePrefix() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        var keys by mutableStateOf(listOf("a", "b", "c", "d"))
        setTrackedContent { VarargEffectNode(keys) }
        snapshotCounts()
        runOnIdle { keys = listOf("a", "b") }
        waitForIdle()
        assertEquals(1, GroundTruth.delta("vararg_callback"))
        assertEquals(1, GroundTruth.delta("vararg_node"))
        onNodeWithTag("vararg_node").assertRecompositions(exactly = GroundTruth.delta("vararg_node"))
    }

    @Test
    fun shrinkingRememberKeysInvalidatesTheCachedValue() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        var keys by mutableStateOf(listOf("a", "b", "c", "d"))
        setTrackedContent { RememberKeysNode(keys) }
        snapshotCounts()
        runOnIdle { keys = listOf("a", "b") }
        waitForIdle()
        onNodeWithTag("remember_node").assertTextEquals("a,b")
        assertEquals(1, GroundTruth.delta("remember_calculation"))
        assertEquals(1, GroundTruth.delta("remember_node"))
        onNodeWithTag("remember_node").assertRecompositions(exactly = GroundTruth.delta("remember_node"))
    }

    @Test
    fun assertionsWithoutImplicitWaitReadSettledFramesWithoutAdvancingTheClock() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        var tick by mutableIntStateOf(0)
        setTrackedContent { KeyedEffectNode(tick, "same") }
        snapshotCounts()
        mainClock.autoAdvance = false
        repeat(3) { index ->
            runOnUiThread { tick++ }
            mainClock.advanceTimeByFrame()
            waitForIdle()
            val frameTime = mainClock.currentTime
            runOnUiThread {
                runWithoutImplicitWait {
                    onNodeWithTag("keyed_node").assertTextEquals("${index + 1}:same")
                    assertEquals(index + 1, GroundTruth.delta("keyed_node"))
                    onNodeWithTag("keyed_node").assertRecompositions(exactly = GroundTruth.delta("keyed_node"))
                }
            }
            assertEquals(frameTime, mainClock.currentTime)
        }
    }

    @Test
    fun linkBufferNestedMovableContentPreservesStateAndTracksLaterUpdates() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        val previous = ComposeRuntimeFlags.isLinkBufferComposerEnabled
        ComposeRuntimeFlags.isLinkBufferComposerEnabled = true
        try {
            var firstSlot by mutableStateOf(true)
            setTrackedContent { NestedMovableScreen(firstSlot) }
            onNodeWithTag("nested_child").performClick()
            waitForIdle()
            onNodeWithTag("nested_child").assertTextEquals("1")
            snapshotCounts()
            repeat(2) { runOnIdle { firstSlot = !firstSlot }; waitForIdle() }
            onNodeWithTag("nested_child").assertTextEquals("1")
            assertEquals(0, GroundTruth.delta("nested_child"))
            onNodeWithTag("nested_child").assertStable()
            onNodeWithTag("nested_child").performClick()
            waitForIdle()
            onNodeWithTag("nested_child").assertTextEquals("2")
            assertEquals(1, GroundTruth.delta("nested_child"))
            onNodeWithTag("nested_child").assertRecompositions(exactly = GroundTruth.delta("nested_child"))
        } finally {
            ComposeRuntimeFlags.isLinkBufferComposerEnabled = previous
        }
    }
}

private fun ComposeUiTest.snapshotCounts() {
    waitForIdle()
    resetRecompositionCounts()
    GroundTruth.snapshotBaseline()
}

@Composable
private fun KeyedEffectNode(tick: Int, key: String) {
    SideEffect { GroundTruth.record("keyed_node") }
    SideEffect(key) { GroundTruth.record("keyed_callback") }
    BasicText("$tick:$key", Modifier.testTag("keyed_node"))
}

@Composable
private fun VarargEffectNode(keys: List<String>) {
    SideEffect { GroundTruth.record("vararg_node") }
    SideEffect(*keys.toTypedArray()) { GroundTruth.record("vararg_callback") }
    BasicText(keys.joinToString(), Modifier.testTag("vararg_node"))
}

@Composable
private fun RememberKeysNode(keys: List<String>) {
    SideEffect { GroundTruth.record("remember_node") }
    val remembered = remember(*keys.toTypedArray()) {
        GroundTruth.record("remember_calculation")
        keys.joinToString(",")
    }
    BasicText(remembered, Modifier.testTag("remember_node"))
}

@Composable
private fun NestedMovableScreen(firstSlot: Boolean) {
    val inner = remember { movableContentOf { NestedCounter() } }
    val outer = remember { movableContentOf { Column { inner() } } }
    Row {
        Box { if (firstSlot) outer() }
        Box { if (!firstSlot) outer() }
    }
}

@Composable
private fun NestedCounter() {
    var count by remember { mutableIntStateOf(0) }
    SideEffect { GroundTruth.record("nested_child") }
    BasicText("$count", Modifier.testTag("nested_child").clickable { count++ })
}
