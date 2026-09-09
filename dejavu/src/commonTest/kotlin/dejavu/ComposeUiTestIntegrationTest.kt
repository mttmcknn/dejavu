@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dejavu

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import dejavu.internal.DejavuTracer
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ComposeUiTestIntegrationTest {
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
private fun AsyncTrackedValue(value: Int) {
    SideEffect { GroundTruth.record("async_value") }
    BasicText("Value: $value", Modifier.testTag("async_value"))
}
