import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import dejavu.assertStable
import dejavu.createRecompositionTrackingRule
import dejavu.resetRecompositionCounts
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse

class PublishedNewRuleApiTest {
    @get:Rule val rule = createRecompositionTrackingRule()
    @Test fun newRuleMethodsDelegateToCompose112() {
        rule.setContent { BasicText("Stable", Modifier.testTag("stable")) }
        rule.waitForIdle()
        rule.resetRecompositionCounts()
        rule.runOnUiThread {
            rule.runWithoutImplicitWait {
                rule.onNodeWithTag("stable").assertTextEquals("Stable").assertStable()
                assertFalse(rule.hasPendingWork())
            }
        }
    }
}
