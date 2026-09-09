package demo.app

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dejavu.Dejavu
import dejavu.assertStable
import dejavu.assertRecompositions
import dejavu.createRecompositionTrackingRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * Regression tests for issue #80: `DejavuComposeTestRule.apply()` must work when
 * rule setup runs inside compose-ui-test's coroutine test scope.
 */
@RunWith(AndroidJUnit4::class)
class RunTestRuleSetupTest {

    @get:Rule
    val composeTestRule = createRecompositionTrackingRule<CounterActivity>()

    @Test
    fun ruleSetup_succeedsInsideRunTest() = runTest {
        composeTestRule.onNodeWithTag("counter_value").assertStable()
    }

    @Test
    fun ruleSetup_succeedsWhenDejavuWasNotPreEnabledByTheApp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Dejavu.disable()
        }

        val rule = createRecompositionTrackingRule<CounterActivity>()
        val statement = rule.apply(
            object : Statement() {
                override fun evaluate() {
                    rule.onNodeWithTag("counter_value").assertStable()
                    GroundTruthCounters.reset()
                    rule.onNodeWithTag("inc_button").performClick()
                    rule.waitForIdle()
                    assertEquals(1, GroundTruthCounters.get("counter_value"))
                    rule.onNodeWithTag("counter_value").assertRecompositions(exactly = 1)
                }
            },
            Description.createTestDescription(
                RunTestRuleSetupTest::class.java,
                "ruleSetup_succeedsWhenDejavuWasNotPreEnabledByTheApp",
            ),
        )

        try {
            statement.evaluate()
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                Dejavu.disable()
            }
        }
    }
}
