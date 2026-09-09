package dejavu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Advanced patterns: nested `CompositionLocalProvider` scoping, a custom-layout-style static child,
 * `remember(key)` recomputation, and `LaunchedEffect` restart on key change — verifying Dejavu's
 * recomposition counts against a [GroundTruth] `SideEffect` (the runtime runs the effect after every
 * successful composition, so it is the real composition count).
 *
 * Every tracked composable here is **single-instance** — each appears exactly once from a distinct
 * call site in [AdvancedScreen], so the public per-tag API resolves its exact count on all platforms.
 * Each test therefore asserts `exactly = GroundTruth.delta(tag)` (or `assertStable()` for the stable
 * cases), proving the tracer count equals the runtime's real recomposition count — not merely a
 * direction (`atLeast`/`atMost`).
 *
 * Every test calls [resetRecompositionCounts] + [GroundTruth.snapshotBaseline] after the initial
 * `waitForIdle()` so `delta` is aligned with Dejavu's zero point.
 */
@OptIn(ExperimentalTestApi::class)
class AdvancedPatternTest {

    @BeforeTest
    fun setUp() {
        enableDejavuForTest()
        GroundTruth.clear()
    }

    @AfterTest
    fun tearDown() = disableDejavuForTest()

    @Test
    fun nestedLocal_changeOuter_outerReadersRecompose() = runComposeUiTest {
        setContent { DejavuTestContent { AdvancedScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("change_outer_btn").performClick()
        waitForIdle()

        // Changing the outer local re-provides LocalValue, so both outer readers recompose once each.
        onNodeWithTag("outer_reader")
            .assertRecompositions(exactly = GroundTruth.delta("outer_reader"))
        onNodeWithTag("outer_reader_b")
            .assertRecompositions(exactly = GroundTruth.delta("outer_reader_b"))
        assertEquals(1, GroundTruth.delta("outer_reader"), "outer reader recomposes once when outer local changes")
        assertEquals(1, GroundTruth.delta("outer_reader_b"), "outer reader B recomposes once when outer local changes")
    }

    @Test
    fun nestedLocal_changeInner_innerReaderRecomposes() = runComposeUiTest {
        setContent { DejavuTestContent { AdvancedScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("change_inner_btn").performClick()
        waitForIdle()

        // Changing the inner local re-provides the inner LocalValue, recomposing the inner reader once.
        onNodeWithTag("inner_reader")
            .assertRecompositions(exactly = GroundTruth.delta("inner_reader"))
        assertEquals(1, GroundTruth.delta("inner_reader"), "inner reader recomposes once when inner local changes")
    }

    @Test
    fun nestedLocal_changeInner_outerReadersStable() = runComposeUiTest {
        setContent { DejavuTestContent { AdvancedScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("change_inner_btn").performClick()
        waitForIdle()

        // The inner local is scoped below the outer readers, so they must not recompose.
        onNodeWithTag("outer_reader").assertStable()
        onNodeWithTag("outer_reader_b").assertStable()
        assertEquals(0, GroundTruth.delta("outer_reader"), "outer reader stable when only inner local changes")
        assertEquals(0, GroundTruth.delta("outer_reader_b"), "outer reader B stable when only inner local changes")
    }

    @Test
    fun customLayout_childStableWhenNoStateChanges() = runComposeUiTest {
        setContent { DejavuTestContent { AdvancedScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("change_inner_btn").performClick()
        waitForIdle()

        // The static child reads no changing state, so an unrelated inner-local change leaves it stable.
        onNodeWithTag("custom_layout_child").assertStable()
        assertEquals(0, GroundTruth.delta("custom_layout_child"), "static child stable across unrelated change")
    }

    @Test
    fun rememberKey_changeKey_childRecomposes() = runComposeUiTest {
        setContent { DejavuTestContent { AdvancedScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("change_key_btn_adv").performClick()
        waitForIdle()

        // The keyValue param changes, recomposing the child once (and recomputing its remember(key)).
        onNodeWithTag("remember_key_child")
            .assertRecompositions(exactly = GroundTruth.delta("remember_key_child"))
        assertEquals(1, GroundTruth.delta("remember_key_child"), "remember-key child recomposes once on key change")
    }

    @Test
    fun effectRestart_changeEffectKey_childRecomposes() = runComposeUiTest {
        setContent { DejavuTestContent { AdvancedScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("change_effect_key_btn").performClick()
        waitForIdle()

        // The effectKey param change recomposes the child; assert the tracer matches the runtime's
        // real recomposition count for this node (the LaunchedEffect restart settles under waitForIdle).
        onNodeWithTag("effect_restart")
            .assertRecompositions(exactly = GroundTruth.delta("effect_restart"))
        assertEquals(1, GroundTruth.delta("effect_restart"), "effect-key child recomposes once on key change")
    }
}

// ── Composables ──────────────────────────────────────────────────

private val LocalValue = compositionLocalOf { "default" }

@Composable
private fun AdvancedScreen() {
    var outerValue by remember { mutableIntStateOf(0) }
    var innerValue by remember { mutableIntStateOf(0) }
    var rememberKey by remember { mutableIntStateOf(0) }
    var effectKey by remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalValue provides "outer-$outerValue") {
        Column {
            OuterReader()
            OuterReaderB()
            CompositionLocalProvider(LocalValue provides "inner-$innerValue") {
                InnerReader()
            }
            CustomLayoutChild()
            RememberKeyChild(rememberKey)
            EffectRestartChild(effectKey)
            BasicText("ChangeOuter", Modifier.testTag("change_outer_btn").clickable { outerValue++ })
            BasicText("ChangeInner", Modifier.testTag("change_inner_btn").clickable { innerValue++ })
            BasicText("ChangeKey", Modifier.testTag("change_key_btn_adv").clickable { rememberKey++ })
            BasicText("ChangeEffectKey", Modifier.testTag("change_effect_key_btn").clickable { effectKey++ })
        }
    }
}

@Composable
private fun OuterReader() {
    SideEffect { GroundTruth.record("outer_reader") }
    val v = LocalValue.current
    BasicText("Outer: $v", Modifier.testTag("outer_reader"))
}

@Composable
private fun OuterReaderB() {
    SideEffect { GroundTruth.record("outer_reader_b") }
    val v = LocalValue.current
    BasicText("OuterB: $v", Modifier.testTag("outer_reader_b"))
}

@Composable
private fun InnerReader() {
    SideEffect { GroundTruth.record("inner_reader") }
    val v = LocalValue.current
    BasicText("Inner: $v", Modifier.testTag("inner_reader"))
}

@Composable
private fun CustomLayoutChild() {
    SideEffect { GroundTruth.record("custom_layout_child") }
    BasicText("Custom", Modifier.testTag("custom_layout_child"))
}

@Composable
private fun RememberKeyChild(keyValue: Int) {
    SideEffect { GroundTruth.record("remember_key_child") }
    val computed = remember(keyValue) { "computed-$keyValue" }
    BasicText("Remember: $computed", Modifier.testTag("remember_key_child"))
}

@Composable
private fun EffectRestartChild(effectKey: Int) {
    SideEffect { GroundTruth.record("effect_restart") }
    var effectRan by remember { mutableStateOf(false) }
    LaunchedEffect(effectKey) { effectRan = true }
    BasicText("Effect: $effectKey, ran=$effectRan", Modifier.testTag("effect_restart"))
}
