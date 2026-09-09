@file:OptIn(
    ExperimentalGridApi::class,
    ExperimentalFlexBoxApi::class,
    ExperimentalMediaQueryApi::class,
    ExperimentalFoundationStyleApi::class,
)

package dejavu.experimental

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalFlexBoxApi
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.FlexBox
import androidx.compose.foundation.layout.FlexBoxConfig
import androidx.compose.foundation.layout.FlexBoxScope
import androidx.compose.foundation.layout.FlexDirection
import androidx.compose.foundation.layout.FlexWrap
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.GridScope
import androidx.compose.foundation.layout.GridTrackSize
import androidx.compose.foundation.layout.columns
import androidx.compose.foundation.layout.rows
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleScope
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.LocalUiMediaScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiMediaScope
import androidx.compose.ui.derivedMediaQuery
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.mediaQuery
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dejavu.assertRecompositions
import dejavu.assertStable
import dejavu.resetRecompositionCounts
import dejavu.runRecompositionTrackingUiTest
import dejavu.setTrackedContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Self-validating recomposition accuracy tests for Compose 1.11's new layout/style APIs.
 *
 * Every tracked node records a [GroundTruth] `SideEffect`, so each test can assert
 * `exactly = GroundTruth.delta(tag)` — proving Dejavu's tracer count equals the runtime's
 * real post-reset recomposition count for that node. The misconfiguration tests deliberately
 * make a node over-recompose and prove Dejavu reports the exact inflated number, not a guess.
 */
@OptIn(ExperimentalTestApi::class)
class ExperimentalLayoutRegressionTest {
    // ── Grid ──────────────────────────────────────────────────────────

    @Test
    fun grid_changedCellRecomposesAndSiblingStaysStable() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        setTrackedContent { GridRegressionScreen() }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("grid_increment_0").performClick()
        waitForIdle()

