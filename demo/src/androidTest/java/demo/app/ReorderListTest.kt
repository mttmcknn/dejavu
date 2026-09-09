package demo.app

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dejavu.assertRecompositions
import dejavu.assertStable
import dejavu.createRecompositionTrackingRule
import dejavu.resetRecompositionCounts
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReorderListTest {

    @get:Rule
    val composeTestRule = createRecompositionTrackingRule<ReorderListActivity>()

    @Test
    fun reorder_swap_affects_first_two_items() {
        composeTestRule.onNodeWithTag("swap_first_two_btn").performClick()
        composeTestRule.waitForIdle()
        // Swapping items[0] and items[1] re-emits the loop with index 0↔1. Items are content-keyed
        // (key = item string), so the two swapped compositions persist but their `index` param flips,
        // changing the tag each now carries: after the swap reorderable_item_0 is the formerly-index-1
        // composition and reorderable_item_1 the formerly-index-0 one. Per-tag tracking (Android-only)
        // resolves the count by the tag currently present, so each of the two changed slots = 1.
        // 1: index param changed once for the composition now carrying this tag.
        composeTestRule.onNodeWithTag("reorderable_item_0").assertRecompositions(exactly = 1)
        // 1: index param changed once for the composition now carrying this tag.
        composeTestRule.onNodeWithTag("reorderable_item_1").assertRecompositions(exactly = 1)
    }

    @Test
    fun reorder_swap_other_items_stable() {
        composeTestRule.onNodeWithTag("swap_first_two_btn").performClick()
        composeTestRule.waitForIdle()
        // Only items 0 and 1 change index; items 2..5 keep their content key AND their index param,
        // so their compositions skip. Content-keyed lazy items do not over-recompose from a structural
        // swap (the keyed item identities are preserved), so each non-swapped slot is exactly stable.
        // 0: index and text unchanged for these slots — they skip.
        composeTestRule.onNodeWithTag("reorderable_item_2").assertStable()
        composeTestRule.onNodeWithTag("reorderable_item_3").assertStable()
        composeTestRule.onNodeWithTag("reorderable_item_4").assertStable()
        composeTestRule.onNodeWithTag("reorderable_item_5").assertStable()
    }

    @Test
    fun reorder_shuffle_affects_all_items() {
        composeTestRule.onNodeWithTag("shuffle_btn").performClick()
        composeTestRule.waitForIdle()
        // Shuffle uses a random non-zero cyclic shift. The offset varies, but every content-keyed
        // item changes index exactly once, so every current slot has one recomposition.
        for (index in 0 until 6) {
            composeTestRule.onNodeWithTag("reorderable_item_$index")
                .assertRecompositions(exactly = 1)
        }
    }

    @Test
    fun reorder_order_label_recomposes_on_mutation() {
        composeTestRule.onNodeWithTag("swap_first_two_btn").performClick()
        composeTestRule.waitForIdle()
        // The swap changes items.joinToString(", "), so the label's String param changes once and it
        // recomposes once. list_order_label is single-instance (one call site, unique key) so its
        // per-tag count resolves exactly on every platform.
        // 1: the joined order string changes exactly once on a single swap.
        composeTestRule.onNodeWithTag("list_order_label").assertRecompositions(exactly = 1)
    }

    @Test
    fun reorder_reset_restores_order() {
        composeTestRule.onNodeWithTag("swap_first_two_btn").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.resetRecompositionCounts()

        composeTestRule.onNodeWithTag("reset_order_btn").performClick()
        composeTestRule.waitForIdle()
        // Reset restores the original order string after a deterministic swap, changing the label once.
        // 1: the joined order string changes once when the list returns to its original order.
        composeTestRule.onNodeWithTag("list_order_label").assertRecompositions(exactly = 1)
    }

    @Test
    fun reorder_static_label_stable() {
        composeTestRule.onNodeWithTag("shuffle_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("swap_first_two_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("static_reorder_label").assertStable()
    }

    @Test
    fun reorder_no_recomposition_without_interaction() {
        composeTestRule.onNodeWithTag("list_order_label").assertStable()
        composeTestRule.onNodeWithTag("static_reorder_label").assertStable()
        composeTestRule.onNodeWithTag("reorderable_item_0").assertStable()
    }
}
