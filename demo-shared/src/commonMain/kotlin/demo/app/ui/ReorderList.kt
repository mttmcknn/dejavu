package demo.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import demo.app.GroundTruthCounters
import kotlinx.coroutines.launch
import kotlin.random.Random

private val ORIGINAL_ORDER = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot")

@Composable
fun ReorderListScreen() {
  SideEffect { GroundTruthCounters.increment("reorder_list_root") }
  val items = remember { mutableStateListOf(*ORIGINAL_ORDER.toTypedArray()) }

  Column(modifier = Modifier.testTag("reorder_list_root")) {
    ListOrderLabel(items.joinToString(", "))
    StaticReorderLabel()
    Row {
      ShuffleButton {
        if (items.size > 1) {
          val offset = Random.nextInt(1, items.size)
          val shifted = items.drop(offset) + items.take(offset)
          items.clear()
          items.addAll(shifted)
        }
      }
      Spacer(modifier = Modifier.width(8.dp))
      SwapFirstTwoButton {
        if (items.size >= 2) {
          val temp = items[0]
          items[0] = items[1]
          items[1] = temp
        }
      }
      Spacer(modifier = Modifier.width(8.dp))
      ResetOrderButton {
        items.clear()
        items.addAll(ORIGINAL_ORDER)
      }
    }
    LazyColumn(modifier = Modifier.weight(1f)) {
      itemsIndexed(items, key = { _, item -> item }) { index, item ->
        ReorderableItem(index, item)
      }
    }
  }
}

@Composable
fun ReorderableItem(index: Int, text: String) {
  val tag = "reorderable_item_$index"
  SideEffect { GroundTruthCounters.increment(tag) }
  Row(
    modifier = Modifier
      .testTag(tag)
      .fillMaxWidth()
      .padding(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    ItemHandle(index)
    Spacer(modifier = Modifier.width(12.dp))
    ItemLabel(index, text)
  }
}

@Composable
fun ItemHandle(index: Int) {
  val tag = "item_handle_$index"
  SideEffect { GroundTruthCounters.increment(tag) }
  val offsetY = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()
  Text(
    text = "\u2261",
    modifier = Modifier
      .testTag(tag)
      .graphicsLayer { translationY = offsetY.value }
      .pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
          onDrag = { change, dragAmount ->
            change.consume()
            scope.launch { offsetY.snapTo(offsetY.value + dragAmount.y) }
          },
          onDragEnd = {
            scope.launch { offsetY.animateTo(0f) }
          },
          onDragCancel = {
            scope.launch { offsetY.animateTo(0f) }
          }
        )
      }
      .padding(8.dp)
  )
}

@Composable
fun ItemLabel(index: Int, text: String) {
  val tag = "item_label_$index"
  SideEffect { GroundTruthCounters.increment(tag) }
  Text(
    text = text,
    modifier = Modifier
      .testTag(tag)
      .padding(8.dp)
  )
}

@Composable
fun ListOrderLabel(order: String) {
  SideEffect { GroundTruthCounters.increment("list_order_label") }
  Text(
    text = order,
    modifier = Modifier
      .testTag("list_order_label")
      .padding(16.dp)
  )
}

@Composable
fun ShuffleButton(onClick: () -> Unit) {
  SideEffect { GroundTruthCounters.increment("shuffle_btn") }
  Button(onClick = onClick, modifier = Modifier.testTag("shuffle_btn")) {
    Text("Shuffle")
  }
}

@Composable
fun SwapFirstTwoButton(onClick: () -> Unit) {
  SideEffect { GroundTruthCounters.increment("swap_first_two_btn") }
  Button(onClick = onClick, modifier = Modifier.testTag("swap_first_two_btn")) {
    Text("Swap 1-2")
  }
}

@Composable
fun ResetOrderButton(onClick: () -> Unit) {
  SideEffect { GroundTruthCounters.increment("reset_order_btn") }
  Button(onClick = onClick, modifier = Modifier.testTag("reset_order_btn")) {
    Text("Reset Order")
  }
}

@Composable
fun StaticReorderLabel() {
  SideEffect { GroundTruthCounters.increment("static_reorder_label") }
  Text(
    text = "Drag to reorder",
    modifier = Modifier
      .testTag("static_reorder_label")
      .padding(8.dp)
  )
}
