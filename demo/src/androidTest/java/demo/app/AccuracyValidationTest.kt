package demo.app

import android.util.Log
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dejavu.DejavuComposeTestRule
import dejavu.assertStable
import dejavu.createRecompositionTrackingRule
import dejavu.getRecompositionCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// ============================================================
// Accuracy Validation Tests
//
// Each test class exercises one Activity's composables, then
// compares Dejavu's recomposition counts against the ground-
// truth counters (SideEffect-based). A mismatch fails the test
// with a detailed diff.
//
// Ground truth formula: recompositions = SideEffect count
// because GroundTruthCounters.reset() runs in @Before AFTER the
// activity launches, clearing the initial composition's SideEffect
// call. Post-reset, every SideEffect call is from a recomposition.
// ============================================================

private fun validateAccuracy(
    rule: DejavuComposeTestRule<*>,
    tags: List<String>,
    label: String,
) {
    rule.waitForIdle()
    val mismatches = mutableListOf<String>()
    for (tag in tags) {
        val gt = GroundTruthCounters.get(tag)
        val dv = rule.getRecompositionCount(tag)
        Log.d("AccuracyValidation", "[$label] $tag: groundTruth=$gt, dejavu=$dv")
        if (gt != dv) mismatches.add("$tag: groundTruth=$gt, dejavu=$dv")
    }
    if (mismatches.isNotEmpty()) {
        throw AssertionError(
            "Accuracy mismatch [$label]:\n${mismatches.joinToString("\n") { "  $it" }}"
        )
    }
}

/**
 * These root tags belong to a CompositionLocalProvider content lambda, while their
 * SideEffect counters sit in the enclosing named screen body. Keep that independent
 * screen-body oracle without presenting it as an exact count for the anonymous owner.
 */
private fun validateAnonymousProviderRoot(
    rule: DejavuComposeTestRule<*>,
    tag: String,
    screenBodyBaseline: Int,
) {
    rule.onNodeWithTag(tag).assertExists()
    assertEquals(
        "Only the provider value change should recompose the named screen body for $tag",
        1,
        GroundTruthCounters.get(tag) - screenBodyBaseline,
    )
    val countError = runCatching { rule.getRecompositionCount(tag) }.exceptionOrNull()
    assertTrue("Expected an unavailable lambda count for $tag", countError is AssertionError)
    assertTrue(countError?.message.orEmpty().contains("testTag='$tag'"))
    assertTrue(countError?.message.orEmpty().contains("includes a lambda owner"))
    val stableError = runCatching { rule.onNodeWithTag(tag).assertStable() }.exceptionOrNull()
    assertEquals(countError?.message, stableError?.message)
}

// ============================================================
// 1. Counter (CounterActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class CounterAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<CounterActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun counterAccuracy() {
        // 3 increments + 1 reset
        repeat(3) {
            rule.onNodeWithTag("inc_button").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("reset_button").performClick()
        rule.waitForIdle()

        validateAccuracy(
            rule,
            listOf("counter_title", "counter_value", "inc_button", "reset_button"),
            "Counter",
        )
    }
}

// ============================================================
// 2. ProductList (ProductActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class ProductListAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<ProductActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun productListAccuracy() {
        // 2 selects + 1 refresh
        rule.onNodeWithTag("select_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("select_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("refresh_button").performClick()
        rule.waitForIdle()

        validateAccuracy(
            rule,
            listOf(
                "product_header",
                "optimized_header",
                "cart_banner",
                "product_footer",
                "total_display",
                "refresh_indicator",
                "product_a",
                "product_b",
            ),
            "ProductList",
        )
    }
}

// ============================================================
// 3. LazyList (LazyListStressActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class LazyListAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<LazyListStressActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun lazyListAccuracy() {
        // Select item 0, then select all, then clear
        rule.onNodeWithTag("select_0_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("select_all_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("clear_btn").performClick()
        rule.waitForIdle()

        // Excluded: "item_0" through "item_*" — all map to the ListItem
        // composable function, so Dejavu's per-qualified-name counter is shared
        // across all instances. Ground truth is per-tag, causing a mismatch.
        // Only validate tags that map to unique composable functions.
        validateAccuracy(
            rule,
            listOf(
                "lazy_stress_root",
                "list_header",
                "list_footer",
                "selected_banner",
                "derived_banner",
                "select_0_btn",
                "select_all_btn",
                "clear_btn",
            ),
            "LazyList",
        )
    }
}

