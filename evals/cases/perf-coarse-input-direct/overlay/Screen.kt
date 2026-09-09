package example
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
@Composable fun Screen(scrollIndex: State<Int>) {
    Column {
        Header(scrollIndex.value)
        StaticLabel()
    }
}
@Composable fun Header(scrollIndex: Int) {
    BasicText(if (scrollIndex > 0) "Back to top" else "At top", Modifier.testTag("header"))
}
@Composable fun StaticLabel() { BasicText("Help", Modifier.testTag("static")) }