        // PRIMARY accuracy: tracer count == SideEffect ground truth.
        onNodeWithTag("grid_cell_0").assertRecompositions(exactly = GroundTruth.delta("grid_cell_0"))
        onNodeWithTag("grid_cell_1").assertRecompositions(exactly = GroundTruth.delta("grid_cell_1"))
        // SECONDARY behavior: changed cell recomposed, sibling stayed stable.
        assertTrue(GroundTruth.delta("grid_cell_0") >= 1, "changed cell should recompose")
        assertEquals(0, GroundTruth.delta("grid_cell_1"), "sibling cell should stay stable")
    }

    @Test
    fun grid_tagMappingSurvivesExplicitPlacementChange() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        setTrackedContent { GridRegressionScreen() }
        waitForIdle()

        onNodeWithTag("grid_toggle_placement").performClick()
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
    fun grid_misconfiguredSiblingOverRecomposesAndDejavuCountsIt() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        setTrackedContent { GridMisconfiguredScreen() }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        // The misconfigured sibling reads the SAME state as the incremented cell, so each
        // increment recomposes both. After N increments the sibling over-recomposed N times
        // (vs 0 in the well-configured GridRegressionScreen).
        val n = 4
        repeat(n) {
            onNodeWithTag("grid_increment_0").performClick()
            waitForIdle()
        }

        // PRIMARY accuracy: Dejavu reports the exact inflated count for the over-recomposing sibling.
        onNodeWithTag("grid_cell_0").assertRecompositions(exactly = GroundTruth.delta("grid_cell_0"))
        onNodeWithTag("grid_cell_1").assertRecompositions(exactly = GroundTruth.delta("grid_cell_1"))
        // SECONDARY: the misconfiguration over-recomposed the sibling once per increment.
        assertEquals(n, GroundTruth.delta("grid_cell_1"), "misconfigured sibling should over-recompose N times")
        assertTrue(GroundTruth.delta("grid_cell_0") >= 1, "changed cell should recompose")
    }

    @Test
    fun grid_overBudgetAssertionThrowsAndIsCaught() = runRecompositionTrackingUiTest {
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

        // The misconfigured sibling over-recomposed, so a too-strict assertion MUST throw.
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

    // ── FlexBox ───────────────────────────────────────────────────────

    @Test
    fun flexBox_changedItemRecomposesAndSiblingStaysStable() = runRecompositionTrackingUiTest {
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
    fun flexBox_tagMappingSurvivesConfigChange() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        setTrackedContent { FlexRegressionScreen() }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("flex_toggle_config").performClick()
        waitForIdle()

        // Self-validating: whatever the config change does to each item, Dejavu must count it
        // exactly. (Previously this asserted atLeast = 0, which is vacuously true.)
        onNodeWithTag("flex_item_0").assertRecompositions(exactly = GroundTruth.delta("flex_item_0"))
        onNodeWithTag("flex_item_1").assertRecompositions(exactly = GroundTruth.delta("flex_item_1"))
    }

    @Test
    fun flexBox_misconfiguredSiblingOverRecomposesAndDejavuCountsIt() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        setTrackedContent { FlexMisconfiguredScreen() }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        val n = 4
        repeat(n) {
            onNodeWithTag("flex_increment_0").performClick()
            waitForIdle()
        }

        onNodeWithTag("flex_item_0").assertRecompositions(exactly = GroundTruth.delta("flex_item_0"))
        onNodeWithTag("flex_item_1").assertRecompositions(exactly = GroundTruth.delta("flex_item_1"))
        assertEquals(n, GroundTruth.delta("flex_item_1"), "misconfigured sibling should over-recompose N times")
        assertTrue(GroundTruth.delta("flex_item_0") >= 1, "changed item should recompose")
    }

    @Test
    fun flexBox_overBudgetAssertionThrowsAndIsCaught() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        setTrackedContent { FlexMisconfiguredScreen() }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        val n = 4
        repeat(n) {
            onNodeWithTag("flex_increment_0").performClick()
            waitForIdle()
        }

        // The misconfigured sibling over-recomposed, so a too-strict assertion MUST throw.
        val error = assertFailsWith<AssertionError> {
            onNodeWithTag("flex_item_1").assertStable()
        }
        assertTrue(
            error.message != null && error.message!!.isNotBlank(),
            "thrown error should carry a descriptive message",
        )
        // The CORRECT (exact) assertion on the same node still passes — caught & handled as intended.
        onNodeWithTag("flex_item_1").assertRecompositions(exactly = GroundTruth.delta("flex_item_1"))
    }

    // ── mediaQuery ────────────────────────────────────────────────────

    @Test
    fun mediaQuery_crossingBreakpointRecomposesDependentNodeAndSiblingStaysStable() =
        runRecompositionTrackingUiTest {
            // Drive UiMediaScope.windowWidth via LocalUiMediaScope. The desktop test backend
            // doesn't implement DeviceConfigurationOverride.WindowSize, so we provide the scope
            // directly. LocalUiMediaScope is a *static* CompositionLocal, so we keep a single
            // stable scope instance whose windowWidth reads a MutableState — changing the state
            // (rather than re-providing a new scope) keeps the provider subtree from being
            // blanket-invalidated, letting derivedMediaQuery's derivedStateOf do the tracking.
            GroundTruth.clear()
            val widthState = mutableStateOf(400.dp)
            val scope = TestUiMediaScope(widthState)
            setTrackedContent {
                CompositionLocalProvider(LocalUiMediaScope provides scope) {
                    MediaQueryRegressionScreen()
                }
            }
            waitForIdle()
            resetRecompositionCounts()
            GroundTruth.snapshotBaseline()

            // 400.dp -> 800.dp width crosses the >= 600.dp breakpoint.
            widthState.value = 800.dp
            waitForIdle()

            onNodeWithTag("media_responsive")
                .assertRecompositions(exactly = GroundTruth.delta("media_responsive"))
            onNodeWithTag("media_independent")
                .assertRecompositions(exactly = GroundTruth.delta("media_independent"))
            assertTrue(GroundTruth.delta("media_responsive") >= 1, "responsive node should recompose on breakpoint cross")
            assertEquals(0, GroundTruth.delta("media_independent"), "independent node should stay stable")
        }

    @Test
    fun mediaQuery_subBreakpointResizeLeavesDependentNodeStable() =
        runRecompositionTrackingUiTest {
            GroundTruth.clear()
            val widthState = mutableStateOf(400.dp)
            val scope = TestUiMediaScope(widthState)
            setTrackedContent {
                CompositionLocalProvider(LocalUiMediaScope provides scope) {
                    MediaQueryRegressionScreen()
                }
            }
            waitForIdle()
            resetRecompositionCounts()
            GroundTruth.snapshotBaseline()

            // 400.dp -> 500.dp width: both sides stay below the 600.dp breakpoint, so
            // derivedMediaQuery's State<Boolean> value does not change. The node that
            // only reads that derived boolean should not recompose.
            widthState.value = 500.dp
            waitForIdle()

            onNodeWithTag("media_responsive")
                .assertRecompositions(exactly = GroundTruth.delta("media_responsive"))
            onNodeWithTag("media_independent")
                .assertRecompositions(exactly = GroundTruth.delta("media_independent"))
            assertEquals(0, GroundTruth.delta("media_responsive"), "derivedMediaQuery node should stay stable sub-breakpoint")
            assertEquals(0, GroundTruth.delta("media_independent"), "independent node should stay stable")
        }

    @Test
    fun mediaQuery_misconfiguredDirectReadOverRecomposesAndDejavuCountsIt() =
        runRecompositionTrackingUiTest {
            // Misconfiguration: the responsive node reads the @Composable mediaQuery { ... }
            // Boolean DIRECTLY instead of derivedMediaQuery's State<Boolean>. mediaQuery reads
            // windowWidth during composition, so every width change recomposes the node — even
            // sub-breakpoint changes that derivedMediaQuery would absorb.
            GroundTruth.clear()
            val widthState = mutableStateOf(400.dp)
            val scope = TestUiMediaScope(widthState)
            setTrackedContent {
                CompositionLocalProvider(LocalUiMediaScope provides scope) {
                    MediaQueryMisconfiguredScreen()
                }
            }
            waitForIdle()
            resetRecompositionCounts()
            GroundTruth.snapshotBaseline()

            // Sub-breakpoint resize (400 -> 500, both < 600). derivedMediaQuery stays stable here
            // (see mediaQuery_subBreakpointResizeLeavesDependentNodeStable); the direct read does not.
            widthState.value = 500.dp
            waitForIdle()

            onNodeWithTag("media_responsive")
                .assertRecompositions(exactly = GroundTruth.delta("media_responsive"))
            onNodeWithTag("media_independent")
                .assertRecompositions(exactly = GroundTruth.delta("media_independent"))
            assertTrue(
                GroundTruth.delta("media_responsive") >= 1,
                "direct mediaQuery read should over-recompose on sub-breakpoint resize " +
                    "(actual delta = ${GroundTruth.delta("media_responsive")})",
            )
            assertEquals(0, GroundTruth.delta("media_independent"), "independent node should stay stable")
        }

    @Test
    fun mediaQuery_overBudgetAssertionThrowsAndIsCaught() =
        runRecompositionTrackingUiTest {
            // The direct-mediaQuery node over-recomposes on a SUB-breakpoint resize (400 -> 500,
            // both < 600) — derivedMediaQuery would absorb this, the direct read does not.
            GroundTruth.clear()
            val widthState = mutableStateOf(400.dp)
            val scope = TestUiMediaScope(widthState)
            setTrackedContent {
                CompositionLocalProvider(LocalUiMediaScope provides scope) {
                    MediaQueryMisconfiguredScreen()
                }
            }
            waitForIdle()
            resetRecompositionCounts()
            GroundTruth.snapshotBaseline()

            widthState.value = 500.dp
            waitForIdle()

            // The responsive node over-recomposed sub-breakpoint, so a too-strict assertion MUST throw.
            val error = assertFailsWith<AssertionError> {
                onNodeWithTag("media_responsive").assertStable()
            }
            assertTrue(
                error.message != null && error.message!!.isNotBlank(),
                "thrown error should carry a descriptive message",
            )
            // The CORRECT (exact) assertion on the same node still passes — caught & handled as intended.
            onNodeWithTag("media_responsive")
                .assertRecompositions(exactly = GroundTruth.delta("media_responsive"))
        }

    // ── Styles ────────────────────────────────────────────────────────

    @Test
    fun style_pressedStateRecomposesStyledNodeAndSiblingStaysStable() =
        runRecompositionTrackingUiTest {
            GroundTruth.clear()
            setTrackedContent { StyleRegressionScreen() }
            waitForIdle()
            resetRecompositionCounts()
            GroundTruth.snapshotBaseline()

            onNodeWithTag("style_toggle").performClick()
            waitForIdle()

            onNodeWithTag("style_target").assertRecompositions(exactly = GroundTruth.delta("style_target"))
            onNodeWithTag("style_sibling").assertRecompositions(exactly = GroundTruth.delta("style_sibling"))
            assertTrue(GroundTruth.delta("style_target") >= 1, "styled target should recompose")
            assertEquals(0, GroundTruth.delta("style_sibling"), "style sibling should stay stable")
        }

    @Test
    fun style_misconfiguredSiblingOverRecomposesAndDejavuCountsIt() =
        runRecompositionTrackingUiTest {
            // Misconfiguration: the sibling reads the same `highlighted` state as the styled
            // target, so toggling recomposes it too (vs staying stable in StyleRegressionScreen).
            GroundTruth.clear()
            setTrackedContent { StyleMisconfiguredScreen() }
            waitForIdle()
            resetRecompositionCounts()
            GroundTruth.snapshotBaseline()

            val n = 3
            repeat(n) {
                onNodeWithTag("style_toggle").performClick()
                waitForIdle()
            }

            onNodeWithTag("style_target").assertRecompositions(exactly = GroundTruth.delta("style_target"))
            onNodeWithTag("style_sibling").assertRecompositions(exactly = GroundTruth.delta("style_sibling"))
            assertEquals(n, GroundTruth.delta("style_sibling"), "misconfigured sibling should over-recompose N times")
            assertTrue(GroundTruth.delta("style_target") >= 1, "styled target should recompose")
        }

    @Test
    fun style_overBudgetAssertionThrowsAndIsCaught() =
        runRecompositionTrackingUiTest {
            GroundTruth.clear()
            setTrackedContent { StyleMisconfiguredScreen() }
            waitForIdle()
            resetRecompositionCounts()
            GroundTruth.snapshotBaseline()

            val n = 3
            repeat(n) {
                onNodeWithTag("style_toggle").performClick()
                waitForIdle()
            }

            // The misconfigured sibling over-recomposed, so a too-strict assertion MUST throw.
            val error = assertFailsWith<AssertionError> {
                onNodeWithTag("style_sibling").assertStable()
            }
            assertTrue(
                error.message != null && error.message!!.isNotBlank(),
                "thrown error should carry a descriptive message",
            )
            // The CORRECT (exact) assertion on the same node still passes — caught & handled as intended.
            onNodeWithTag("style_sibling")
                .assertRecompositions(exactly = GroundTruth.delta("style_sibling"))
        }
}

