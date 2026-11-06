package dejavu.internal

import android.os.Handler
import android.os.Looper
import android.view.Choreographer

/**
 * A runtime session's coalesced, one-shot inspection refresh.
 *
 * Requests may come from snapshot/composition observers on other threads. Always
 * enqueue them: composition observers can run before changes have been applied.
 * A callback never reposts itself, including while the test clock has pending work.
 * That lets Robolectric finish draining its main queue and advance the Compose clock.
 */
internal class FrameRefreshScheduler(private val refresh: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var choreographer: Choreographer? = null
    private val lock = Any()
    private var disposed = false
    private var scheduled = false

    private val callback = Choreographer.FrameCallback {
        val runRefresh = synchronized(lock) {
            if (disposed) false else {
                scheduled = false
                true
            }
        }
        if (runRefresh) refresh()
    }

    private val schedule = Runnable {
        synchronized(lock) {
            if (!disposed) {
                // Bind on the main queue, even when enable/request came from a
                // snapshot thread without a Looper (or with a different one).
                val owner = choreographer ?: Choreographer.getInstance().also { choreographer = it }
                owner.postFrameCallback(callback)
            }
        }
    }

    fun request() {
        synchronized(lock) {
            if (disposed || scheduled) return
            scheduled = true
            handler.post(schedule)
        }
    }

    /** Called on the main thread; stale requests retain this disposed session. */
    fun dispose() {
        synchronized(lock) {
            disposed = true
            scheduled = false
            handler.removeCallbacks(schedule)
            choreographer?.removeFrameCallback(callback)
        }
    }
}
