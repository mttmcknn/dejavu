package dejavu

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Shared `SideEffect`-as-ground-truth recomposition counter for the dejavu pattern tests.
 *
 * Each tracked composable calls [record] from a `SideEffect`, which the Compose runtime invokes
 * after every successful composition (initial + each recomposition). By snapshotting a [baseline]
 * at the point Dejavu's counts are zeroed — right after the initial `waitForIdle()`, and again after
 * any mid-test [resetRecompositionCounts] — the [delta] of a tag's count over that baseline equals
 * the number of *recompositions* the runtime actually ran for that node since the baseline. That is
 * the exact quantity Dejavu reports via `assertRecompositions`.
 *
 * This lets the pattern tests assert `exactly = GroundTruth.delta(tag)`, proving the tracer count
 * equals the runtime's real recomposition count for that node — not merely a direction
 * (`atLeast`/`atMost`). It mirrors the public-API ground-truth helper used by the
 * `compose-experimental` regression tests and the `SideEffect`-as-ground-truth invariant in
 * [SideEffectAccuracyTest] (`dejavuCount == sideEffectCount - 1`).
 *
 * Usage:
 * ```
 * GroundTruth.clear()
 * setContent { DejavuTestContent { Screen() } }
 * waitForIdle()
 * GroundTruth.snapshotBaseline()           // baseline after initial composition
 * // (optional) resetRecompositionCounts(); GroundTruth.snapshotBaseline()  // realign after a reset
 * onNodeWithTag("toggle").performClick()
 * waitForIdle()
 * onNodeWithTag("target").assertRecompositions(exactly = GroundTruth.delta("target"))
 * ```
 *
 * Record by **testTag** for single-instance nodes (so `delta(tag)` lines up with the per-tag
 * assertion). For multi-instance composables whose per-tag counts only resolve on Android, record
 * a function-level key instead and assert against that — see the converted multi-instance tests.
 *
 * Access is synchronized because Android records `SideEffect`s on the main thread while assertions
 * run on the instrumentation thread.
 */
internal object GroundTruth {
    private val lock = SynchronizedObject()
    private val counts = mutableMapOf<String, Int>()
    private val baseline = mutableMapOf<String, Int>()

    /** Record one composition (initial or recomposition) of the node with [tag]. */
    fun record(tag: String) {
        synchronized(lock) {
            counts[tag] = (counts[tag] ?: 0) + 1
        }
    }

    /** Freeze the current counts as the baseline, aligning with Dejavu's reset/zero point. */
    fun snapshotBaseline() {
        synchronized(lock) {
            baseline.clear()
            baseline.putAll(counts)
        }
    }

    /** Recompositions of [tag] since the last [snapshotBaseline] (== Dejavu's post-baseline count). */
    fun delta(tag: String): Int = synchronized(lock) {
        (counts[tag] ?: 0) - (baseline[tag] ?: 0)
    }

    /** Total compositions of [tag] recorded so far (initial + recompositions). */
    fun total(tag: String): Int = synchronized(lock) { counts[tag] ?: 0 }

    /** Reset all state. Call at the start of every test (in the test body or `@BeforeTest`). */
    fun clear() {
        synchronized(lock) {
            counts.clear()
            baseline.clear()
        }
    }
}