// ── Grid screens ──────────────────────────────────────────────────────

@Composable
internal fun GridRegressionScreen() {
    var shifted by remember { mutableStateOf(false) }
    var cell0Count by remember { mutableIntStateOf(0) }

    Column {
        Grid(
            config = {
                columns(
                    GridTrackSize.Fixed(72.dp),
                    GridTrackSize.Fixed(72.dp),
                )
                rows(
                    GridTrackSize.Fixed(36.dp),
                    GridTrackSize.Fixed(36.dp),
                )
            },
            modifier = Modifier.width(160.dp),
        ) {
            GridCell0(count = cell0Count, row = if (shifted) 1 else 0, column = 0)
            GridCell1(row = 0, column = if (shifted) 0 else 1)
            GridCell2(row = 1, column = 1)
        }
        BasicText(
            text = "Increment grid cell 0",
            modifier = Modifier
                .testTag("grid_increment_0")
                .clickable { cell0Count++ },
        )
        BasicText(
            text = "Toggle placement",
            modifier = Modifier
                .testTag("grid_toggle_placement")
                .clickable { shifted = !shifted },
        )
    }
}

/**
 * Misconfigured Grid: the sibling cell reads the SAME `cell0Count` state as cell 0, so each
 * increment recomposes both cells. The well-configured [GridRegressionScreen] keeps the sibling
 * stable. Used to prove Dejavu reports the exact inflated sibling count.
 */