// ============================================================
// 4. DeepNesting (DeepNestingStressActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class DeepNestingAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<DeepNestingStressActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun deepNestingAccuracy() {
        // Click increment 3 times
        repeat(3) {
            rule.onNodeWithTag("deep_inc_btn").performClick()
            rule.waitForIdle()
        }

        validateAccuracy(
            rule,
            listOf(
                "deep_root",
                "level_1",
                "level_2",
                "level_3",
                "level_4",
                "level_5",
                "level_6",
                "sibling_branch",
                "sibling_child",
            ),
            "DeepNesting",
        )
    }
}

// ============================================================
// 5. SharedState (SharedStateStressActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class SharedStateAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<SharedStateStressActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun sharedStateAccuracy() {
        rule.waitForIdle()
        val screenBodyBaseline = GroundTruthCounters.get("shared_root")
        // Inc A, Inc B, Inc C, toggle theme
        rule.onNodeWithTag("inc_a_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("inc_b_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("inc_c_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("toggle_theme_btn").performClick()
        rule.waitForIdle()

        // Only themeMode is read outside the provider's content lambda; A/B/C reads
        // occur inside it. The named screen's SideEffect must therefore execute once.
        validateAnonymousProviderRoot(rule, "shared_root", screenBodyBaseline)
        validateAccuracy(
            rule,
            listOf(
                "reader_a",
                "reader_b",
                "reader_c",
                "dual_reader",
                "theme_reader",
                "all_reader",
                "inc_a_btn",
                "inc_b_btn",
                "inc_c_btn",
                "toggle_theme_btn",
            ),
            "SharedState",
        )
    }
}

// ============================================================
// 6. AnimationStress (AnimationStressActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class AnimationStressAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<AnimationStressActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun animationStressAccuracy() {
        rule.onNodeWithTag("pulse_banner_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("pulse_banner_btn").performClick()
        rule.waitForIdle()

        validateAccuracy(
            rule,
            listOf(
                "anim_stress_root",
                "toggle_vis_btn",
                "cycle_content_btn",
                "toggle_cond_btn",
                "pulse_banner_btn",
                "animating_banner",
                "static_label",
            ),
            "AnimationStress",
        )
    }
}

// ============================================================
// 7. FlowState (FlowStateActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class FlowStateAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<FlowStateActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun flowStateAccuracy() {
        rule.onNodeWithTag("flow_inc_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("real_value_write_btn").performClick()
        rule.waitForIdle()

        validateAccuracy(
            rule,
            listOf(
                "flow_root",
                "flow_counter",
                "same_value_reader",
                "coalescing_reader",
                "list_size_reader",
                "batch_reader_a",
                "batch_reader_b",
                "flow_inc_btn",
                "flow_inc_three_btn",
                "same_value_write_btn",
                "real_value_write_btn",
                "add_item_btn",
                "coalesce_btn",
                "batch_btn",
            ),
            "FlowState",
        )
    }
}

// ============================================================
// 8. KeyIdentity (KeyIdentityActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class KeyIdentityAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<KeyIdentityActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun keyIdentityAccuracy() {
        rule.onNodeWithTag("inc_base_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("inc_base_twice_btn").performClick()
        rule.waitForIdle()

        validateAccuracy(
            rule,
            listOf(
                "key_root",
                "keyed_child",
                "saveable_child",
                "loop_count_label",
                "derived_chain_a",
                "derived_chain_b",
                "change_key_btn",
                "inc_base_btn",
                "inc_base_twice_btn",
                "add_loop_btn",
            ),
            "KeyIdentity",
        )
    }
}

// ============================================================
// 9. DialogPopup (DialogPopupActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class DialogPopupAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<DialogPopupActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun dialogPopupAccuracy() {
        rule.waitForIdle()
        val screenBodyBaseline = GroundTruthCounters.get("dialog_root")
        rule.onNodeWithTag("change_static_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("change_dynamic_btn").performClick()
        rule.waitForIdle()

        // staticConfig is read outside the provider, but dynamicValue is read inside
        // its content lambda. The named screen's separate SideEffect executes once.
        validateAnonymousProviderRoot(rule, "dialog_root", screenBodyBaseline)
        validateAccuracy(
            rule,
            listOf(
                "static_reader_a",
                "static_reader_b",
                "static_reader_c",
                "dynamic_reader_d",
                "unrelated_static",
                "show_dialog_btn",
                "show_popup_btn",
                "change_static_btn",
                "change_dynamic_btn",
            ),
            "DialogPopup",
        )
    }
}

