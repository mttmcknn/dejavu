package dejavu.experimental

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Module-local ground-truth recomposition counter for the compose-experimental tests.
 *
 * Each tracked composable calls [record] from a `SideEffect`, which the Compose runtime
 * invokes after every successful composition (initial + each recomposition). By snapshotting
 * a [baseline] right after [dejavu.resetRecompositionCounts] (the point at which every node
 * already exists), the [delta] of a tag's count over the baseline equals the number of
 * *recompositions* the runtime actually ran for that node after the reset — the same quantity
 * Dejavu reports via `assertRecompositions`.
 *
 * This lets the regression tests assert `exactly = GroundTruth.delta(tag)`, proving the tracer
 * count equals the runtime's real recomposition count for that node — not merely a direction.
 *
 * Mirrors the `SideEffect`-as-ground-truth invariant in dejavu's own `SideEffectAccuracyTest`,
 * but uses only the public Dejavu API (the regression module cannot touch `DejavuTracer`).
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

    /** Freeze the current counts as the baseline, aligning with Dejavu's reset point. */
    fun snapshotBaseline() {
        synchronized(lock) {
            baseline.clear()
            baseline.putAll(counts)
        }
    }

    /** Recompositions of [tag] since the last [snapshotBaseline] (== Dejavu post-reset count). */
    fun delta(tag: String): Int = synchronized(lock) {
        (counts[tag] ?: 0) - (baseline[tag] ?: 0)
    }

    /** Reset all state. Call at the start of every test. */
    fun clear() {
        synchronized(lock) {
            counts.clear()
            baseline.clear()
        }
    }
}