@Composable
internal fun GridMisconfiguredScreen() {
    var cell0Count by remember { mutableIntStateOf(0) }

    Column {
        Grid(
            config = {
                columns(
                    GridTrackSize.Fixed(72.dp),
                    GridTrackSize.Fixed(72.dp),
                )
                rows(
                    GridTrackSize.Fixed(36.dp),
                    GridTrackSize.Fixed(36.dp),
                )
            },
            modifier = Modifier.width(160.dp),
        ) {
            GridCell0(count = cell0Count, row = 0, column = 0)
            // Misconfiguration: sibling also reads cell0Count.
            GridCell1Reading(count = cell0Count, row = 0, column = 1)
            GridCell2(row = 1, column = 1)
        }
        BasicText(
            text = "Increment grid cell 0",
            modifier = Modifier
                .testTag("grid_increment_0")
                .clickable { cell0Count++ },
        )
    }
}

// ── FlexBox screens ───────────────────────────────────────────────────

@Composable
internal fun FlexRegressionScreen() {
    var vertical by remember { mutableStateOf(false) }
    var item0Count by remember { mutableIntStateOf(0) }

    Column {
        FlexBox(
            modifier = Modifier.width(180.dp),
            config = FlexBoxConfig {
                direction(if (vertical) FlexDirection.Column else FlexDirection.Row)
                wrap(FlexWrap.Wrap)
                gap(4.dp)
            },
        ) {
            FlexItem0(count = item0Count)
            FlexItem1()
            FlexItem2()
        }
        BasicText(
            text = "Increment flex item 0",
            modifier = Modifier
                .testTag("flex_increment_0")
                .clickable { item0Count++ },
        )
        BasicText(
            text = "Toggle flex",
            modifier = Modifier
                .testTag("flex_toggle_config")
                .clickable { vertical = !vertical },
        )
    }
}

