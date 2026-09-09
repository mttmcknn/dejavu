package dejavu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Cross-platform port of Android ErrorMessageValidationTest.
 * Validates that assertion error messages contain structured diagnostic
 * information: header, expected/actual, source location, composable list,
 * timeline, parent info, causality, and semantic tree.
 */
@OptIn(ExperimentalTestApi::class)
class ErrorMessagePatternTest {

    @BeforeTest
    fun setUp() {
        enableDejavuForTest()
    }

    @AfterTest
    fun tearDown() {
        disableDejavuForTest()
    }

    /**
     * Triggers 3 recompositions on the test_header node by clicking
     * the select button 3 times, then asserts exactly=1 to produce
     * a structured error message.
     */
    private fun ComposeUiTest.captureErrorMessage(): String {
        setContent { DejavuTestContent { ErrorTestScreen() } }
        waitForIdle()
        repeat(3) {
            onNodeWithTag("select_button").performClick()
            waitForIdle()
        }
        return assertFailsWith<AssertionError> {
            onNodeWithTag("test_header").assertRecompositions(exactly = 1)
        }.message.orEmpty()
    }

    @Test
    fun errorMessage_containsHeaderAndExpectedActual() = runComposeUiTest {
        val msg = captureErrorMessage()
        assertTrue(
            msg.contains("Recomposition assertion failed for testTag='test_header'"),
            "Should contain header with testTag. Got:\n$msg"
        )
        assertTrue(
            msg.contains("Expected: exactly 1"),
            "Should contain 'Expected: exactly 1'. Got:\n$msg"
        )
        assertTrue(
            msg.contains("Actual: 3"),
            "Should contain 'Actual: 3'. Got:\n$msg"
        )
    }

    @Test
    fun errorMessage_containsSourceLocation() = runComposeUiTest {
        val msg = captureErrorMessage()
        assertTrue(
            msg.contains("ErrorTestHeader"),
            "Should contain composable function name 'ErrorTestHeader'. Got:\n$msg"
        )
    }

    @Test
    fun errorMessage_containsAllTrackedComposables() = runComposeUiTest {
        val msg = captureErrorMessage()
        assertTrue(
            msg.contains("All tracked composables:"),
            "Should contain 'All tracked composables:'. Got:\n$msg"
        )
        assertTrue(
            msg.contains("ErrorTestHeader"),
            "Should list ErrorTestHeader. Got:\n$msg"
        )
        assertTrue(
            msg.contains("<-- FAILED"),
            "Should mark the failed composable with '<-- FAILED'. Got:\n$msg"
        )
    }

    @Test
    fun errorMessage_containsRecompositionTimeline() = runComposeUiTest {
        val msg = captureErrorMessage()
        assertTrue(
            msg.contains("Recomposition timeline:"),
            "Should contain 'Recomposition timeline:'. Got:\n$msg"
        )
        assertTrue(
            msg.contains("#1 at +"),
            "Should contain '#1 at +'. Got:\n$msg"
        )
        assertTrue(
            msg.contains("#2 at +"),
            "Should contain '#2 at +'. Got:\n$msg"
        )
        assertTrue(
            msg.contains("#3 at +"),
            "Should contain '#3 at +'. Got:\n$msg"
        )
        assertTrue(
            msg.contains("param slots changed:"),
            "Should contain 'param slots changed:'. Got:\n$msg"
        )
    }

    @Test
    fun errorMessage_containsParentInfo() = runComposeUiTest {
        val msg = captureErrorMessage()
        assertTrue(
            msg.contains("parent:"),
            "Should contain 'parent:' info. Got:\n$msg"
        )
    }

    @Test
    fun errorMessage_containsCausalityInfo() = runComposeUiTest {
        val msg = captureErrorMessage()
        assertTrue(
            msg.contains("Possible cause:"),
            "Should contain 'Possible cause:'. Got:\n$msg"
        )
        assertTrue(
            msg.contains("Parameter/parent change detected"),
            "Should contain 'Parameter/parent change detected'. Got:\n$msg"
        )
    }

    @Test
    fun errorMessage_containsSemanticTree() = runComposeUiTest {
        val msg = captureErrorMessage()
        assertTrue(
            msg.contains("Node:"),
            "Should contain 'Node:'. Got:\n$msg"
        )
        assertTrue(
            msg.contains("Semantic tree:"),
            "Should contain 'Semantic tree:'. Got:\n$msg"
        )
        assertTrue(
            msg.contains("<-- THIS NODE"),
            "Should contain '<-- THIS NODE'. Got:\n$msg"
        )
    }
}

// ══════════════════════════════════════════════════════════════
// Test Composables
// ══════════════════════════════════════════════════════════════

@Composable
private fun ErrorTestScreen() {
    var selected by remember { mutableIntStateOf(0) }
    Column {
        ErrorTestHeader(selected)
        BasicText("Select", Modifier.testTag("select_button").clickable { selected++ })
    }
}

@Composable
private fun ErrorTestHeader(selected: Int) {
    BasicText("Header: $selected", Modifier.testTag("test_header"))
}
