package dejavu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

/**
 * Cross-platform port of Android StarRatingTest.
 * Star rating with Unicode characters. Validates per-star recomposition
 * tracking and stability when rating value does not change.
 *
 * Every assertion is exact and self-validating against a [GroundTruth] `SideEffect`:
 * - **Single-instance** nodes (rating bar/display/root/label) and the three distinct
 *   `SetRatingButton` call sites have unique composer keys, so the public per-tag API
 *   resolves their exact count on all platforms → `exactly = GroundTruth.delta(tag)`.
 * - The five `Star`s are emitted from a keyless `for` loop. Depending on the target's composition
 *   data, the public API can resolve either a per-instance count or the shared function-level
 *   fallback. Ground truth records both levels, so each resolution mode has an independent oracle.
 *   The tests also assert the deterministic number of stars that actually changed. Android
 *   per-instance isolation is covered by the demo `PerTagTrackingRegressionTest`.
 *
 * Every test calls [resetRecompositionCounts] + [GroundTruth.snapshotBaseline] after the initial
 * `waitForIdle()`. The reset zeroes the keyless-loop's initial-composition artifact (5 stars share
 * one composer key, so instances 2..5 first compose as `totalCount > 1`); snapshotting the ground
 * truth at the same point keeps `delta`/tracer aligned.
 */
@OptIn(ExperimentalTestApi::class)
class StarRatingPatternTest {

    @BeforeTest
    fun setUp() {
        enableDejavuForTest()
        GroundTruth.clear()
    }

    @AfterTest
    fun tearDown() = disableDejavuForTest()