/**
 * Misconfigured FlexBox: the sibling item reads the SAME `item0Count` state as item 0, so each
 * increment recomposes both. The well-configured [FlexRegressionScreen] keeps the sibling stable.
 */
@Composable
internal fun FlexMisconfiguredScreen() {
    var item0Count by remember { mutableIntStateOf(0) }

    Column {
        FlexBox(
            modifier = Modifier.width(180.dp),
            config = FlexBoxConfig {
                direction(FlexDirection.Row)
                wrap(FlexWrap.Wrap)
                gap(4.dp)
            },
        ) {
            FlexItem0(count = item0Count)
            // Misconfiguration: sibling also reads item0Count.
            FlexItem1Reading(count = item0Count)
            FlexItem2()
        }
        BasicText(
            text = "Increment flex item 0",
            modifier = Modifier
                .testTag("flex_increment_0")
                .clickable { item0Count++ },
        )
    }
}

// ── Grid cells ────────────────────────────────────────────────────────

@Composable
private fun GridScope.GridCell0(count: Int, row: Int, column: Int) {
    SideEffect { GroundTruth.record("grid_cell_0") }
    BasicText(
        text = "grid_cell_0:$count",
        modifier = Modifier
            .gridItem(row = row, column = column)
            .testTag("grid_cell_0"),
    )
}

@Composable
private fun GridScope.GridCell1(row: Int, column: Int) {
    SideEffect { GroundTruth.record("grid_cell_1") }
    BasicText(
        text = "grid_cell_1",
        modifier = Modifier
            .gridItem(row = row, column = column)
            .testTag("grid_cell_1"),
    )
}

