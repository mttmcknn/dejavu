package dejavu.internal

import androidx.compose.runtime.Composer
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.setObserver
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Compose has one observer slot per composition. Runtime diagnostics and a tracked test therefore
 * share one installation, retaining independent leases so either owner can stop without removing
 * the other's callbacks. Explicit subcomposition installs use the same dispatcher as their parent.
 */
@OptIn(ExperimentalComposeRuntimeApi::class, InternalComposeApi::class)
internal object TrackingCompositionObservers : CompositionObserver {
    private class Entry(
        val composition: ObservableComposition,
        val observer: CompositionObserver,
        var handle: CompositionObserverHandle? = null,
        var leases: Int = 0,
    )

    private class HelperCapture(val composition: ObservableComposition) {
        var handle: CompositionObserverHandle? = null
    }

    private class Acquisition(val handle: CompositionObserverHandle, val ready: Boolean)

    private val lock = SynchronizedObject()
    private val entries = mutableListOf<Entry>()
    private val helperCaptures = mutableListOf<HelperCapture>()
    private var helperActive = false
    private var helperGeneration = 0

    fun startHelperSession() {
        clearHelperSession()
        ExactScopeCounts.start()
        synchronized(lock) { helperActive = true }
    }

    /** Acquires the observer for an independently owned runtime registration. */
    fun acquire(composition: ObservableComposition): CompositionObserverHandle =
        acquireObserver(composition).handle

    private fun acquireObserver(composition: ObservableComposition): Acquisition {
        val (entry, install, ready) = synchronized(lock) {
            val existing = entries.firstOrNull { it.composition === composition }
            val current = existing ?: Entry(
                composition,
                object : CompositionObserver by TrackingCompositionObservers {},
            ).also { entries += it }
            current.leases++
            Triple(current, existing == null, existing == null || existing.handle != null)
        }
        val lease = object : CompositionObserverHandle {
            private var released = false

            override fun dispose() {
                val handle = synchronized(lock) {
                    if (released) return
                    released = true
                    entry.leases--
                    if (entry.leases == 0) {
                        entries.remove(entry)
                        entry.handle
                    } else null
                }
                // Compose takes its composition lock here. Never hold our registry lock while
                // calling it: attachComposer runs with the composition lock already held.
                handle?.dispose()
            }
        }
        if (install) {
            try {
                // Only the entry's creator installs. A concurrent lease must not wait here:
                // its caller may hold the composition lock needed by the installer.
                val handle = composition.setObserver(entry.observer)
                val retained = synchronized(lock) {
                    if (entry.leases > 0) {
                        entry.handle = handle
                        true
                    } else false
                }
                // Each entry has a unique observer; late cleanup cannot clear a new entry.
                if (!retained) handle.dispose()
            } catch (failure: Throwable) {
                lease.dispose()
                throw failure
            }
        }
        // The creator owns an unreturned lease throughout installation: the entry cannot be
        // retired before its handle is stored. Other callers never dispose that creator lease.
        return Acquisition(lease, ready)
    }

    /** Called in the helper's parent and child composition before invoking user content. */
    fun attachComposer(composer: Composer) {
        val composition = composer.composition as? ObservableComposition ?: return
        val (capture, generation) = synchronized(lock) {
            // Old content can still be disposed or recomposed as runComposeUiTest tears down.
            if (!helperActive) return
            if (helperCaptures.any { it.composition === composition }) return
            val capture = HelperCapture(composition).also { helperCaptures += it }
            capture to helperGeneration
        }
        val acquisition = acquireObserver(composition)
        val retained = synchronized(lock) {
            if (helperActive && helperGeneration == generation && helperCaptures.any { it === capture }) {
                capture.handle = acquisition.handle
                // If another installer was pending, initial coverage is unknown. Keep the
                // capture token for this session to prevent a later pass establishing a false
                // initial baseline. Exact tag queries then remain explicitly unsupported.
                if (acquisition.ready) {
                    ExactScopeCounts.attachComposer(composer)
                } else {
                    // A child created while its parent's installer is pending can also miss
                    // its initial observer callbacks. Fail closed for this exact-count session
                    // rather than later treating an observed recomposition as initial work.
                    ExactScopeCounts.invalidateCoverage()
                }
                true
            } else false
        }
        if (!retained) acquisition.handle.dispose()
    }

    /** Also releases captures from a composition that threw before applying any effects. */
    fun clearHelperSession() {
        val handles = synchronized(lock) {
            helperActive = false
            helperGeneration++
            helperCaptures.mapNotNull { it.handle }.also { helperCaptures.clear() }
        }
        ExactScopeCounts.clear()
        ExactTagMapping.clear()
        handles.forEach { it.dispose() }
    }

    override fun onBeginComposition(composition: ObservableComposition) {
        ExactScopeCounts.onBeginComposition(composition)
        platformDiagnosticCompositionObserver()?.onBeginComposition(composition)
    }

    override fun onEndComposition(composition: ObservableComposition) {
        ExactScopeCounts.onEndComposition(composition)
        platformDiagnosticCompositionObserver()?.onEndComposition(composition)
    }

    override fun onScopeEnter(scope: RecomposeScope) {
        ExactScopeCounts.onScopeEnter(scope)
        platformDiagnosticCompositionObserver()?.onScopeEnter(scope)
    }

    override fun onScopeExit(scope: RecomposeScope) {
        ExactScopeCounts.onScopeExit(scope)
        platformDiagnosticCompositionObserver()?.onScopeExit(scope)
    }

    override fun onReadInScope(scope: RecomposeScope, value: Any) {
        ExactScopeCounts.onReadInScope(scope, value)
        platformDiagnosticCompositionObserver()?.onReadInScope(scope, value)
    }

    override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) {
        ExactScopeCounts.onScopeInvalidated(scope, value)
        platformDiagnosticCompositionObserver()?.onScopeInvalidated(scope, value)
    }

    override fun onScopeDisposed(scope: RecomposeScope) {
        ExactScopeCounts.onScopeDisposed(scope)
        platformDiagnosticCompositionObserver()?.onScopeDisposed(scope)
    }
}
