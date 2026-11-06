@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dejavu

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import dejavu.internal.DejavuTracer
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeUiTestIntegrationTest {
    @Test
    fun untrackedContentCannotPassARecompositionBudget() = runRecompositionTrackingUiTest {
        val value = mutableIntStateOf(0)
        GroundTruth.clear()
        // A real setup mistake: tracing is enabled, but setContent does not install inspection tables.
        setContent { UnmappedTrackedValue(value.intValue, "unmapped_value") }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()
        repeat(3) {
            runOnIdle { value.intValue++ }
            waitForIdle()
        }
        onNodeWithTag("unmapped_value").assertTextEquals("Value: 3")
        assertEquals(3, GroundTruth.delta("unmapped_value"))
        assertEquals(3, DejavuTracer.getRecompositionCount("dejavu.UnmappedTrackedValue"))
        assertNull(DejavuTracer.getFunctionNameForTag("unmapped_value"))

        val node = onNodeWithTag("unmapped_value")
        val errors = listOf(
            assertFailsWith<UnexpectedRecompositionsError> { node.assertStable() },
            assertFailsWith<UnexpectedRecompositionsError> { node.assertRecompositions(exactly = 0) },
            assertFailsWith<UnexpectedRecompositionsError> { node.assertRecompositions(atMost = 3) },
            assertFailsWith<UnexpectedRecompositionsError> { node.assertRecompositions(atLeast = 0) },
            assertFailsWith<UnexpectedRecompositionsError> { node.assertRecompositions(atLeast = 0, atMost = 3) },
        )
        errors.forEach { error ->
            val message = error.message.orEmpty()
            assertTrue(message.contains("testTag 'unmapped_value' could not be mapped"), message)
            assertTrue(message.contains("Actual: unavailable"), message)
            assertTrue(message.contains("setTrackedContent"), message)
            assertTrue(message.contains("UnmappedTrackedValue = 3"), message)
            assertTrue(message.contains("Semantic tree:"), message)
            assertFalse(message.contains("Actual: 0"), message)
        }
    }

    @Test
    fun tagMatchingAFunctionNameStillRequiresAMapping() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        setContent { UnmappedTrackedValue(0, "dejavu.UnmappedTrackedValue") }
        waitForIdle()
        // A tag is a semantic-node identifier, not a request for an aggregate function count.
        val error = assertFailsWith<UnexpectedRecompositionsError> {
            onNodeWithTag("dejavu.UnmappedTrackedValue").assertStable()
        }
        assertTrue(error.message.orEmpty().contains("Actual: unavailable"))
    }

    @Test
    fun trackedStableContentStillHasAZeroCount() = runRecompositionTrackingUiTest {
        GroundTruth.clear()
        setTrackedContent { AsyncTrackedValue(0) }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()
        onNodeWithTag("async_value").assertTextEquals("Value: 0").assertStable()
        assertEquals(1, GroundTruth.total("async_value"))
        assertEquals(0, GroundTruth.delta("async_value"))
    }

    @Test
    fun trackingSurvivesSuspensionAndReportsCaughtBudgetFailure() = runRecompositionTrackingUiTest {
        val value = mutableIntStateOf(0)
        GroundTruth.clear()
        setTrackedContent { AsyncTrackedValue(value.intValue) }
        waitForIdle()
        resetRecompositionCounts()
        GroundTruth.snapshotBaseline()

        yield()
        assertTrue(DejavuTracer.enabled, "Tracking must stay enabled until the async test completes")
        runOnIdle { value.intValue++ }
        waitForIdle()
        yield()

        assertEquals(1, GroundTruth.delta("async_value"))
        onNodeWithTag("async_value")
            .assertRecompositions(exactly = GroundTruth.delta("async_value"))
        val error = assertFailsWith<UnexpectedRecompositionsError> {
            onNodeWithTag("async_value").assertStable()
        }
        assertTrue(error.message.orEmpty().contains("Actual: 1"))
    }
}

@Composable
private fun UnmappedTrackedValue(value: Int, tag: String) {
    SideEffect { GroundTruth.record(tag) }
    BasicText("Value: $value", Modifier.testTag(tag))
}

@Composable
private fun AsyncTrackedValue(value: Int) {
    SideEffect { GroundTruth.record("async_value") }
    BasicText("Value: $value", Modifier.testTag("async_value"))
}
