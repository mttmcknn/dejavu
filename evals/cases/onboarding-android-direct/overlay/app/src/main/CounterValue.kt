package example
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
@Composable
fun CounterValue(value: Int) {
    BasicText("Value: $value", modifier = Modifier.testTag("counter_value"))
}