// ============================================================
// 10. Subcompose (SubcomposeActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class SubcomposeAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<SubcomposeActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun subcomposeAccuracy() {
        rule.onNodeWithTag("trigger_parent_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("toggle_width_btn").performClick()
        rule.waitForIdle()

        validateAccuracy(
            rule,
            listOf(
                "subcompose_root",
                "movable_child",
                "non_restartable",
                "regular_child",
                "toggle_width_btn",
                "animate_btn",
                "move_content_btn",
                "trigger_parent_btn",
            ),
            "Subcompose",
        )
    }
}

// ============================================================
// 11. InputScroll (InputScrollActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class InputScrollAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<InputScrollActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun inputScrollAccuracy() {
        rule.onNodeWithTag("type_char_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("change_source_btn").performClick()
        rule.waitForIdle()

        validateAccuracy(
            rule,
            listOf(
                "input_scroll_root",
                "text_display",
                "text_unrelated",
                "scroll_position",
                "flow_reader",
                "type_char_btn",
                "scroll_down_btn",
                "change_source_btn",
                "same_source_btn",
            ),
            "InputScroll",
        )
    }
}

// ============================================================
// 12. LazyVariants (LazyVariantsActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class LazyVariantsAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<LazyVariantsActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun lazyVariantsAccuracy() {
        rule.onNodeWithTag("select_row_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("select_grid_btn").performClick()
        rule.waitForIdle()

        validateAccuracy(
            rule,
            listOf(
                "lazy_variants_root",
                "row_selection_count",
                "grid_highlight_count",
                "select_row_btn",
                "clear_row_btn",
                "select_grid_btn",
                "clear_grid_btn",
            ),
            "LazyVariants",
        )
    }
}

// ============================================================
// 13. PagerCrossfade (PagerCrossfadeActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class PagerCrossfadeAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<PagerCrossfadeActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun pagerCrossfadeAccuracy() {
        rule.onNodeWithTag("cycle_crossfade_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("reset_crossfade_btn").performClick()
        rule.waitForIdle()

        validateAccuracy(
            rule,
            listOf(
                "pager_crossfade_root",
                "pager_indicator",
                "crossfade_label",
                "pager_sibling",
                "next_page_btn",
                "cycle_crossfade_btn",
                "reset_crossfade_btn",
            ),
            "PagerCrossfade",
        )
    }
}

// ============================================================
// 14. AdvancedPatterns (AdvancedPatternsActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class AdvancedPatternsAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<AdvancedPatternsActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun advancedPatternsAccuracy() {
        rule.onNodeWithTag("change_outer_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("change_inner_btn").performClick()
        rule.waitForIdle()

        validateAccuracy(
            rule,
            listOf(
                "advanced_root",
                "outer_reader",
                "inner_reader",
                "outer_reader_b",
                "custom_layout_child",
                "deferred_read",
                "remember_key_child",
                "effect_restart",
                "change_outer_btn",
                "change_inner_btn",
                "change_key_btn_adv",
                "toggle_vis_adv_btn",
                "change_effect_key_btn",
            ),
            "AdvancedPatterns",
        )
    }
}

// ============================================================
// 15. ScaffoldSlots (ScaffoldSlotsActivity)
// ============================================================

@RunWith(AndroidJUnit4::class)
class ScaffoldSlotsAccuracyTest {

    @get:Rule
    val rule = createRecompositionTrackingRule<ScaffoldSlotsActivity>()

    @Before
    fun setup() {
        GroundTruthCounters.reset()
    }

    @Test
    fun scaffoldSlotsAccuracy() {
        rule.onNodeWithTag("change_top_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("change_content_btn").performClick()
        rule.waitForIdle()

        validateAccuracy(
            rule,
            listOf(
                "scaffold_top_bar",
                "scaffold_bottom_bar",
                "scaffold_fab",
                "scaffold_content",
                "content_body",
                "content_static",
                "fab_click_label",
                "change_top_btn",
                "change_bottom_btn",
                "change_content_btn",
            ),
            "ScaffoldSlots",
        )
    }
}
