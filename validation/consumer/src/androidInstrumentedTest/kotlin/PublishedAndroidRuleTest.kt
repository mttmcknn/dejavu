import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import dejavu.assertRecompositions
import dejavu.createRecompositionTrackingRule
import dejavu.resetRecompositionCounts
import org.junit.Rule
import org.junit.Test

private var ruleCompositions = 0

class PublishedAndroidRuleTest {
    @get:Rule val rule = createRecompositionTrackingRule()
    @Test fun baselineBuiltRuleWorksWithTheSelectedRuntime() {
        val value = mutableIntStateOf(0)
        ruleCompositions = 0
        rule.setContent { ConsumerAndroidValue(value.intValue) }
        rule.waitForIdle()
        val baseline = ruleCompositions
        rule.resetRecompositionCounts()
        rule.runOnIdle { value.intValue++ }
        rule.waitForIdle()
        kotlin.test.assertEquals(1, ruleCompositions - baseline)
        rule.onNodeWithTag("value").assertRecompositions(exactly = ruleCompositions - baseline)
    }
}

@Composable
private fun ConsumerAndroidValue(value: Int) {
    SideEffect { ruleCompositions++ }
    BasicText("$value", Modifier.testTag("value"))
}
