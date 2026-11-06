@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package demo.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dejavu.assertRecompositions
import dejavu.assertStable
import dejavu.resetRecompositionCounts
import dejavu.runRecompositionTrackingUiTest
import dejavu.setTrackedContent
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Android's named-owner mapper must not hide the lambda that actually executes. */
@RunWith(AndroidJUnit4::class)
class LambdaAttributionAndroidTest {
    @Test fun lambdaInstances_countThreeAndZeroWithoutIntermediateQueries() = registryCase(false, false)
    @Test fun lambdaInstances_queriesPreserveExactCounts() = registryCase(true, false)
    @Test fun namedRenderer_doesNotHideLambdaOwnership() = registryCase(false, true)
    @Test fun namedRenderer_queriesPreserveExactCounts() = registryCase(true, true)

    private fun registryCase(queryEachUpdate: Boolean, namedRenderer: Boolean) = runRecompositionTrackingUiTest {
        val echo = mutableIntStateOf(0)
        val stable = mutableIntStateOf(0)
        val echoCount = AtomicInteger()
        val stableCount = AtomicInteger()
        val component: @Composable (String, State<Int>, AtomicInteger) -> Unit = { tag, state, count ->
            val value = state.value
            SideEffect { count.incrementAndGet() }
            BasicText("$tag:$value", Modifier.testTag(tag))
        }
        setTrackedContent {
            Column {
                key("echo") {
                    if (namedRenderer) AnonymousTestRenderer(component, "echo", echo, echoCount)
                    else component("echo", echo, echoCount)
                }
                key("stable") {
                    if (namedRenderer) AnonymousTestRenderer(component, "stable", stable, stableCount)
                    else component("stable", stable, stableCount)
                }
            }
        }
        waitForIdle()
        val echoBaseline = echoCount.get()
        val stableBaseline = stableCount.get()
        resetRecompositionCounts()
        repeat(3) { index ->
            runOnIdle { echo.intValue++ }
            waitForIdle()
            if (queryEachUpdate) {
                assertEquals(index + 1, echoCount.get() - echoBaseline)
                assertEquals(0, stableCount.get() - stableBaseline)
                onNodeWithTag("echo").assertRecompositions(exactly = index + 1)
                onNodeWithTag("stable").assertStable()
            }
        }
        onNodeWithTag("echo").assertTextEquals("echo:3")
        onNodeWithTag("stable").assertTextEquals("stable:0")
        assertEquals(3, echoCount.get() - echoBaseline)
        assertEquals(0, stableCount.get() - stableBaseline)
        // Query the unchanged instance first to expose accidental shared totals.
        repeat(2) {
            onNodeWithTag("stable").assertRecompositions(exactly = 0)
            onNodeWithTag("echo").assertRecompositions(exactly = 3)
        }
        onNodeWithTag("echo").assertRecompositions(atLeast = 0)
        assertTrue(runCatching { onNodeWithTag("echo").assertStable() }.exceptionOrNull() is AssertionError)
        assertTrue(runCatching {
            onNodeWithTag("echo").assertRecompositions(atMost = 2)
        }.exceptionOrNull() is AssertionError)
        assertTrue(runCatching {
            onNodeWithTag("stable").assertRecompositions(exactly = 1)
        }.exceptionOrNull() is AssertionError)

        val echoResetBaseline = echoCount.get()
        val stableResetBaseline = stableCount.get()
        resetRecompositionCounts()
        onNodeWithTag("echo").assertRecompositions(exactly = 0)
        onNodeWithTag("stable").assertStable()
        assertEquals(0, echoCount.get() - echoResetBaseline)
        assertEquals(0, stableCount.get() - stableResetBaseline)
    }
}

@Composable
private fun AnonymousTestRenderer(
    component: @Composable (String, State<Int>, AtomicInteger) -> Unit,
    tag: String,
    state: State<Int>,
    counter: AtomicInteger,
) {
    component(tag, state, counter)
}
