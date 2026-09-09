package dejavu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dejavu.internal.DejavuTracer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-platform port of Android LazyVariantsTest.
 * LazyRow + LazyVerticalGrid with set-based selection.
 *
 * Every assertion is exact and self-validating against a [GroundTruth] `SideEffect` (the runtime
 * runs the effect after every successful composition, so it is the real composition count):
 * - **Single-instance** nodes (`RowSelectionCount`/`row_selection_count`,
 *   `GridHighlightCount`/`grid_highlight_count`) have unique composer keys, so the public per-tag
 *   API resolves their exact count on all platforms → `exactly = GroundTruth.delta(tag)`.
 * - `RowItem` and `GridCell` are emitted from Lazy `items {}` loops (one call site each), so their
 *   per-*instance* counts only resolve on Android. On the common targets the public per-tag count
 *   falls back to the shared *function-level* sum, so those tests assert the function-level count —
 *   `DejavuTracer.getRecompositionCount("dejavu.RowItem"/"dejavu.GridCell")` ==
 *   `GroundTruth.delta(...)` (tracer == real total recompositions across all loop items).
 *
 * All tests render a LazyVerticalGrid on every target. This also guards the inspection-collection
 * identity fix: a content-hashed collection crashes Compose registration on iOS and Wasm (#21).
 */
@OptIn(ExperimentalTestApi::class)
class LazyVariantsPatternTest {

    @BeforeTest
    fun setUp() {
        enableDejavuForTest()
        GroundTruth.clear()
    }

    @AfterTest
    fun tearDown() = disableDejavuForTest()

    // --- LazyRow tests ---

    @Test
    fun lazyRow_tagMappingWorks() = runComposeUiTest {
        setContent { DejavuTestContent { LazyVariantsScreen() } }
        waitForIdle()
        refreshTagMapping()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        // RowItem is loop-emitted (Lazy items), so per-instance counts are Android-only; on the
        // common targets the per-tag count falls back to the function-level sum. No interaction
        // occurred, so the function-level RowItem count must equal the ground truth (both 0).
        assertEquals(
            GroundTruth.delta("RowItem"),
            DejavuTracer.getRecompositionCount("dejavu.RowItem"),
            "tracer RowItem count should equal SideEffect ground truth",
        )
        assertEquals(0, GroundTruth.delta("RowItem"), "no row item composes without interaction")
    }

    @Test
    fun lazyRow_selectItem_selectionCountRecomposes() = runComposeUiTest {
        setContent { DejavuTestContent { LazyVariantsScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("select_row_btn").performClick()
        waitForIdle()

        // Adding item 0 changes selectedRowItems.size 0 → 1, recomposing the single-instance count.
        onNodeWithTag("row_selection_count")
            .assertRecompositions(exactly = GroundTruth.delta("row_selection_count"))
        assertEquals(1, GroundTruth.delta("row_selection_count"), "row selection count recomposes once when size changes")
    }

    @Test
    fun lazyRow_selectItem_gridCountStable() = runComposeUiTest {
        setContent { DejavuTestContent { LazyVariantsScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("select_row_btn").performClick()
        waitForIdle()

        // Selecting a row item does not touch highlightedGridCells, so the grid count is stable.
        onNodeWithTag("grid_highlight_count")
            .assertRecompositions(exactly = GroundTruth.delta("grid_highlight_count"))
        assertEquals(0, GroundTruth.delta("grid_highlight_count"), "grid count must not recompose on a row-only change")
        onNodeWithTag("grid_highlight_count").assertStable()
    }

    @Test
    fun lazyRow_initiallyStable() = runComposeUiTest {
        setContent { DejavuTestContent { LazyVariantsScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        // No interaction occurred, so the single-instance row count must be stable.
        onNodeWithTag("row_selection_count")
            .assertRecompositions(exactly = GroundTruth.delta("row_selection_count"))
        assertEquals(0, GroundTruth.delta("row_selection_count"), "row count is stable without interaction")
        onNodeWithTag("row_selection_count").assertStable()
    }

    // --- LazyVerticalGrid tests ---

    @Test
    fun lazyGrid_tagMappingWorks() = runComposeUiTest {
        setContent { DejavuTestContent { LazyVariantsScreen() } }
        waitForIdle()
        refreshTagMapping()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        // GridCell is loop-emitted (Lazy grid items), so per-instance counts are Android-only; on the
        // common targets the per-tag count falls back to the function-level sum. No interaction
        // occurred, so the function-level GridCell count must equal the ground truth (both 0).
        assertEquals(
            GroundTruth.delta("GridCell"),
            DejavuTracer.getRecompositionCount("dejavu.GridCell"),
            "tracer GridCell count should equal SideEffect ground truth",
        )
        assertEquals(0, GroundTruth.delta("GridCell"), "no grid cell composes without interaction")
    }

    @Test
    fun lazyGrid_selectCell_highlightCountRecomposes() = runComposeUiTest {
        setContent { DejavuTestContent { LazyVariantsScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("select_grid_btn").performClick()
        waitForIdle()

        // Highlighting cell 0 changes highlightedGridCells.size 0 → 1, recomposing the count.
        onNodeWithTag("grid_highlight_count")
            .assertRecompositions(exactly = GroundTruth.delta("grid_highlight_count"))
        assertEquals(1, GroundTruth.delta("grid_highlight_count"), "grid highlight count recomposes once when size changes")
    }

    @Test
    fun lazyGrid_selectCell_rowCountStable() = runComposeUiTest {
        setContent { DejavuTestContent { LazyVariantsScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("select_grid_btn").performClick()
        waitForIdle()

        // Highlighting a grid cell does not touch selectedRowItems, so the row count is stable.
        onNodeWithTag("row_selection_count")
            .assertRecompositions(exactly = GroundTruth.delta("row_selection_count"))
        assertEquals(0, GroundTruth.delta("row_selection_count"), "row count must not recompose on a grid-only change")
        onNodeWithTag("row_selection_count").assertStable()
    }

    @Test
    fun lazyGrid_initiallyStable() = runComposeUiTest {
        setContent { DejavuTestContent { LazyVariantsScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        // No interaction occurred, so the single-instance grid count must be stable.
        onNodeWithTag("grid_highlight_count")
            .assertRecompositions(exactly = GroundTruth.delta("grid_highlight_count"))
        assertEquals(0, GroundTruth.delta("grid_highlight_count"), "grid count is stable without interaction")
        onNodeWithTag("grid_highlight_count").assertStable()
    }
}

// ── Composables ──────────────────────────────────────────────────

@Composable
private fun LazyVariantsScreen() {
    var selectedRowItems by remember { mutableStateOf(setOf<Int>()) }
    var highlightedGridCells by remember { mutableStateOf(setOf<Int>()) }

    Column {
        LazyRow(
            modifier = Modifier.height(60.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(10, key = { it }) { index ->
                RowItem(index, selectedRowItems.contains(index))
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(200.dp)
        ) {
            items(8, key = { it }) { index ->
                GridCell(index, highlightedGridCells.contains(index))
            }
        }

        RowSelectionCount(selectedRowItems.size)
        GridHighlightCount(highlightedGridCells.size)

        Row {
            SelectRowItemButton { selectedRowItems = selectedRowItems + 0 }
            ClearRowButton { selectedRowItems = emptySet() }
        }
        Row {
            SelectGridCellButton { highlightedGridCells = highlightedGridCells + 0 }
            ClearGridButton { highlightedGridCells = emptySet() }
        }
    }
}

@Composable
private fun RowItem(index: Int, selected: Boolean) {
    // One Lazy items {} call site emits every row item, so the tracer aggregates them under the
    // shared compile-time key on non-Android. Record at the function level to match the
    // function-level assertions (per-instance counts are Android-only).
    SideEffect { GroundTruth.record("RowItem") }
    BasicText(
        text = if (selected) "R$index*" else "R$index",
        modifier = Modifier
            .testTag("row_item_$index")
            .width(50.dp)
            .padding(4.dp)
    )
}

@Composable
private fun GridCell(index: Int, highlighted: Boolean) {
    // One Lazy grid items {} call site emits every grid cell, so the tracer aggregates them under
    // the shared compile-time key on non-Android. Record at the function level to match the
    // function-level assertions (per-instance counts are Android-only).
    SideEffect { GroundTruth.record("GridCell") }
    BasicText(
        text = if (highlighted) "G$index*" else "G$index",
        modifier = Modifier
            .testTag("grid_cell_$index")
            .padding(8.dp)
    )
}

@Composable
private fun RowSelectionCount(count: Int) {
    SideEffect { GroundTruth.record("row_selection_count") }
    BasicText("Row selected: $count", modifier = Modifier.testTag("row_selection_count"))
}

@Composable
private fun GridHighlightCount(count: Int) {
    SideEffect { GroundTruth.record("grid_highlight_count") }
    BasicText("Grid highlighted: $count", modifier = Modifier.testTag("grid_highlight_count"))
}

@Composable
private fun SelectRowItemButton(onClick: () -> Unit) {
    BasicText("Select Row 0", Modifier.testTag("select_row_btn").clickable(onClick = onClick))
}

@Composable
private fun ClearRowButton(onClick: () -> Unit) {
    BasicText("Clear Row", Modifier.testTag("clear_row_btn").clickable(onClick = onClick))
}

@Composable
private fun SelectGridCellButton(onClick: () -> Unit) {
    BasicText("Select Grid 0", Modifier.testTag("select_grid_btn").clickable(onClick = onClick))
}

@Composable
private fun ClearGridButton(onClick: () -> Unit) {
    BasicText("Clear Grid", Modifier.testTag("clear_grid_btn").clickable(onClick = onClick))
}
