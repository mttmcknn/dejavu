package demo.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dejavu.assertRecompositions
import dejavu.assertStable
import dejavu.resetRecompositionCounts
import dejavu.runRecompositionTrackingUiTest
import dejavu.setTrackedContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Device coverage for exact helper-owned lambda scopes, without an Android tracking rule. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ExactLambdaCountingTest {
    @Test
    fun registeredSiblings_countThreeAndZeroWithoutIntermediateQueries() =
        exerciseSharedLambda(equalValueWrites = false)

    @Test
    fun equalValueWrites_countThreeExecutionsDespiteUnchangedOutputAndRepeatedQueries() =
        exerciseSharedLambda(equalValueWrites = true)

    private fun exerciseSharedLambda(equalValueWrites: Boolean) = runRecompositionTrackingUiTest {
        val value = if (equalValueWrites) mutableStateOf(0, neverEqualPolicy()) else mutableStateOf(0)
        val echo = DeviceLambdaProbe("echo", value)
        val sibling = DeviceLambdaProbe("sibling", mutableStateOf(0))
        // Both instances execute the very same registered lambda and source group.
        val registry: Map<String, @Composable (DeviceLambdaProbe) -> Unit> = mapOf(
            "value" to { probe ->
                val displayed = probe.state.value
                SideEffect { probe.recordExecution() }
                BasicText("${probe.tag}:$displayed", Modifier.testTag(probe.tag))
            },
        )
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
            runOnIdle { value.value = if (equalValueWrites) 0 else value.value + 1 }
            waitForIdle()
            if (equalValueWrites) {
                // Same State object, arguments, and output on every execution.
                // Repeated queries must neither create nor consume counts.
                repeat(2) {
                    assertDeviceBodyCount(echo, echoBaseline, index + 1)
                    assertDeviceBodyCount(sibling, siblingBaseline, 0)
                }
            }
        }

        onNodeWithTag("echo").assertTextEquals(if (equalValueWrites) "echo:0" else "echo:3")
        onNodeWithTag("sibling").assertTextEquals("sibling:0")
        assertDeviceBodyCount(sibling, siblingBaseline, 0)
        assertDeviceBodyCount(echo, echoBaseline, 3)
        assertThrows(AssertionError::class.java) { onNodeWithTag("echo").assertRecompositions(atMost = 2) }
        assertThrows(AssertionError::class.java) { onNodeWithTag("echo").assertStable() }
        assertDeviceBodyCount(echo, echoBaseline, 3)
        assertDeviceBodyCount(sibling, siblingBaseline, 0)
    }
}

// Stable rendered inputs; the private non-snapshot counter is used only as an oracle.
@Stable
private class DeviceLambdaProbe(val tag: String, val state: State<Int>) {
    private val executions = AtomicInteger()
    fun recordExecution() { executions.incrementAndGet() }
    fun executionCount(): Int = executions.get()
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertDeviceBodyCount(probe: DeviceLambdaProbe, baseline: Int, expected: Int) {
    val actual = probe.executionCount() - baseline
    assertEquals("Committed body executions for ${probe.tag}", expected, actual)
    onNodeWithTag(probe.tag).assertRecompositions(exactly = actual)
}
