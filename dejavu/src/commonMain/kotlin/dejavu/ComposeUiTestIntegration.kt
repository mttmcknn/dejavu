package dejavu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.tooling.LocalInspectionTables
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dejavu.internal.DejavuTracer
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.test.TestResult

/**
 * Runs a recomposition-tracking UI test using [runComposeUiTest].
 *
 * This is the KMP equivalent of Android's [createRecompositionTrackingRule][dejavu.DejavuComposeTestRule].
 * It handles all Dejavu lifecycle management automatically:
 *
 * 1. Enables the [DejavuTracer] via [Composer.setTracer]
 * 2. Enables debug inspector info for tag mapping
 * 3. Resets all tracking state
 * 4. Runs the test block inside [runComposeUiTest]
 * 5. Cleans up in a `finally` block
 *
 * Usage:
 * ```
 * @Test
 * fun myComposable_isStable() = runRecompositionTrackingUiTest {
 *     setTrackedContent { MyComposable() }
 *     waitForIdle()
 *     onNodeWithTag("my_tag").assertStable()
 * }
 * ```
 *
 * Return this function's result directly from the test, as in the example. On Wasm it is the
 * asynchronous result awaited by the test runner. Tracking remains enabled across suspensions
 * and is cleaned up only after the test body completes or fails.
 *
 * @param block The suspendable test body, executed within a [ComposeUiTest] receiver.
 * @see setTrackedContent
 */
@OptIn(ExperimentalTestApi::class, InternalComposeTracingApi::class)
public fun runRecompositionTrackingUiTest(
    block: suspend ComposeUiTest.() -> Unit,
): TestResult = runComposeUiTest {
    isDebugInspectorInfoEnabled = true
    DejavuTracer.enabled = true
    Composer.setTracer(DejavuTracer)
    synchronized(DejavuTracer.inspectionTablesLock) { DejavuTracer.inspectionTables.clear() }
    DejavuTest.reset()

    try {
        block()
    } finally {
        DejavuTracer.enabled = false
        Composer.setTracer(null)
        isDebugInspectorInfoEnabled = false
        synchronized(DejavuTracer.inspectionTablesLock) { DejavuTracer.inspectionTables.clear() }
    }
}

/**
 * Sets content wrapped with Dejavu's inspection tables and sub-composition layout.
 *
 * This is the tracked equivalent of [ComposeUiTest.setContent]. It provides
 * [LocalInspectionTables] so that the Compose runtime populates [CompositionData][androidx.compose.runtime.CompositionData]
 * for tag-to-function mapping, and uses [SubcomposeLayout] to create a sub-composition
 * whose `startRoot()` reads the provided tables.
 *
 * Usage:
 * ```
 * runRecompositionTrackingUiTest {
 *     setTrackedContent { MyComposable() }
 *     waitForIdle()
 *     onNodeWithTag("my_tag").assertStable()
 * }
 * ```
 *
 * @param content The composable content to render under tracking.
 * @see runRecompositionTrackingUiTest
 */
@OptIn(ExperimentalTestApi::class)
public fun ComposeUiTest.setTrackedContent(content: @Composable () -> Unit) {
    setContent {
        DejavuTrackedContent(content)
    }
}

/**
 * Resets recomposition counts while preserving the active composition history.
 *
 * Use this after initial composition and before the interaction whose
 * recomposition budget is being asserted.
 */
@OptIn(ExperimentalTestApi::class)
public fun ComposeUiTest.resetRecompositionCounts() {
    DejavuTest.resetCounts()
}

/**
 * Wraps content with [LocalInspectionTables] so that the Compose runtime
 * populates [CompositionData][androidx.compose.runtime.CompositionData] for tag mapping on all platforms.
 *
 * Uses [SubcomposeLayout] to create a sub-composition. The sub-composition's
 * `startRoot()` reads [LocalInspectionTables] from its parent context (this
 * composition), finds our provided set, and adds its [CompositionData][androidx.compose.runtime.CompositionData] to it.
 * Without SubcomposeLayout, the root composition's `startRoot()` reads from the
 * Recomposer (which has no LocalInspectionTables) and never populates the set.
 *
 * Provides [DejavuTracer.inspectionTables] directly so that the assertion API
 * ([assertRecompositions]/[assertStable]) can resolve testTag to function mappings
 * via [DejavuTracer.getCompositionSnapshots] without any manual sync.
 */
@Composable
internal fun DejavuTrackedContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalInspectionTables provides DejavuTracer.inspectionTables) {
        SubcomposeLayout { constraints ->
            val placeables = subcompose(Unit) { content() }
                .map { it.measure(constraints) }
            layout(
                placeables.maxOfOrNull { it.width } ?: 0,
                placeables.maxOfOrNull { it.height } ?: 0
            ) {
                placeables.forEach { it.place(0, 0) }
            }
        }
    }
}
