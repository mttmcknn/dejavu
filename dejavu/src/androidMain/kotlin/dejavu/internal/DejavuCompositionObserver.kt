package dejavu.internal

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.State
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Implements the Compose [CompositionObserver] API to track per-scope state
 * dependencies and invalidation causality — information the existing
 * [DejavuTracer] and snapshot observer cannot provide.
 *
 * **What this adds over existing tracking:**
 * - The snapshot apply observer knows "a MutableState<Int> changed" globally.
 *   This observer knows "CounterValue reads MutableState@0x1a2b and was
 *   invalidated specifically by that object."
 * - Per-composable state dependency sets — which state objects each scope reads.
 *
 * Runs alongside [DejavuTracer] on the composition thread. Scope-to-name
 * correlation is done lazily in [onReadInScope]: by the time a state read
 * occurs in the composable body, [traceEventStart] has already pushed the
 * name onto `composableStack`. ([onScopeEnter] fires too early — during
 * `startRestartGroup`, before [traceEventStart].)
 *
 * Purely internal — used only to enrich error messages in
 * [dejavu.SemanticNodeInteractionsKt.failRecompositionsExpectation].
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
internal object DejavuCompositionObserver :
    CompositionObserver,
    CompositionRegistrationObserver,
    ObserverDelegate {

    @Volatile
    override var isAvailable: Boolean = false

    // scope → qualified composable name
    private val scopeToComposable: MutableMap<RecomposeScope, String> =
        Collections.synchronizedMap(IdentityHashMap())

    // Scope from the most recent onScopeEnter, awaiting name from traceEventStart.
    // Thread-local because composition runs on a single thread.
    private val pendingScope = ThreadLocal<RecomposeScope?>()

    // Reentrancy guard: reading a DerivedState's .value triggers the derivation
    // computation, which reads other state objects, re-entering onReadInScope.
    // Placed in stateValue() so all callers (onReadInScope, onScopeInvalidated,
    // friendlyStateName) are protected.
    private val isCapturingValue = ThreadLocal<Boolean>()

    // scope → state objects read during last composition pass
    private val scopeReads: MutableMap<RecomposeScope, MutableSet<Any>> =
        Collections.synchronizedMap(IdentityHashMap())

    // qualifiedName → state objects this composable depends on (aggregated)
    private val composableDeps: MutableMap<String, MutableSet<Any>> =
        ConcurrentHashMap()

    // qualifiedName → invalidation records [(causeStateObject, timestampMs)]
    private val invalidations: MutableMap<String, MutableList<Invalidation>> =
        ConcurrentHashMap()

    internal data class Invalidation(
        val cause: Any?,
        val timestampMs: Long,
        val valueAtInvalidation: String?,
    )

    // state object → value string captured on first read (baseline before any invalidation)
    private val initialValues: MutableMap<Any, String> =
        Collections.synchronizedMap(IdentityHashMap())

    // ── CompositionObserver callbacks ────────────────────────────────

    override fun onBeginComposition(composition: ObservableComposition) {}
    override fun onEndComposition(composition: ObservableComposition) {
        // This hook precedes applyChanges (and can run on an abort). Only queue
        // work; inspecting the tree synchronously here would observe partial data.
        if (compositionHandles.containsKey(composition)) Runtime.requestFrameRefresh()
    }

    override fun onScopeEnter(scope: RecomposeScope) {
        // Name isn't available yet (startRestartGroup fires before traceEventStart).
        // Store as pending; bindPendingScope() will be called from traceEventStart.
        pendingScope.set(scope)
        scopeReads[scope]?.clear()
    }

    override fun onScopeExit(scope: RecomposeScope) {
        val name = scopeToComposable[scope] ?: return
        val reads = scopeReads[scope] ?: return
        if (reads.isNotEmpty()) {
            composableDeps.getOrPut(name) { ConcurrentHashMap.newKeySet() }.addAll(reads)
        }
    }

    override fun onReadInScope(scope: RecomposeScope, state: Any) {
        scopeReads.getOrPut(scope) {
            Collections.newSetFromMap(IdentityHashMap())
        }.add(state)
        // Record baseline value lazily — only capture when an assertion fails and
        // the error message is built (describeInvalidationCauses). This avoids
        // calling state.value + toString() on the per-read hot path.
        if (!initialValues.containsKey(state)) {
            initialValues[state] = PENDING_VALUE
        }
    }

    /**
     * Called from [DejavuTracer.traceEventStart] immediately after pushing the
     * composable name onto the stack. Binds the pending scope (from the most
     * recent [onScopeEnter]) to the given name.
     */
    override fun bindPendingScope(qualifiedName: String) {
        val scope = pendingScope.get() ?: return
        pendingScope.set(null)
        scopeToComposable[scope] = qualifiedName
    }

    override fun onScopeInvalidated(scope: RecomposeScope, cause: Any?) {
        val name = scopeToComposable[scope] ?: return
        val value = cause?.let { describeStateValue(it) }
        invalidations.getOrPut(name) { Collections.synchronizedList(ArrayList()) }
            .add(Invalidation(cause, System.currentTimeMillis(), value))
    }

    override fun onScopeDisposed(scope: RecomposeScope) {
        scopeToComposable.remove(scope)
        scopeReads.remove(scope)
    }

    // ── CompositionRegistrationObserver ──

    private val compositionHandles: MutableMap<ObservableComposition, CompositionObserverHandle> =
        Collections.synchronizedMap(IdentityHashMap())

    override fun onCompositionRegistered(composition: ObservableComposition) {
        if (compositionHandles.containsKey(composition)) return
        compositionHandles[composition] = composition.setObserver(this)
    }

    override fun onCompositionUnregistered(composition: ObservableComposition) {
        compositionHandles.remove(composition)?.dispose()
    }

    // ── ObserverDelegate ─────────────────────────────────────────────

    override fun tryRegister(recomposerInfo: Any): Any? {
        return try {
            val observeMethod = recomposerInfo.javaClass.methods.find {
                it.name == "observe" && it.parameterCount == 1
            } ?: return null
            val handle = observeMethod.invoke(recomposerInfo, this)
            if (handle != null) isAvailable = true
            handle
        } catch (_: Throwable) { null }
    }

    override fun disposeHandle(handle: Any) {
        (handle as? CompositionObserverHandle)?.dispose()
    }

    override fun resetRegistrar() {
        synchronized(compositionHandles) {
            compositionHandles.values.toList().also { compositionHandles.clear() }
        }.forEach { it.dispose() }
    }

    // ── Query (for error messages) ──────────────────────────────────

    /**
     * Formats a human-readable description of the state that directly
     * caused invalidation for this composable. Returns null if no
     * invalidation was recorded.
     */
    override fun describeInvalidationCauses(qualifiedName: String): String? {
        val records = invalidations[qualifiedName] ?: return null
        if (records.isEmpty()) return null

        // Group by actual state object identity (not identityHashCode, which can collide).
        // IdentityHashMap keys on reference equality, so distinct objects always get
        // separate groups even if they share an identityHashCode.
        val nullCauses = mutableListOf<Invalidation>()
        val grouped = IdentityHashMap<Any, MutableList<Invalidation>>()
        for (record in records) {
            val cause = record.cause
            if (cause == null) {
                nullCauses.add(record)
            } else {
                grouped.getOrPut(cause) { mutableListOf() }.add(record)
            }
        }

        return buildString {
            appendLine("  Invalidation causes:")
            if (nullCauses.isNotEmpty()) {
                appendLine("    (forced/unknown cause) × ${nullCauses.size}")
            }
            for ((cause, group) in grouped) {
                val typeName = friendlyStateName(cause)
                val times = group.size
                val timesStr = if (times == 1) "1 time" else "$times times"

                // Build value progression: initial → v1 → v2 → v3
                // Resolve pending initial value lazily (deferred from onReadInScope)
                val values = mutableListOf<String>()
                val initial = initialValues[cause]
                if (initial != null && initial != PENDING_VALUE) {
                    values.add(initial)
                } else if (initial == PENDING_VALUE) {
                    describeStateValue(cause)?.let { values.add(it) }
                }
                for (inv in group) {
                    val v = inv.valueAtInvalidation ?: continue
                    if (values.isEmpty() || values.last() != v) values.add(v)
                }

                append("    $typeName changed $timesStr")
                if (values.size >= 2) {
                    append(": ${values.joinToString(" → ")}")
                } else if (values.size == 1) {
                    append(" (current value: ${values.first()})")
                }
                appendLine()
            }
        }.trimEnd()
    }

    /**
     * Formats the set of state objects this composable reads.
     * Returns null if no reads were recorded.
     */
    override fun describeStateDependencies(qualifiedName: String): String? {
        val deps = composableDeps[qualifiedName] ?: return null
        if (deps.isEmpty()) return null

        val descriptions = deps.map { friendlyStateName(it) }.sorted()
        return "  State reads: ${descriptions.joinToString(", ")}"
    }

    override fun reset() {
        // Keep scopeToComposable — it's structural (scope → name) and must persist
        // so that onScopeInvalidated (which fires before recomposition) can resolve
        // scope names. Cleared only in fullReset() when the observer is disposed.
        pendingScope.remove()
        isCapturingValue.remove()
        scopeReads.clear()
        composableDeps.clear()
        invalidations.clear()
        initialValues.clear()
    }

    /** Full cleanup when the observer is being disposed. */
    override fun fullReset() {
        scopeToComposable.clear()
        reset()
    }

    // Sentinel value for initialValues entries that haven't been resolved yet.
    // Actual value is captured lazily when the error message is built.
    private const val PENDING_VALUE = "\u0000PENDING"

    // ── Internals ───────────────────────────────────────────────────

    private fun stateValue(state: Any): Any? {
        if (state !is State<*>) return null
        if (isCapturingValue.get() == true) return null
        isCapturingValue.set(true)
        return try {
            state.value
        } finally {
            isCapturingValue.remove()
        }
    }

    /**
     * Returns a human-friendly name like `MutableState<Int>` instead of
     * `SnapshotMutableStateImpl`.
     */
    private fun friendlyStateName(state: Any): String {
        val valueType = stateValue(state)?.let { it::class.simpleName }

        val rawName = state::class.simpleName ?: "State"
        return when {
            rawName.contains("Derived") -> "DerivedState<${valueType ?: "?"}>"
            state is MutableState<*> -> "MutableState<${valueType ?: "?"}>"
            rawName.contains("Mutable") || rawName.contains("Snapshot") ->
                "MutableState<${valueType ?: "?"}>"
            else -> if (valueType != null) "$rawName<$valueType>" else rawName
        }
    }

    private fun describeStateValue(state: Any): String? {
        return stateValue(state)?.let { v ->
            val s = v.toString()
            if (s.length > 60) s.take(57) + "..." else s
        }
    }
}
