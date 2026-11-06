package dejavu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import dejavu.internal.DejavuTracer
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.synchronized
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Missing or ambiguous ownership must never return an apparently exact count. */
@OptIn(ExperimentalTestApi::class)
open class ExactLambdaBoundaryTest {
    @Test
    fun clearedInspectionTables_invalidateAnAlreadyVerifiedLambdaCount() = runRecompositionTrackingUiTest {
        val value = mutableIntStateOf(0)
        val probe = BoundaryProbe("cleared", "Tracked", value)
        val registry = boundaryRegistry()
        setTrackedContent { registry.getValue("value")(probe) }
        waitForIdle()
        val baseline = probe.executionCount()
        resetRecompositionCounts()

        repeat(3) {
            runOnIdle { value.intValue++ }
            waitForIdle()
        }
        onNodeWithTag("cleared").assertTextEquals("Tracked:3")
        assertEquals(3, probe.executionCount() - baseline)
        onNodeWithTag("cleared").assertRecompositions(exactly = 3)
        assertFailsWith<UnexpectedRecompositionsError> {
            onNodeWithTag("cleared").assertRecompositions(atMost = 2)
        }

        // Leave the live UI and counters intact, but remove the only ownership
        // evidence. A previously cached mapping must not survive this boundary.
        runOnIdle {
            synchronized(DejavuTracer.inspectionTablesLock) {
                DejavuTracer.inspectionTables.clear()
            }
        }
        onNodeWithTag("cleared").assertTextEquals("Tracked:3")
        assertEquals(3, probe.executionCount() - baseline)
        onNodeWithTag("cleared").assertCountUnavailable("cleared", expected = 3)
    }

    @Test
    fun uncapturedSubcomposition_doesNotBorrowTheCapturedParentsCount() = runRecompositionTrackingUiTest {
        val value = mutableIntStateOf(0)
        val child = BoundaryProbe("subcomposed", "Child", value)
        val registry = boundaryRegistry()
        val parentExecutions = atomic(0)
        setTrackedContent {
            Column {
                SideEffect { parentExecutions.incrementAndGet() }
                BasicText("Captured parent", Modifier.testTag("captured_parent"))
                // This independently created child composer has no explicit
                // Dejavu capture boundary, even though inspection tables inherit.
                SubcomposeLayout { constraints ->
                    val placeables = subcompose("uncaptured_child") {
                        registry.getValue("value")(child)
                    }.map { it.measure(constraints) }
                    layout(
                        placeables.maxOfOrNull { it.width } ?: 0,
                        placeables.maxOfOrNull { it.height } ?: 0,
                    ) {
                        placeables.forEach { it.place(0, 0) }
                    }
                }
            }
        }
        waitForIdle()
        val childBaseline = child.executionCount()
        val parentBaseline = parentExecutions.value
        resetRecompositionCounts()

        repeat(3) {
            runOnIdle { value.intValue++ }
            waitForIdle()
        }

        onNodeWithTag("subcomposed").assertTextEquals("Child:3")
        onNodeWithTag("captured_parent").assertTextEquals("Captured parent")
        assertEquals(3, child.executionCount() - childBaseline)
        assertEquals(0, parentExecutions.value - parentBaseline)
        onNodeWithTag("subcomposed").assertCountUnavailable("subcomposed", expected = 3)
    }

    @Test
    fun duplicateTagAcrossNamedAndAnonymousBodies_isUnavailableForBothNodes() = runRecompositionTrackingUiTest {
        val value = mutableIntStateOf(0)
        val named = BoundaryProbe("duplicate", "Named", mutableIntStateOf(0))
        val anonymous = BoundaryProbe("duplicate", "Anonymous", value)
        val registry = boundaryRegistry()
        setTrackedContent {
            Column {
                NamedBoundaryBody(named)
                registry.getValue("value")(anonymous)
            }
        }
        waitForIdle()
        val namedBaseline = named.executionCount()
        val anonymousBaseline = anonymous.executionCount()
        resetRecompositionCounts()

        repeat(3) {
            runOnIdle { value.intValue++ }
            waitForIdle()
        }

        val nodes = onAllNodesWithTag("duplicate", useUnmergedTree = true)
        nodes.assertCountEquals(2)
        nodes[0].assertTextEquals("Named:0")
        nodes[1].assertTextEquals("Anonymous:3")
        assertEquals(0, named.executionCount() - namedBaseline)
        assertEquals(3, anonymous.executionCount() - anonymousBaseline)
        // Resolving an individual semantics node must not hide the fact that the
        // tag-level mapping cannot choose between these distinct owners.
        nodes[0].assertCountUnavailable("duplicate", expected = 0)
        nodes[1].assertCountUnavailable("duplicate", expected = 3)
    }
}

@Stable
private class BoundaryProbe(
    val tag: String,
    val label: String,
    val state: State<Int>,
) {
    private val executions = atomic(0)

    fun recordExecution() { executions.incrementAndGet() }
    fun executionCount(): Int = executions.value
}

private fun boundaryRegistry(): Map<String, @Composable (BoundaryProbe) -> Unit> = mapOf(
    "value" to { probe ->
        val value = probe.state.value
        SideEffect { probe.recordExecution() }
        BasicText("${probe.label}:$value", Modifier.testTag(probe.tag))
    },
)

@Composable
private fun NamedBoundaryBody(probe: BoundaryProbe) {
    val value = probe.state.value
    SideEffect { probe.recordExecution() }
    BasicText("${probe.label}:$value", Modifier.testTag(probe.tag))
}

private fun SemanticsNodeInteraction.assertCountUnavailable(tag: String, expected: Int) {
    val exactFailure = assertFailsWith<UnexpectedRecompositionsError> {
        assertRecompositions(exactly = expected)
    }
    assertContains(exactFailure.message.orEmpty(), "Recomposition count unavailable")
    assertContains(exactFailure.message.orEmpty(), "testTag='$tag'")
    val stableFailure = assertFailsWith<UnexpectedRecompositionsError> { assertStable() }
    assertContains(stableFailure.message.orEmpty(), "Recomposition count unavailable")
    assertContains(stableFailure.message.orEmpty(), "testTag='$tag'")
}
