@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import dejavu.assertRecompositions
import dejavu.assertStable
import dejavu.resetRecompositionCounts
import dejavu.runRecompositionTrackingUiTest
import dejavu.setTrackedContent
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private var compositions = 0

class PublishedArtifactTest {
    @Test
    fun publishedArtifactTracksAcrossSuspension() = runRecompositionTrackingUiTest {
        val value = mutableIntStateOf(0)
        compositions = 0
        setTrackedContent { PublishedValue(value.intValue) }
        waitForIdle()
        val baseline = compositions
        resetRecompositionCounts()
        yield()
        runOnIdle { value.intValue++ }
        waitForIdle()
        assertEquals(1, compositions - baseline)
        onNodeWithTag("published_value").assertRecompositions(exactly = compositions - baseline)
        assertFailsWith<AssertionError> { onNodeWithTag("published_value").assertStable() }
    }
}

@Composable
private fun PublishedValue(value: Int) {
    SideEffect { compositions++ }
    BasicText("Value: $value", Modifier.testTag("published_value"))
}
