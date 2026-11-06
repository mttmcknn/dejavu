@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dejavu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import dejavu.internal.DejavuTracer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LambdaAttributionTest {
    @Test
    fun repeatedLambdaWithoutIntermediateQueries_rejectsUnsupportedCounts() =
        registryCase(queryEachUpdate = false, namedRenderer = false)

    @Test
    fun repeatedLambdaWithIntermediateQueries_rejectsTheSameCounts() =
        registryCase(queryEachUpdate = true, namedRenderer = false)

    @Test
    fun lambdaInsideNamedRenderer_doesNotReportTheRendererCount() =
        registryCase(queryEachUpdate = false, namedRenderer = true)

    @Test
    fun namedComponentStillCountsThreeCommittedUpdates() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        val value = mutableIntStateOf(0)
        setTrackedContent { LambdaTestNamedComponent("named", value) }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()
        repeat(3) {
            runOnIdle { value.intValue++ }
            waitForIdle()
        }
        onNodeWithTag("named").assertTextEquals("named:3")
        assertEquals(3, GroundTruth.delta("named"))
        onNodeWithTag("named").assertRecompositions(exactly = 3)
        assertFailsWith<UnexpectedRecompositionsError> {
            onNodeWithTag("named").assertStable()
        }
    }

    @Test
    fun tagReusedByNamedComponent_requiresANewMeasurementWindow() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        val useLambda = mutableIntStateOf(1)
        val value = mutableIntStateOf(0)
        val component: @Composable () -> Unit = {
            BasicText("lambda:${value.intValue}", Modifier.testTag("reused"))
        }
        setTrackedContent {
            if (useLambda.intValue == 1) component()
            else LambdaTestNamedComponent("reused", value)
        }
        waitForIdle()
        assertLambdaCountUnavailable("reused")

        runOnIdle { useLambda.intValue = 0 }
        waitForIdle()
        onNodeWithTag("reused").assertTextEquals("reused:0")
        // The old window includes unsupported executions even though its tag was reused.
        assertLambdaCountUnavailable("reused")
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()
        runOnIdle { value.intValue++ }
        waitForIdle()
        assertEquals(1, GroundTruth.delta("reused"))
        onNodeWithTag("reused").assertRecompositions(exactly = 1)
    }

    private fun registryCase(queryEachUpdate: Boolean, namedRenderer: Boolean) = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        val echo = mutableIntStateOf(0)
        val stable = mutableIntStateOf(0)
        val registry: Map<String, @Composable (String, State<Int>) -> Unit> = mapOf(
            "text" to { tag, state ->
                val value = state.value
                SideEffect { GroundTruth.record(tag) }
                BasicText("$tag:$value", Modifier.testTag(tag))
            }
        )
        val component = registry.getValue("text")
        setTrackedContent {
            Column {
                key("echo") {
                    if (namedRenderer) LambdaTestRenderer(component, "echo", echo)
                    else component("echo", echo)
                }
                key("stable") {
                    if (namedRenderer) LambdaTestRenderer(component, "stable", stable)
                    else component("stable", stable)
                }
            }
        }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()
        repeat(3) {
            runOnIdle { echo.intValue++ }
            waitForIdle()
            if (queryEachUpdate) assertLambdaCountUnavailable("echo")
        }
        onNodeWithTag("echo").assertTextEquals("echo:3")
        onNodeWithTag("stable").assertTextEquals("stable:0")
        assertEquals(3, GroundTruth.delta("echo"))
        assertEquals(0, GroundTruth.delta("stable"))
        assertLambdaCountUnavailable("echo")
        assertLambdaCountUnavailable("stable")
        assertTrue(DejavuTracer.getFunctionNameForTag("echo") != "ReusableComposeNode")

        // Clearing the measurement window must not turn unsupported ownership into zero.
        resetRecompositionCounts()
        assertLambdaCountUnavailable("echo")
    }

    private fun ComposeUiTest.assertLambdaCountUnavailable(tag: String) {
        val stableError = assertFailsWith<UnexpectedRecompositionsError> {
            onNodeWithTag(tag).assertStable()
        }
        assertTrue(stableError.message.orEmpty().contains("includes a lambda owner"))
        val countError = assertFailsWith<UnexpectedRecompositionsError> {
            onNodeWithTag(tag).assertRecompositions(exactly = 3)
        }
        assertEquals(stableError.message, countError.message)
        val unboundedError = assertFailsWith<UnexpectedRecompositionsError> {
            onNodeWithTag(tag).assertRecompositions(atLeast = 0)
        }
        assertEquals(stableError.message, unboundedError.message)
    }
}

@Composable
private fun LambdaTestRenderer(component: @Composable (String, State<Int>) -> Unit, tag: String, state: State<Int>) {
    component(tag, state)
}

@Composable
private fun LambdaTestNamedComponent(tag: String, state: State<Int>) {
    val value = state.value
    SideEffect { GroundTruth.record(tag) }
    BasicText("$tag:$value", Modifier.testTag(tag))
}