/** Sibling that reads [count] — used by [GridMisconfiguredScreen] to force over-recomposition. */
@Composable
private fun GridScope.GridCell1Reading(count: Int, row: Int, column: Int) {
    SideEffect { GroundTruth.record("grid_cell_1") }
    BasicText(
        text = "grid_cell_1:$count",
        modifier = Modifier
            .gridItem(row = row, column = column)
            .testTag("grid_cell_1"),
    )
}

@Composable
private fun GridScope.GridCell2(row: Int, column: Int) {
    SideEffect { GroundTruth.record("grid_cell_2") }
    BasicText(
        text = "grid_cell_2",
        modifier = Modifier
            .gridItem(row = row, column = column)
            .testTag("grid_cell_2"),
    )
}

// ── Flex items ────────────────────────────────────────────────────────

@Composable
private fun FlexBoxScope.FlexItem0(count: Int) {
    SideEffect { GroundTruth.record("flex_item_0") }
    BasicText(
        text = "flex_item_0:$count",
        modifier = Modifier
            .flex { grow(1f) }
            .testTag("flex_item_0"),
    )
}

@Composable
private fun FlexBoxScope.FlexItem1() {
    SideEffect { GroundTruth.record("flex_item_1") }
    BasicText(
        text = "flex_item_1",
        modifier = Modifier
            .flex { grow(1f) }
            .testTag("flex_item_1"),
    )
}

/** Sibling that reads [count] — used by [FlexMisconfiguredScreen] to force over-recomposition. */
@Composable
private fun FlexBoxScope.FlexItem1Reading(count: Int) {
    SideEffect { GroundTruth.record("flex_item_1") }
    BasicText(
        text = "flex_item_1:$count",
        modifier = Modifier
            .flex { grow(1f) }
            .testTag("flex_item_1"),
    )
}

@Composable
private fun FlexBoxScope.FlexItem2() {
    SideEffect { GroundTruth.record("flex_item_2") }
    BasicText(
        text = "flex_item_2",
        modifier = Modifier
            .flex { grow(1f) }
            .testTag("flex_item_2"),
    )
}

// ── mediaQuery screens / nodes ────────────────────────────────────────

@Composable
internal fun MediaQueryRegressionScreen() {
    Column {
        ResponsiveNode()
        IndependentNode()
    }
}

/**
 * Misconfigured media-query screen: the responsive node reads the raw `mediaQuery { ... }` Boolean
 * directly instead of the derived [State] from [derivedMediaQuery]. See [MisconfiguredResponsiveNode].
 */
@Composable
internal fun MediaQueryMisconfiguredScreen() {
    Column {
        MisconfiguredResponsiveNode()
        IndependentNode()
    }
}

/**
 * Minimal [UiMediaScope] used to drive [derivedMediaQuery] in tests. A single stable instance
 * is provided to the static [LocalUiMediaScope]; [windowWidth] is backed by a [State] so that
 * width changes are observed by derivedMediaQuery's internal `derivedStateOf` without
 * blanket-invalidating the provider subtree. Everything else is a fixed sensible default.
 */
private class TestUiMediaScope(private val widthState: State<Dp>) : UiMediaScope {
    override val windowWidth: Dp get() = widthState.value
    override val windowHeight: Dp = 800.dp
    override val windowPosture: UiMediaScope.Posture = UiMediaScope.Posture.Flat
    override val pointerPrecision: UiMediaScope.PointerPrecision = UiMediaScope.PointerPrecision.Fine
    override val keyboardKind: UiMediaScope.KeyboardKind = UiMediaScope.KeyboardKind.Physical
    override val hasMicrophone: Boolean = false
    override val hasCamera: Boolean = false
    override val viewingDistance: UiMediaScope.ViewingDistance = UiMediaScope.ViewingDistance.Near
}

