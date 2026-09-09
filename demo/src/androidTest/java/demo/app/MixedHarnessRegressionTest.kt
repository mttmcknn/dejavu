package demo.app

import android.app.Application
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import dejavu.Dejavu
import dejavu.assertRecompositions
import dejavu.assertStable
import dejavu.resetRecompositionCounts
import dejavu.runRecompositionTrackingUiTest
import dejavu.setTrackedContent
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows

/** Both public harnesses can run in the same instrumentation process. */
@OptIn(ExperimentalTestApi::class)
class MixedHarnessRegressionTest {
    @Test
    fun enabledAndroidRuntimeKeepsTheKmpHelpersInspectionTablesVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            Dejavu.enable(instrumentation.targetContext.applicationContext as Application)
        }
        try {
            runRecompositionTrackingUiTest {
                val value = mutableIntStateOf(0)
                var compositions = 0
                setTrackedContent { MixedHarnessValue(value.intValue) { compositions++ } }
                waitForIdle()
                val baseline = compositions
                resetRecompositionCounts()
                runOnIdle { value.intValue++ }
                waitForIdle()
                assertEquals(1, compositions - baseline)
                onNodeWithTag("mixed_harness_value").assertRecompositions(exactly = compositions - baseline)
                assertThrows(AssertionError::class.java) { onNodeWithTag("mixed_harness_value").assertStable() }
            }
        } finally {
            instrumentation.runOnMainSync { Dejavu.disable() }
        }
    }
}

@Composable
private fun MixedHarnessValue(value: Int, onComposition: () -> Unit) {
    SideEffect(onComposition)
    BasicText("$value", Modifier.testTag("mixed_harness_value"))
}
