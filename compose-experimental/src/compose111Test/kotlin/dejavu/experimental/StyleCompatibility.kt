@file:OptIn(androidx.compose.foundation.style.ExperimentalFoundationStyleApi::class)

package dejavu.experimental

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleScope
import androidx.compose.foundation.style.pressed
import androidx.compose.ui.graphics.Color

// The experimental pressed API takes a Style in 1.11 and a block in 1.12.
internal fun StyleScope.dejavuPressedStyle() {
    pressed(object : Style {
        override fun StyleScope.applyStyle() { background(Color.Red) }
    })
}