@Composable
private fun ResponsiveNode() {
    // Reading derivedMediaQuery here means only this node recomposes when the breakpoint
    // boolean flips. Sub-breakpoint window changes leave the derived value (and this node)
    // unchanged.
    SideEffect { GroundTruth.record("media_responsive") }
    val isWide by derivedMediaQuery { windowWidth >= 600.dp }
    BasicText(
        text = if (isWide) "wide" else "narrow",
        modifier = Modifier.testTag("media_responsive"),
    )
}

/**
 * Misconfigured responsive node: reads the @Composable [mediaQuery] Boolean directly. [mediaQuery]
 * reads `windowWidth` during composition, so any width change recomposes this node — including
 * sub-breakpoint changes that [derivedMediaQuery] absorbs.
 */
@Composable
private fun MisconfiguredResponsiveNode() {
    SideEffect { GroundTruth.record("media_responsive") }
    val isWide = mediaQuery { windowWidth >= 600.dp }
    BasicText(
        text = if (isWide) "wide" else "narrow",
        modifier = Modifier.testTag("media_responsive"),
    )
}

@Composable
private fun IndependentNode() {
    SideEffect { GroundTruth.record("media_independent") }
    BasicText(
        text = "independent",
        modifier = Modifier.testTag("media_independent"),
    )
}

// ── Style screens / nodes ─────────────────────────────────────────────

@Composable
internal fun StyleRegressionScreen() {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = remember(interactionSource) { MutableStyleState(interactionSource) }
    var highlighted by remember { mutableStateOf(false) }

    // The pressed() block is interaction-driven (no host recomposition needed); we additionally
    // read `highlighted` to build the base style, so flipping that state swaps the Style object
    // passed to styleable() and recomposes the styled node — while the sibling stays stable.
    val style = remember(highlighted) {
        val baseColor = if (highlighted) Color.Green else Color.Blue
        object : Style {
            override fun StyleScope.applyStyle() {
                background(baseColor)
                dejavuPressedStyle()
            }
        }
    }

    Column {
        StyledTarget(styleState = styleState, style = style)
        StyleSibling()
        BasicText(
            text = "Toggle style",
            modifier = Modifier
                .testTag("style_toggle")
                .clickable { highlighted = !highlighted },
        )
    }
}

/**
 * Misconfigured style screen: the sibling reads the same `highlighted` state as the styled target,
 * so each toggle recomposes it too. The well-configured [StyleRegressionScreen] keeps it stable.
 */
@Composable
internal fun StyleMisconfiguredScreen() {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = remember(interactionSource) { MutableStyleState(interactionSource) }
    var highlighted by remember { mutableStateOf(false) }

    val style = remember(highlighted) {
        val baseColor = if (highlighted) Color.Green else Color.Blue
        object : Style {
            override fun StyleScope.applyStyle() {
                background(baseColor)
                dejavuPressedStyle()
            }
        }
    }

    Column {
        StyledTarget(styleState = styleState, style = style)
        // Misconfiguration: sibling reads the same highlighted state.
        StyleSiblingReading(highlighted = highlighted)
        BasicText(
            text = "Toggle style",
            modifier = Modifier
                .testTag("style_toggle")
                .clickable { highlighted = !highlighted },
        )
    }
}

@Composable
private fun StyledTarget(styleState: MutableStyleState, style: Style) {
    SideEffect { GroundTruth.record("style_target") }
    BasicText(
        text = "style_target",
        modifier = Modifier
            .testTag("style_target")
            .styleable(styleState, style),
    )
}

@Composable
private fun StyleSibling() {
    SideEffect { GroundTruth.record("style_sibling") }
    BasicText(
        text = "style_sibling",
        modifier = Modifier.testTag("style_sibling"),
    )
}

/** Sibling that reads [highlighted] — used by [StyleMisconfiguredScreen] to force over-recomposition. */
@Composable
private fun StyleSiblingReading(highlighted: Boolean) {
    SideEffect { GroundTruth.record("style_sibling") }
    BasicText(
        text = if (highlighted) "style_sibling:on" else "style_sibling:off",
        modifier = Modifier.testTag("style_sibling"),
    )
}
