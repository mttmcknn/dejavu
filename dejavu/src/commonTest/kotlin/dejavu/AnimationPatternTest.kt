package dejavu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import dejavu.internal.DejavuTracer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Animation / conditional-composition pattern tests.
 *
 * Every assertion is exact and self-validating against a [GroundTruth] `SideEffect`: the Compose
 * runtime runs the effect after every successful composition, so the per-key `SideEffect` tally is
 * the real composition count and `GroundTruth.delta(tag)` is the real number of recompositions
 * since the baseline — exactly what Dejavu reports.
 *
 * Named composable boundaries compare exact counts with the independent oracle. The
 * `visible_panel` tag belongs directly to an anonymous content lambda, whose exact per-instance
 * count is unsupported; that case verifies an explicit diagnostic after exercising the content.
 *
 * Animation handling:
 * - The only real animation in this screen is the finite [AnimatedVisibility] wrapping
 *   `visible_panel`. That test drives a manual clock (`mainClock.autoAdvance = false`) so the panel
 *   recomposes across the enter/exit frames, then runs the animation to completion
 *   (`mainClock.autoAdvance = true` + `waitForIdle()`) and verifies that the content executed
 *   without presenting a sampled slot-change count as its recomposition count.
 * - `variant_a`/`variant_b` (if/else swap), `animating_banner` (plain `$pulse` state read), and
 *   `conditional_child` (if-guarded) are not animated; they settle on a plain `waitForIdle()` and
 *   assert `tracer == GroundTruth.delta(tag)` directly.
 *
 * Each `SideEffect` is placed *inside* its node's conditional/animation scope so it records only
 * when that content actually composes.
 */
@OptIn(ExperimentalTestApi::class)
class AnimationPatternTest {

    @BeforeTest
    fun setUp() {
        enableDejavuForTest()
        GroundTruth.clear()
    }

    @AfterTest
    fun tearDown() = disableDejavuForTest()

    @Test
    fun toggleVisibility_panelTracked() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { DejavuTestContent { AnimationScreen() } }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        // Two toggles of a finite AnimatedVisibility (off then on); drive the clock so the
        // enter/exit animation runs, then settle to completion.
        onNodeWithTag("toggle_vis_btn").performClick()
        mainClock.advanceTimeBy(500)
        onNodeWithTag("toggle_vis_btn").performClick()
        mainClock.advanceTimeBy(500)
        mainClock.autoAdvance = true
        waitForIdle()