    @Test
    fun rating_changedStarsRecomposeExactlyOnce() = runComposeUiTest {
        setContent { DejavuTestContent { RatingBarScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("set_rating_3_btn").performClick()
        waitForIdle()

        // 0 → 3 flips isFilled for stars 0,1,2 (false→true); stars 3,4 are unchanged.
        // Compose targets differ in whether this keyless-loop node resolves per instance or via
        // the function-level fallback. Both paths use an independent SideEffect oracle.
        refreshTagMapping()
        val publicStarCount = if (DejavuTracer.getPerTagRecompositionCount("star_0") != null) {
            GroundTruth.delta("star_0")
        } else {
            GroundTruth.delta("Star")
        }
        onNodeWithTag("star_0")
            .assertRecompositions(exactly = publicStarCount)
        // PRIMARY accuracy: tracer's function-level Star count == real total recompositions.
        assertEquals(
            GroundTruth.delta("Star"),
            DejavuTracer.getRecompositionCount("dejavu.Star"),
            "tracer Star count should equal SideEffect ground truth",
        )
        // SECONDARY behavior: exactly the three changed stars recomposed (once each).
        assertEquals(3, GroundTruth.delta("Star"), "stars 0,1,2 each recompose once on 0→3")
        assertEquals(1, GroundTruth.delta("star_0"), "star 0 recomposes once on 0→3")
    }

    @Test
    fun rating_displayRecomposesOnChange() = runComposeUiTest {
        setContent { DejavuTestContent { RatingBarScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("set_rating_3_btn").performClick()
        waitForIdle()

        onNodeWithTag("rating_display")
            .assertRecompositions(exactly = GroundTruth.delta("rating_display"))
        assertEquals(1, GroundTruth.delta("rating_display"), "display recomposes once on rating change")
    }

    @Test
    fun rating_barRecomposesOnChange() = runComposeUiTest {
        setContent { DejavuTestContent { RatingBarScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("set_rating_3_btn").performClick()
        waitForIdle()

        onNodeWithTag("rating_bar")
            .assertRecompositions(exactly = GroundTruth.delta("rating_bar"))
        assertEquals(1, GroundTruth.delta("rating_bar"), "rating bar recomposes once on rating change")
    }

    @Test
    fun rating_sameValueNoRecomposition() = runComposeUiTest {
        setContent { DejavuTestContent { RatingBarScreen() } }
        waitForIdle()

        onNodeWithTag("set_rating_3_btn").performClick()
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        // Writing the same rating (3 → 3) is a same-value write: no star recomposes.
        onNodeWithTag("set_rating_3_btn").performClick()
        waitForIdle()

        assertEquals(
            GroundTruth.delta("Star"),
            DejavuTracer.getRecompositionCount("dejavu.Star"),
            "tracer Star count should equal SideEffect ground truth",
        )
        assertEquals(0, GroundTruth.delta("Star"), "same-value write must not recompose any star")
    }

    @Test
    fun rating_staticLabelStable() = runComposeUiTest {
        setContent { DejavuTestContent { RatingBarScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("set_rating_1_btn").performClick()
        waitForIdle()
        onNodeWithTag("set_rating_3_btn").performClick()
        waitForIdle()
        onNodeWithTag("set_rating_5_btn").performClick()
        waitForIdle()

        onNodeWithTag("static_rating_label")
            .assertRecompositions(exactly = GroundTruth.delta("static_rating_label"))
        assertEquals(0, GroundTruth.delta("static_rating_label"), "parameterless label never recomposes")
    }

    @Test
    fun rating_buttonsRecomposeExactlyOnceForChangedRatingInput() = runComposeUiTest {
        setContent { DejavuTestContent { RatingBarScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("set_rating_3_btn").performClick()
        waitForIdle()

        // Every button receives the changed rating input. Its three call sites have unique
        // composer keys, so per-tag counts resolve exactly on every platform.
        onNodeWithTag("set_rating_1_btn")
            .assertRecompositions(exactly = GroundTruth.delta("set_rating_1_btn"))
        onNodeWithTag("set_rating_5_btn")
            .assertRecompositions(exactly = GroundTruth.delta("set_rating_5_btn"))
        assertEquals(1, GroundTruth.delta("set_rating_1_btn"), "button recomposes once for the changed rating")
        assertEquals(1, GroundTruth.delta("set_rating_5_btn"), "button recomposes once for the changed rating")
    }

    // ── Per-tag tracking regression tests (port of Android PerTagTrackingRegressionTest) ──
    //
    // Keyless multi-instance composables can resolve per instance or fall back to the shared
    // function-level count depending on the target's composition data. These tests assert the
    // function-level Star count exactly (tracer == ground truth) plus the deterministic number of
    // changed stars. Per-instance isolation is verified through the public API above and in the
    // Android demo PerTagTrackingRegressionTest.

    /**
     * Rating 0→3 after a reset: stars 0-2 change (isFilled false→true). Verify the tracer
     * counts exactly those three recompositions.
     */
    @Test
    fun multiInstance_changedInstancesDetected() = runComposeUiTest {
        setContent { DejavuTestContent { RatingBarScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("set_rating_3_btn").performClick()
        waitForIdle()

        assertEquals(
            GroundTruth.delta("Star"),
            DejavuTracer.getRecompositionCount("dejavu.Star"),
            "tracer Star count should equal SideEffect ground truth",
        )
        assertEquals(3, GroundTruth.delta("Star"), "stars 0,1,2 each recompose once on 0→3")
    }

    /**
     * Rating 0→3, reset, then 3→5: stars 3-4 change from unfilled to filled.
     * Verify exactly those two recompositions are counted across a reset boundary.
     */
    @Test
    fun perTagDetection_afterReset_changedInstancesDetected() = runComposeUiTest {
        setContent { DejavuTestContent { RatingBarScreen() } }
        waitForIdle()

        // Establish initial state: rating = 3
        onNodeWithTag("set_rating_3_btn").performClick()
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        // Change rating 3→5: stars 3-4 become filled
        onNodeWithTag("set_rating_5_btn").performClick()
        waitForIdle()

        assertEquals(
            GroundTruth.delta("Star"),
            DejavuTracer.getRecompositionCount("dejavu.Star"),
            "tracer Star count should equal SideEffect ground truth",
        )
        assertEquals(2, GroundTruth.delta("Star"), "stars 3,4 each recompose once on 3→5")
    }

    @Test
    fun rating_sequentialChanges() = runComposeUiTest {
        setContent { DejavuTestContent { RatingBarScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("set_rating_1_btn").performClick()
        waitForIdle()
        onNodeWithTag("set_rating_5_btn").performClick()
        waitForIdle()

        // 0→1 flips star 0 (1 recomp); 1→5 flips stars 1,2,3,4 (4 recomps) → 5 total.
        assertEquals(
            GroundTruth.delta("Star"),
            DejavuTracer.getRecompositionCount("dejavu.Star"),
            "tracer Star count should equal SideEffect ground truth",
        )
        assertEquals(5, GroundTruth.delta("Star"), "one star changes on 0→1, four on 1→5")
    }

    @Test
    fun rating_noRecompositionWithoutInteraction() = runComposeUiTest {
        setContent { DejavuTestContent { RatingBarScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("rating_bar_root")
            .assertRecompositions(exactly = GroundTruth.delta("rating_bar_root"))
        onNodeWithTag("rating_bar")
            .assertRecompositions(exactly = GroundTruth.delta("rating_bar"))
        onNodeWithTag("rating_display")
            .assertRecompositions(exactly = GroundTruth.delta("rating_display"))
        onNodeWithTag("static_rating_label")
            .assertRecompositions(exactly = GroundTruth.delta("static_rating_label"))
        // No interaction occurred, so every node must be stable.
        assertEquals(0, GroundTruth.delta("rating_bar_root"))
        assertEquals(0, GroundTruth.delta("rating_bar"))
        assertEquals(0, GroundTruth.delta("rating_display"))
        assertEquals(0, GroundTruth.delta("static_rating_label"))
    }
}

// ── Composables ──────────────────────────────────────────────────

@Composable
private fun RatingBarScreen() {
    var rating by remember { mutableFloatStateOf(0f) }
    SideEffect { GroundTruth.record("rating_bar_root") }
    Column(Modifier.testTag("rating_bar_root")) {
        StaticRatingLabel()
        RatingBar(rating = rating, onRatingChange = { rating = it })
        RatingDisplay(rating = rating)
        SetRatingButton(
            label = "Set 1",
            tag = "set_rating_1_btn",
            currentRating = rating,
            targetRating = 1f,
            onRatingChange = { rating = it },
        )
        SetRatingButton(
            label = "Set 3",
            tag = "set_rating_3_btn",
            currentRating = rating,
            targetRating = 3f,
            onRatingChange = { rating = it },
        )
        SetRatingButton(
            label = "Set 5",
            tag = "set_rating_5_btn",
            currentRating = rating,
            targetRating = 5f,
            onRatingChange = { rating = it },
        )
    }
}

@Composable
private fun StaticRatingLabel() {
    SideEffect { GroundTruth.record("static_rating_label") }
    BasicText("Rate this item", Modifier.testTag("static_rating_label"))
}

@Composable
private fun RatingBar(rating: Float, onRatingChange: (Float) -> Unit) {
    SideEffect { GroundTruth.record("rating_bar") }
    Row(Modifier.testTag("rating_bar")) {
        for (i in 0 until 5) {
            Star(
                index = i,
                isFilled = i < rating,
                onClick = { onRatingChange((i + 1).toFloat()) },
            )
        }
    }
}

@Composable
private fun Star(index: Int, isFilled: Boolean, onClick: () -> Unit) {
    SideEffect {
        GroundTruth.record("Star")
        GroundTruth.record("star_$index")
    }
    BasicText(
        text = if (isFilled) "★" else "☆",
        modifier = Modifier.testTag("star_$index").clickable { onClick() },
    )
}

@Composable
private fun RatingDisplay(rating: Float) {
    SideEffect { GroundTruth.record("rating_display") }
    BasicText("Rating: $rating / 5.0", Modifier.testTag("rating_display"))
}

@Composable
private fun SetRatingButton(
    label: String,
    tag: String,
    currentRating: Float,
    targetRating: Float,
    onRatingChange: (Float) -> Unit,
) {
    // Intentional over-recomposition fixture: passing currentRating makes every button recompose
    // when the rating changes, even though its label is unchanged. Keep this redundant input so
    // the test proves Dejavu counts that inefficient pattern accurately.
    SideEffect { GroundTruth.record(tag) }
    BasicText(
        label,
        Modifier.testTag(tag).clickable {
            if (currentRating != targetRating) onRatingChange(targetRating)
        },
    )
}
