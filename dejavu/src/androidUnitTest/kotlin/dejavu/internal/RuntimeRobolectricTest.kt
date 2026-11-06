package dejavu.internal

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import dejavu.Dejavu
import dejavu.DejavuComposeTestRule
import dejavu.DejavuTest
import dejavu.assertRecompositions
import dejavu.createRecompositionTrackingRule
import dejavu.resetRecompositionCounts
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.atomic.AtomicInteger

/** Real Android UI coverage for #141; no paused Choreographer workaround. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class RuntimeRobolectricTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val application get() = instrumentation.targetContext.applicationContext as Application

    @Before
    fun reset() {
        instrumentation.runOnMainSync { Dejavu.disable() }
        DejavuTest.reset()
    }

    @After
    fun cleanup() {
        instrumentation.runOnMainSync { Dejavu.disable() }
    }

    @Test
    fun ruleReachesIdleAndTracksUpdatesAfterIdle() {
        runTracked { rule ->
            val state = mutableIntStateOf(0)
            val commits = AtomicInteger()
            rule.setContent { RuntimeCounter(state, commits) }
            rule.waitForIdle()
            val baseline = commits.get()
            rule.resetRecompositionCounts()
            repeat(3) {
                rule.mainClock.advanceTimeBy(1_000)
                rule.runOnIdle { state.intValue++ }
                rule.waitForIdle()
            }
            assertEquals(3, commits.get() - baseline)
            rule.onNodeWithTag("counter").assertTextEquals("3")
            rule.onNodeWithTag("counter").assertRecompositions(exactly = 3)
        }
        assertFalse("the rule must release its runtime", DejavuTracer.enabled)
        assertTrue(Runtime.currentCompositionsSnapshot().isEmpty())
    }

    @Test
    fun pendingWorkDoesNotSpinOrAdvanceAManualComposeClock() {
        runTracked { rule ->
            val state = mutableIntStateOf(0)
            val commits = AtomicInteger()
            rule.setContent { RuntimeCounter(state, commits) }
            rule.waitForIdle()
            val baseline = commits.get()
            rule.resetRecompositionCounts()
            rule.mainClock.autoAdvance = false
            rule.runOnUiThread { state.intValue++ }
            rule.waitForIdle()
            assertEquals("idle must not advance the Compose clock", baseline, commits.get())
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            assertEquals(1, commits.get() - baseline)
            rule.onNodeWithTag("counter").assertTextEquals("1")
            rule.onNodeWithTag("counter").assertRecompositions(exactly = 1)
        }
        assertFalse(DejavuTracer.enabled)
    }

    @Test
    fun failingTestReleasesOwnedRuntimeAndTheNextRuleWorks() {
        val failure = IllegalStateException("deliberate test failure")
        val actual = runCatching { runTracked { throw failure } }.exceptionOrNull()
        assertSame(failure, actual)
        assertFalse(DejavuTracer.enabled)
        runTracked { rule ->
            rule.setContent { BasicText("next", Modifier.testTag("next")) }
            rule.onNodeWithTag("next").assertTextEquals("next")
        }
        assertFalse(DejavuTracer.enabled)
    }

    @Test
    fun rulePreservesApplicationOwnedRuntime() {
        instrumentation.runOnMainSync { Dejavu.enable(application) }
        runTracked { rule ->
            rule.setContent { BasicText("owned", Modifier.testTag("owned")) }
            rule.onNodeWithTag("owned").assertTextEquals("owned")
        }
        assertTrue("an application-owned runtime must survive the rule", DejavuTracer.enabled)
    }

    @Test
    fun anOldRuleCannotDisableANewerRuntimeSession() {
        runTracked { rule ->
            rule.runOnUiThread {
                Dejavu.disable()
                Dejavu.enable(application)
            }
        }
        assertTrue("cleanup must match the owned session identity", DejavuTracer.enabled)
    }

    @Test
    fun reEnableOnAnAlreadyResumedActivityRestoresTagMapping() {
        runTracked { rule ->
            val state = mutableIntStateOf(0)
            val commits = AtomicInteger()
            rule.setContent { RuntimeCounter(state, commits) }
            rule.waitForIdle()
            rule.runOnUiThread {
                Dejavu.disable()
                assertTrue(Runtime.currentCompositionsSnapshot().isEmpty())
                Dejavu.enable(application)
            }
            rule.waitForIdle()
            val baseline = commits.get()
            rule.resetRecompositionCounts()
            rule.runOnIdle { state.intValue++ }
            rule.waitForIdle()
            assertEquals(1, commits.get() - baseline)
            rule.onNodeWithTag("counter").assertTextEquals("1")
            rule.onNodeWithTag("counter").assertRecompositions(exactly = 1)
        }
    }

    @Test
    fun frameRequestsCoalesceAndAStoppedSessionCannotRefresh() {
        var oldRefreshes = 0
        var newRefreshes = 0
        instrumentation.runOnMainSync {
            val old = FrameRefreshScheduler { oldRefreshes++ }
            repeat(3) { old.request() }
            old.dispose()
            val current = FrameRefreshScheduler { newRefreshes++ }
            repeat(3) { current.request() }
            old.request()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, oldRefreshes)
            assertEquals(1, newRefreshes)
            // A later external event starts another bounded refresh after idle.
            current.request()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2, newRefreshes)
            current.dispose()
        }
    }

    @Test
    fun aBackgroundRequestBindsToTheMainChoreographer() {
        var refreshes = 0
        lateinit var scheduler: FrameRefreshScheduler
        val requester = Thread {
            scheduler = FrameRefreshScheduler {
                assertSame(Looper.getMainLooper(), Looper.myLooper())
                refreshes++
            }
            scheduler.request()
        }
        requester.start()
        requester.join()
        instrumentation.runOnMainSync {
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, refreshes)
            scheduler.dispose()
        }
    }

    private fun runTracked(block: (DejavuComposeTestRule<ComponentActivity>) -> Unit) {
        val rule = createRecompositionTrackingRule()
        rule.apply(object : Statement() {
            override fun evaluate() = block(rule)
        }, Description.createTestDescription(javaClass, "trackedTest")).evaluate()
    }
}

@Composable
private fun RuntimeCounter(state: State<Int>, commits: AtomicInteger) {
    val value = state.value
    SideEffect { commits.incrementAndGet() }
    BasicText("$value", Modifier.testTag("counter"))
}