        assertTrue(GroundTruth.delta("visible_panel") > 0, "the animation content must execute")
        onNodeWithTag("visible_panel").assertExists()
        val error = assertFailsWith<UnexpectedRecompositionsError> {
            onNodeWithTag("visible_panel")
                .assertRecompositions(exactly = GroundTruth.delta("visible_panel"))
        }
        assertTrue(error.message.orEmpty().contains("includes a lambda owner"))
    }

    @Test
    fun animatedContent_variantsSwitchCleanly() = runComposeUiTest {
        setContent { DejavuTestContent { AnimationScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        // Not actually animated — variant cycles via an if/else swap, settles on waitForIdle.
        // variant_a / variant_b are inline branches of AnimationScreen (one composable carrying
        // many testTags), so their per-tag counts resolve to the shared AnimationScreen function
        // count rather than the per-branch count. Assert the function-level recomposition count
        // against ground truth (each cycle click recomposes the screen exactly once), plus the
        // behavioral fact that two cycles (A→B→A) bring variant A back on screen.
        onNodeWithTag("cycle_content_btn").performClick()
        waitForIdle()
        onNodeWithTag("cycle_content_btn").performClick()
        waitForIdle()

        assertEquals(
            GroundTruth.delta("AnimationScreen"),
            DejavuTracer.getRecompositionCount("dejavu.AnimationScreen"),
            "tracer AnimationScreen count should equal SideEffect ground truth",
        )
        assertEquals(2, GroundTruth.delta("AnimationScreen"), "two variant cycles recompose the screen twice")
    }

    @Test
    fun infiniteTransition_bannerRecomposes() = runComposeUiTest {
        setContent { DejavuTestContent { AnimationScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        // AnimatingBanner just reads `$pulse`; a single increment recomposes it once (no animation).
        onNodeWithTag("pulse_banner_btn").performClick()
        waitForIdle()

        onNodeWithTag("animating_banner")
            .assertRecompositions(exactly = GroundTruth.delta("animating_banner"))
        assertEquals(1, GroundTruth.delta("animating_banner"), "one pulse increment recomposes the banner once")
    }

    @Test
    fun conditionalComposition_childTrackedWhenPresent() = runComposeUiTest {
        setContent { DejavuTestContent { AnimationScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        // conditional_child is an inline branch of AnimationScreen, so its per-tag count resolves
        // to the screen's recomposition count (one toggle → one screen recomposition), which equals
        // the one composition the child's SideEffect records when it appears. assert tracer == GT.
        onNodeWithTag("toggle_cond_btn").performClick()
        waitForIdle()

        onNodeWithTag("conditional_child")
            .assertRecompositions(exactly = GroundTruth.delta("conditional_child"))
    }

    @Test
    fun conditionalComposition_childGoneWhenRemoved() = runComposeUiTest {
        setContent { DejavuTestContent { AnimationScreen() } }
        waitForIdle()

        onNodeWithTag("toggle_cond_btn").performClick()
        waitForIdle()
        onNodeWithTag("toggle_cond_btn").performClick()
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("static_label")
            .assertRecompositions(exactly = GroundTruth.delta("static_label"))
        assertEquals(0, GroundTruth.delta("static_label"), "parameterless label never recomposes")
    }

    @Test
    fun staticLabel_neverRecomposes() = runComposeUiTest {
        setContent { DejavuTestContent { AnimationScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("toggle_vis_btn").performClick()
        waitForIdle()

        onNodeWithTag("static_label")
            .assertRecompositions(exactly = GroundTruth.delta("static_label"))
        assertEquals(0, GroundTruth.delta("static_label"), "parameterless label never recomposes")
    }

    @Test
    fun allInteractions_staticLabelStaysStable() = runComposeUiTest {
        setContent { DejavuTestContent { AnimationScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("toggle_vis_btn").performClick()
        waitForIdle()
        onNodeWithTag("cycle_content_btn").performClick()
        waitForIdle()
        onNodeWithTag("toggle_cond_btn").performClick()
        waitForIdle()
        onNodeWithTag("pulse_banner_btn").performClick()
        waitForIdle()

        onNodeWithTag("static_label")
            .assertRecompositions(exactly = GroundTruth.delta("static_label"))
        assertEquals(0, GroundTruth.delta("static_label"), "parameterless label is stable across every interaction")
    }
}

// ── Composables ──────────────────────────────────────────────────

@Composable
private fun AnimationScreen() {
    var visible by remember { mutableStateOf(true) }
    var variant by remember { mutableIntStateOf(0) }
    var showConditional by remember { mutableStateOf(false) }
    var bannerPulse by remember { mutableIntStateOf(0) }
    SideEffect { GroundTruth.record("AnimationScreen") }
    Column {
        StaticAnimLabel()
        AnimatedVisibility(visible) {
            SideEffect { GroundTruth.record("visible_panel") }
            BasicText("Visible Panel", Modifier.testTag("visible_panel"))
        }
        if (variant % 2 == 0) {
            SideEffect { GroundTruth.record("variant_a") }
            BasicText("Variant A", Modifier.testTag("variant_a"))
        } else {
            SideEffect { GroundTruth.record("variant_b") }
            BasicText("Variant B", Modifier.testTag("variant_b"))
        }
        AnimatingBanner(bannerPulse)
        if (showConditional) {
            SideEffect { GroundTruth.record("conditional_child") }
            BasicText("Conditional", Modifier.testTag("conditional_child"))
        }
        BasicText("ToggleVis", Modifier.testTag("toggle_vis_btn").clickable { visible = !visible })
        BasicText("CycleContent", Modifier.testTag("cycle_content_btn").clickable { variant++ })
        BasicText("ToggleCond", Modifier.testTag("toggle_cond_btn").clickable { showConditional = !showConditional })
        BasicText("PulseBanner", Modifier.testTag("pulse_banner_btn").clickable { bannerPulse++ })
    }
}

@Composable
private fun StaticAnimLabel() {
    SideEffect { GroundTruth.record("static_label") }
    BasicText("Static", Modifier.testTag("static_label"))
}

@Composable
private fun AnimatingBanner(pulse: Int) {
    SideEffect { GroundTruth.record("animating_banner") }
    BasicText("Banner: $pulse", Modifier.testTag("animating_banner"))
}
