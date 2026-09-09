import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import dejavu.assertRecompositions
import dejavu.createRecompositionTrackingRule
import dejavu.resetRecompositionCounts
import org.junit.Rule
import org.junit.Test

class PublishedAndroidRuleTest {
    @get:Rule val rule = createRecompositionTrackingRule()
    @Test fun baselineBuiltRuleWorksWithTheSelectedRuntime() {
        val value = mutableIntStateOf(0)
        rule.setContent { BasicText("${value.intValue}", Modifier.testTag("value")) }
        rule.waitForIdle()
        rule.resetRecompositionCounts()
        rule.runOnIdle { value.intValue++ }
        rule.waitForIdle()
        rule.onNodeWithTag("value").assertRecompositions(exactly = 1)
    }
}
