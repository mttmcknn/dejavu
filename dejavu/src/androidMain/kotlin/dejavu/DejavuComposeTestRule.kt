package dejavu

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import dejavu.internal.Runtime
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A test rule that wraps [AndroidComposeTestRule] with automatic Dejavu
 * lifecycle management. Enables Dejavu and resets recomposition counts
 * before each test, so no `@Before` boilerplate is needed.
 */
public class DejavuComposeTestRule<A : ComponentActivity>(
    private val delegate: AndroidComposeTestRule<ActivityScenarioRule<A>, A>
) : ComposeContentTestRule by delegate {

    /** Provides access to the underlying activity instance. */
    public val activity: A get() = delegate.activity

    override fun apply(base: Statement, description: Description): Statement {
        val trackedTest = delegate.apply(object : Statement() {
            override fun evaluate() {
                // Setup touches Choreographer (via Runtime.seedActiveActivity), which
                // requires a thread with a Looper. The wrapped Statement runs on
                // compose-ui-test's TestDispatcher coroutine thread (no Looper), so
                // hop to the instrumentation main thread for setup.
                delegate.runOnUiThread {
                    Runtime.seedActiveActivity(delegate.activity)
                }
                delegate.waitForIdle()
                DejavuTest.resetCounts()
                base.evaluate()
            }
        }, description)
        return object : Statement() {
            override fun evaluate() {
                // Install tracking before ActivityScenario launches the activity. Enabling
                // it afterward can miss the initial inspection-table registration.
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                instrumentation.runOnMainSync {
                    Dejavu.enable(instrumentation.targetContext.applicationContext as Application)
                }
                trackedTest.evaluate()
            }
        }
    }
}

/**
 * Creates a [DejavuComposeTestRule] that automatically enables Dejavu and
 * resets recomposition counts before each test. Drop-in replacement for
 * [createAndroidComposeRule].
 */
@JvmName("createRecompositionTrackingRuleForActivity")
public inline fun <reified A : ComponentActivity> createRecompositionTrackingRule():
    DejavuComposeTestRule<A> = DejavuComposeTestRule(createAndroidComposeRule())

/**
 * Creates a [DejavuComposeTestRule] backed by a plain [ComponentActivity].
 * Drop-in replacement for [androidx.compose.ui.test.junit4.createComposeRule]
 * with automatic Dejavu lifecycle management.
 */
public fun createRecompositionTrackingRule():
    DejavuComposeTestRule<ComponentActivity> = DejavuComposeTestRule(createAndroidComposeRule())

/** Resets recomposition counts to zero while preserving composition history. */
public fun ComposeTestRule.resetRecompositionCounts() {
    DejavuTest.resetCounts()
}

/**
 * Returns the recomposition count for the composable identified by [tag].
 * Refreshes the tag-to-function mapping before lookup.
 *
 * @param tag the `Modifier.testTag()` value identifying the composable
 */
public fun ComposeTestRule.getRecompositionCount(tag: String): Int {
    val snapshots = dejavu.internal.DejavuTracer.getCompositionSnapshots()
    if (snapshots.isNotEmpty()) {
        dejavu.internal.DejavuTracer.refreshTagMapping(snapshots)
    }
    val functionName = dejavu.internal.DejavuTracer.getFunctionNameForTag(tag)
    // Use per-tag fingerprint counting only for multi-instance functions
    if (functionName != null && dejavu.internal.DejavuTracer.isMultiInstanceFunction(functionName)) {
        val perTagCount = dejavu.internal.DejavuTracer.getPerTagRecompositionCount(tag)
        if (perTagCount != null) return perTagCount
    }
    return if (functionName != null) {
        dejavu.internal.DejavuTracer.getRecompositionCount(functionName)
    } else {
        dejavu.internal.DejavuTracer.getRecompositionCount(tag)
    }
}
