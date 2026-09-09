package example
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import dejavu.assertRecompositions
import dejavu.createRecompositionTrackingRule
import dejavu.resetRecompositionCounts
import org.junit.Rule
import org.junit.Test
class CounterTest {
    @get:Rule val rule = createRecompositionTrackingRule()
    @Test fun counterUpdates() {
        val count = mutableIntStateOf(0)
        rule.setContent { CounterValue(count.intValue) }
        rule.waitForIdle()
        rule.resetRecompositionCounts()
        rule.runOnIdle { count.intValue++ }
        rule.waitForIdle()
        rule.onNodeWithTag("counter_value").assertTextEquals("Value: 1")
    }
}
