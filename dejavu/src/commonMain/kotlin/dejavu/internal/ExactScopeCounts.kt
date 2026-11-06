package dejavu.internal

import androidx.compose.runtime.Composer
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.IdentifiableRecomposeScope
import androidx.compose.runtime.tooling.ObservableComposition
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Applied body executions for explicitly captured composers.
 *
 * A scope entry is not an execution: Compose also enters skipped scopes and rereads
 * unchanged derived state. Compiler body traces supply the execution signal. A trace
 * is counted only when its Composer applies the recorded side effect, so an aborted
 * or not-yet-applied composition cannot increase the count.
 *
 * Callers must match BOTH a group's identity and its own compiler key. Nested inline
 * or non-restartable traces run in an ancestor scope and are not that scope's body.
 * The helper's shared observer registry owns observation; this object owns no handles.
 */
@OptIn(ExperimentalComposeRuntimeApi::class, InternalComposeApi::class, ComposeToolingApi::class)
internal object ExactScopeCounts : CompositionObserver {
    private data class Owner(val identity: Any, val key: Int)
    private class Lifetime(val scope: RecomposeScope, val generation: Int) {
        var active = true
    }
    private class Count(val lifetime: Lifetime, var total: Int = 0, var baseline: Int = 0)
    private class Frame(val composition: ObservableComposition, val generation: Int)
    private val lock = SynchronizedObject()
    private val composers = mutableMapOf<ObservableComposition, Composer>()
    private val lifetimes = mutableMapOf<RecomposeScope, Lifetime>()
    private val counts = mutableMapOf<Owner, Count>()
    private val ambiguousOwners = mutableSetOf<Owner>()
    private val frames = PlatformThreadLocal { mutableListOf<Frame>() }
    private var generation = 0
    private var active = false
    private var coverageInvalid = false

    val isActive: Boolean get() = synchronized(lock) { active }

    /** Opens a fresh tracking session. Observer registration is managed separately. */
    fun start() {
        clear()
        synchronized(lock) { active = true }
    }

    /** Captures an explicitly instrumented composer; never borrows a parent's composer. */
    fun attachComposer(composer: Composer) {
        val composition = composer.composition as? ObservableComposition ?: return
        synchronized(lock) {
            if (active && !coverageInvalid) composers[composition] = composer
        }
    }

    /** Called for a compiler body trace selected for exact instance attribution. */
    fun onTrace(key: Int) {
        val frame = frames.get().lastOrNull() ?: return
        val composer = synchronized(lock) {
            if (!active || coverageInvalid || frame.generation != generation) return
            composers[frame.composition] ?: return
        }
        if (composer.composition !== frame.composition) return
        // Unlike currentRecomposeScope, this read does not mark the scope used.
        // The runtime returns null while this composer is composing a child.
        val scope = composer.recomposeScope ?: return
        val identifiable = scope as? IdentifiableRecomposeScope ?: return
        val lifetime = synchronized(lock) {
            if (!active || frame.generation != generation) return
            lifetimes.getOrPut(scope) { Lifetime(scope, generation) }
        }
        composer.recordSideEffect {
            // Initial anchors may be assigned after the scope-exit callback.
            val identity = identifiable.identity ?: return@recordSideEffect
            synchronized(lock) {
                if (!active || coverageInvalid || !lifetime.active || lifetime.generation != generation) return@synchronized
                val owner = Owner(identity, key)
                if (owner in ambiguousOwners) return@synchronized
                val previous = counts[owner]
                if (previous != null && previous.lifetime !== lifetime) {
                    // Two live scopes claiming one owner cannot provide an exact count.
                    counts.remove(owner)
                    ambiguousOwners.add(owner)
                } else {
                    counts.getOrPut(owner) { Count(lifetime) }.total++
                }
            }
        }
    }

    /** Null means no applied, unambiguous scope/body pair has been observed. */
    fun count(identity: Any, key: Int): Int? = synchronized(lock) {
        if (coverageInvalid) return null
        counts[Owner(identity, key)]?.let { maxOf(0, it.total - 1 - it.baseline) }
    }

    /** Missing initial observation cannot be repaired by treating a later pass as initial. */
    fun invalidateCoverage() = synchronized(lock) {
        coverageInvalid = true
        counts.clear()
        composers.clear()
    }

    /** Preserves live scope history while opening a new measurement window. */
    fun resetCounts() = synchronized(lock) {
        counts.values.forEach { it.baseline = maxOf(0, it.total - 1) }
    }

    /** Closes the session before detaching observers, rejecting any late callbacks. */
    fun clear() {
        synchronized(lock) {
            active = false
            coverageInvalid = false
            generation++
            lifetimes.values.forEach { it.active = false }
            lifetimes.clear()
            composers.clear()
            counts.clear()
            ambiguousOwners.clear()
        }
        frames.get().clear()
    }

    override fun onBeginComposition(composition: ObservableComposition) {
        val currentGeneration = synchronized(lock) { if (!active) return else generation }
        frames.get().add(Frame(composition, currentGeneration))
    }

    override fun onEndComposition(composition: ObservableComposition) {
        // This callback also runs on aborted passes. It is only a stack boundary.
        val stack = frames.get()
        val index = stack.indexOfLast { it.composition === composition }
        if (index >= 0) while (stack.size > index) stack.removeAt(stack.lastIndex)
    }

    override fun onScopeDisposed(scope: RecomposeScope) {
        synchronized(lock) {
            val lifetime = lifetimes.remove(scope) ?: return
            lifetime.active = false
            counts.entries.removeAll { it.value.lifetime === lifetime }
        }
    }

    override fun onScopeEnter(scope: RecomposeScope) = Unit
    override fun onScopeExit(scope: RecomposeScope) = Unit
    override fun onReadInScope(scope: RecomposeScope, value: Any) = Unit
    override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) = Unit
}
