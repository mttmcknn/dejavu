@file:OptIn(ExperimentalComposeApi::class, ExperimentalTestApi::class)

package dejavu.experimental

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.ExperimentalComposeApi
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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dejavu.assertRecompositions
import dejavu.assertStable
import dejavu.resetRecompositionCounts
import dejavu.runRecompositionTrackingUiTest
import dejavu.setTrackedContent
import kotlinx.coroutines.test.TestResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Self-validating recomposition accuracy tests on the LinkBuffer composer runtime path
 * (`ComposeRuntimeFlags.isLinkBufferComposerEnabled = true`).
 *
 * Each tracked node records a [GroundTruth] `SideEffect`, so every test asserts
 * `exactly = GroundTruth.delta(tag)` — proving Dejavu's tracer count equals the runtime's
 * real recomposition count even with the LinkBuffer composer enabled, including under a
 * deliberate misconfiguration.
 */
class LinkBufferRegressionTest {
    @Test
    fun linkBuffer_gridStillTracksChangedCell() = withLinkBufferComposer {
        GroundTruth.clear()
        setTrackedContent { GridRegressionScreen() }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("grid_increment_0").performClick()
        waitForIdle()

        onNodeWithTag("grid_cell_0").assertRecompositions(exactly = GroundTruth.delta("grid_cell_0"))
        onNodeWithTag("grid_cell_1").assertRecompositions(exactly = GroundTruth.delta("grid_cell_1"))
        assertTrue(GroundTruth.delta("grid_cell_0") >= 1, "changed cell should recompose")
        assertEquals(0, GroundTruth.delta("grid_cell_1"), "sibling cell should stay stable")
    }

    @Test
    fun linkBuffer_flexBoxStillTracksChangedItem() = withLinkBufferComposer {
        GroundTruth.clear()
        setTrackedContent { FlexRegressionScreen() }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("flex_increment_0").performClick()
        waitForIdle()

        onNodeWithTag("flex_item_0").assertRecompositions(exactly = GroundTruth.delta("flex_item_0"))
        onNodeWithTag("flex_item_1").assertRecompositions(exactly = GroundTruth.delta("flex_item_1"))
        assertTrue(GroundTruth.delta("flex_item_0") >= 1, "changed item should recompose")
        assertEquals(0, GroundTruth.delta("flex_item_1"), "sibling item should stay stable")
    }

    @Test
    fun linkBuffer_movableContentMoveKeepsChildStable() = withLinkBufferComposer {
        GroundTruth.clear()
        setTrackedContent { MovableRegressionScreen() }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("move_content").performClick()
        waitForIdle()

        onNodeWithTag("movable_child").assertRecompositions(exactly = GroundTruth.delta("movable_child"))
        assertEquals(0, GroundTruth.delta("movable_child"), "moved child should keep its state and stay stable")
    }

    @Test
    fun linkBuffer_misconfiguredGridSiblingOverRecomposesAndDejavuCountsIt() = withLinkBufferComposer {
        // Same over-recomposition misconfiguration as the non-LinkBuffer Grid test, run under the
        // LinkBuffer composer to prove Dejavu's exact count holds on that runtime path too.
        GroundTruth.clear()
        setTrackedContent { GridMisconfiguredScreen() }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        val n = 4
        repeat(n) {
            onNodeWithTag("grid_increment_0").performClick()
            waitForIdle()
        }

        onNodeWithTag("grid_cell_0").assertRecompositions(exactly = GroundTruth.delta("grid_cell_0"))
        onNodeWithTag("grid_cell_1").assertRecompositions(exactly = GroundTruth.delta("grid_cell_1"))
        assertEquals(n, GroundTruth.delta("grid_cell_1"), "misconfigured sibling should over-recompose N times under LinkBuffer")
        assertTrue(GroundTruth.delta("grid_cell_0") >= 1, "changed cell should recompose")
    }

    @Test
    fun linkBuffer_overBudgetAssertionThrowsAndIsCaught() = withLinkBufferComposer {
        // Prove the failure path fires on the LinkBuffer runtime path too: the misconfigured grid
        // sibling over-recomposes, so a too-strict assertion MUST throw a catchable AssertionError.
        GroundTruth.clear()
        setTrackedContent { GridMisconfiguredScreen() }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        val n = 4
        repeat(n) {
            onNodeWithTag("grid_increment_0").performClick()
            waitForIdle()
        }

        val error = assertFailsWith<AssertionError> {
            onNodeWithTag("grid_cell_1").assertStable()
        }
        assertTrue(
            error.message != null && error.message!!.isNotBlank(),
            "thrown error should carry a descriptive message",
        )
        // The CORRECT (exact) assertion on the same node still passes — caught & handled as intended.
        onNodeWithTag("grid_cell_1").assertRecompositions(exactly = GroundTruth.delta("grid_cell_1"))
    }
}

private fun withLinkBufferComposer(block: ComposeUiTest.() -> Unit): TestResult = runRecompositionTrackingUiTest {
    val previous = ComposeRuntimeFlags.isLinkBufferComposerEnabled
    ComposeRuntimeFlags.isLinkBufferComposerEnabled = true
    try {
        block()
    } finally {
        ComposeRuntimeFlags.isLinkBufferComposerEnabled = previous
    }
}

@Composable
private fun MovableRegressionScreen() {
    var firstSlot by remember { mutableStateOf(true) }
    val movable = remember { movableContentOf { MovableChild() } }

    Column {
        Row {
            Box(Modifier.testTag("slot_a")) {
                if (firstSlot) movable()
            }
            Box(Modifier.testTag("slot_b")) {
                if (!firstSlot) movable()
            }
        }
        BasicText(
            text = "Move",
            modifier = Modifier
                .testTag("move_content")
                .clickable { firstSlot = !firstSlot },
        )
    }
}

@Composable
private fun MovableChild() {
    SideEffect { GroundTruth.record("movable_child") }
    BasicText("Movable", Modifier.testTag("movable_child"))
}
