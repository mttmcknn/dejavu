package dejavu.consumer

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import dejavu.assertRecompositions
import dejavu.assertStable
import dejavu.resetRecompositionCounts
import dejavu.runRecompositionTrackingUiTest
import dejavu.setTrackedContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalTestApi::class)
class PublishedDependencyTest {
    @Test
    fun packagedArtifactTracksWithConsumerComposeAndSuppliesTooling() = runRecompositionTrackingUiTest {
        // Missing tooling can fall back to common tag mapping. Check it is actually
        // present so a green assertion cannot hide a broken runtime dependency.
        Class.forName("androidx.compose.ui.tooling.data.Group")
        val value = mutableIntStateOf(0)
        val compositions = AtomicInteger()
        setTrackedContent { PublishedValue(value.intValue, compositions) }
        waitForIdle()
        val baseline = compositions.get()
        resetRecompositionCounts()
        repeat(3) {
            runOnIdle { value.intValue++ }
            waitForIdle()
        }
        onNodeWithTag("published_value").assertTextEquals("Value: 3")
        assertEquals(3, compositions.get() - baseline)
        onNodeWithTag("published_value").assertRecompositions(exactly = compositions.get() - baseline)
        assertThrows(AssertionError::class.java) { onNodeWithTag("published_value").assertStable() }
    }
}

@Composable
private fun PublishedValue(value: Int, compositions: AtomicInteger) {
    SideEffect { compositions.incrementAndGet() }
    BasicText("Value: $value", Modifier.testTag("published_value"))
}
