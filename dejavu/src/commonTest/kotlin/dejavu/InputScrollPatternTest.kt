package dejavu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlin.test.assertTrue

/**
 * Text input + asynchronous source (`produceState`, `snapshotFlow`) recomposition patterns.
 *
 * Every assertion is exact and self-validating against a [GroundTruth] `SideEffect` (the Compose
 * runtime runs the effect after every successful composition, so it is the real composition count).
 * Each tracked composable here is **single-instance with a single testTag**, emitted from a distinct
 * call site in [InputScreen], so the public per-tag API resolves its exact count on every platform →
 * `exactly = GroundTruth.delta(tag)`.
 *
 * Two classes of timing:
 * - **Deterministic** synchronous state writes (typing a character flips `text`, which the
 *   value-reader displays) recompose exactly once per click → also pinned with `assertEquals(N, ...)`.
 * - **Settle-then-delta** asynchronous sources (`produceState` recomposing on a trigger change, a
 *   `snapshotFlow` collector emitting a new value) deliver their value across one or more frames.
 *   The test settles with `waitForIdle()` before asserting and pins only `tracer == delta(tag)` —
 *   it does not hardcode a literal frame count — and verifies the source did fire with
 *   `delta(tag) >= 1` rather than a directional `atLeast`.
 *
 * Every test calls [resetRecompositionCounts] + [GroundTruth.snapshotBaseline] after the initial
 * `waitForIdle()` (or after pre-interactions) so `delta`/tracer stay aligned at the zero point.
 */
@OptIn(ExperimentalTestApi::class)
class InputScrollPatternTest {

    @BeforeTest
    fun setUp() {
        enableDejavuForTest()
        GroundTruth.clear()
    }

    @AfterTest
    fun tearDown() = disableDejavuForTest()

    @Test
    fun typeCharacter_displayRecomposesOnce() = runComposeUiTest {
        setContent { DejavuTestContent { InputScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("type_char_btn").performClick()
        waitForIdle()

        // Typing one character flips `text`, which the value-reader displays → recomposes once.
        onNodeWithTag("text_display")
            .assertRecompositions(exactly = GroundTruth.delta("text_display"))
        assertEquals(1, GroundTruth.delta("text_display"), "one keystroke recomposes the display once")
    }

    @Test
    fun typeCharacter_unrelatedStable() = runComposeUiTest {
        setContent { DejavuTestContent { InputScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("type_char_btn").performClick()
        waitForIdle()

        onNodeWithTag("text_unrelated").assertStable()
        assertEquals(0, GroundTruth.delta("text_unrelated"), "parameterless sibling never recomposes on typing")
    }

    @Test
    fun rapidTyping_eachKeystrokeTracked() = runComposeUiTest {
        setContent { DejavuTestContent { InputScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        repeat(5) {
            onNodeWithTag("type_char_btn").performClick()
            waitForIdle()
        }

        // Five distinct keystrokes, each settled, each flips `text` once → five recompositions.
        onNodeWithTag("text_display")
            .assertRecompositions(exactly = GroundTruth.delta("text_display"))
        assertEquals(5, GroundTruth.delta("text_display"), "five keystrokes recompose the display five times")
    }

    @Test
    fun produceState_recomposesOnTriggerChange() = runComposeUiTest {
        setContent { DejavuTestContent { InputScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("change_source_btn").performClick()
        waitForIdle()

        // produceState is asynchronous: changing `trigger` relaunches the producer, which writes a
        // new `produced` value across one or more frames. Settle, then assert the tracer matches the
        // real composition count exactly (don't hardcode a frame literal), and confirm it did fire.
        onNodeWithTag("produced_value")
            .assertRecompositions(exactly = GroundTruth.delta("produced_value"))
        assertTrue(GroundTruth.delta("produced_value") >= 1, "produceState recomposes the reader on a trigger change")
    }

    @Test
    fun snapshotFlowChange_recomposesReader() = runComposeUiTest {
        setContent { DejavuTestContent { InputScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("change_source_btn").performClick()
        waitForIdle()

        // snapshotFlow delivers the new value asynchronously to the collector, which writes
        // `flowValue` and recomposes the reader. Settle, then assert tracer == real count exactly
        // (no hardcoded frame literal), and confirm the flow emission did reach the reader.
        onNodeWithTag("flow_reader")
            .assertRecompositions(exactly = GroundTruth.delta("flow_reader"))
        assertTrue(GroundTruth.delta("flow_reader") >= 1, "snapshotFlow emission recomposes the reader")
    }

    @Test
    fun snapshotFlowSameValue_readerStable() = runComposeUiTest {
        setContent { DejavuTestContent { InputScreen() } }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        onNodeWithTag("same_source_btn").performClick()
        waitForIdle()

        onNodeWithTag("flow_reader").assertStable()
        assertEquals(0, GroundTruth.delta("flow_reader"), "same-value source write emits nothing new → reader stable")
    }
}

// ── Composables ──────────────────────────────────────────────────

@Composable
private fun InputScreen() {
    var text by remember { mutableStateOf("") }
    var source by remember { mutableIntStateOf(0) }
    Column {
        TextDisplay(text)
        TextUnrelated()
        ProducedValueReader(source)
        FlowReaderComposable(source)
        BasicText("TypeChar", Modifier.testTag("type_char_btn").clickable { text += "a" })
        BasicText("ChangeSource", Modifier.testTag("change_source_btn").clickable { source++ })
        BasicText("SameSource", Modifier.testTag("same_source_btn").clickable { source = source })
    }
}

@Composable
private fun TextDisplay(text: String) {
    SideEffect { GroundTruth.record("text_display") }
    BasicText("Text: $text", Modifier.testTag("text_display"))
}

@Composable
private fun TextUnrelated() {
    SideEffect { GroundTruth.record("text_unrelated") }
    BasicText("Unrelated", Modifier.testTag("text_unrelated"))
}

@Composable
private fun ProducedValueReader(trigger: Int) {
    SideEffect { GroundTruth.record("produced_value") }
    val produced by produceState(initialValue = 0, trigger) { value = trigger * 10 }
    BasicText("Produced: $produced", Modifier.testTag("produced_value"))
}

@Composable
private fun FlowReaderComposable(source: Int) {
    SideEffect { GroundTruth.record("flow_reader") }
    val sourceState = remember { mutableIntStateOf(source) }
    sourceState.intValue = source
    var flowValue by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        snapshotFlow { sourceState.intValue }.collect { flowValue = it }
    }
    BasicText("Flow: $flowValue", Modifier.testTag("flow_reader"))
}
