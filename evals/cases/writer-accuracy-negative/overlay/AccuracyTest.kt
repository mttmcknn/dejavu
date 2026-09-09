package example
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import dejavu.*
import kotlin.test.*
@OptIn(ExperimentalTestApi::class)
class AccuracyTest {
    @Test fun deliberatelyRepeated() = runRecompositionTrackingUiTest {
        val value = mutableIntStateOf(0)
        val committed = Counter() // non-snapshot, thread-safe; increment/get API
        setTrackedContent { InefficientValue(value.intValue, committed) }
        waitForIdle()
        val baseline = committed.get()
        resetRecompositionCounts()
        repeat(4) { runOnIdle { value.intValue++ }; waitForIdle() }
        assertEquals(4, committed.get() - baseline)
        onNodeWithTag("inefficient").assertRecompositions(exactly = 4)
        assertFailsWith<UnexpectedRecompositionsError> {
            onNodeWithTag("inefficient").assertStable()
        }
    }
}
// InefficientValue deliberately executes for each update and increments committed
// from unkeyed SideEffect; both this behavior and the expected failure are contracts.
