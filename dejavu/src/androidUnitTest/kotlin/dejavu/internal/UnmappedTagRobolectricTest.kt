package dejavu.internal

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dejavu.DejavuTestContent
import dejavu.GroundTruth
import dejavu.UnexpectedRecompositionsError
import dejavu.assertStable
import dejavu.disableDejavuForTest
import dejavu.enableDejavuForTest
import dejavu.getRecompositionCount
import dejavu.resetRecompositionCounts
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class UnmappedTagRobolectricTest {
    // This tests lookup independently of the Android Runtime's frame scheduler.
    @get:Rule val rule = createComposeRule()

    @Before fun enableTracking() {
        enableDejavuForTest()
        GroundTruth.clear()
    }

    @After fun disableTracking() {
        disableDejavuForTest()
    }

    @Test fun untrackedContentHasNoNumericCountEvenWhenItsFunctionWasTraced() {
        val value = mutableIntStateOf(0)
        rule.setContent { GetterTrackedValue(value.intValue) }
        rule.waitForIdle()
        rule.resetRecompositionCounts()
        GroundTruth.snapshotBaseline()
        repeat(3) {
            rule.runOnIdle { value.intValue++ }
            rule.waitForIdle()
        }
        rule.onNodeWithTag("getter_value").assertTextEquals("Value: 3")
        assertEquals(3, GroundTruth.delta("getter_value"))
        assertEquals(3, DejavuTracer.getRecompositionCount("GetterTrackedValue"))
        val error = assertFailsWith<IllegalStateException> {
            rule.getRecompositionCount("getter_value")
        }
        assertTrue(error.message.orEmpty().contains("testTag 'getter_value' could not be mapped"))
        assertFailsWith<IllegalStateException> {
            rule.getRecompositionCount("dejavu.internal.GetterTrackedValue")
        }
        assertFailsWith<UnexpectedRecompositionsError> {
            rule.onNodeWithTag("getter_value").assertStable()
        }
    }

    @Test fun trackedContentDistinguishesStableAndChangedCounts() {
        val value = mutableIntStateOf(0)
        rule.setContent { DejavuTestContent { GetterTrackedValue(value.intValue) } }
        rule.waitForIdle()
        rule.resetRecompositionCounts()
        GroundTruth.snapshotBaseline()
        rule.onNodeWithTag("getter_value").assertStable()
        assertEquals(0, rule.getRecompositionCount("getter_value"))
        repeat(3) {
            rule.runOnIdle { value.intValue++ }
            rule.waitForIdle()
        }
        rule.onNodeWithTag("getter_value").assertTextEquals("Value: 3")
        assertEquals(3, GroundTruth.delta("getter_value"))
        assertEquals(GroundTruth.delta("getter_value"), rule.getRecompositionCount("getter_value"))
        assertFailsWith<UnexpectedRecompositionsError> {
            rule.onNodeWithTag("getter_value").assertStable()
        }
    }
}

@Composable
private fun GetterTrackedValue(value: Int) {
    SideEffect { GroundTruth.record("getter_value") }
    BasicText("Value: $value", Modifier.testTag("getter_value"))
}
