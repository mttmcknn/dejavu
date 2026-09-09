package dejavu.internal

import androidx.compose.runtime.tooling.CompositionData
import dejavu.Dejavu
import kotlinx.atomicfu.locks.synchronized
import java.util.concurrent.CopyOnWriteArraySet

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual fun platformLog(tag: String, message: String) {
    println("$tag: $message")
}

internal actual fun platformWarnLog(tag: String, message: String) {
    System.err.println("WARN $tag: $message")
}

internal actual fun getPendingCause(): RecomposeCause? = null

internal actual fun isLoggingEnabled(): Boolean = Dejavu.logToStdout

internal actual fun currentCompositionsSnapshot(): Set<CompositionData> =
    synchronized(DejavuTracer.inspectionTablesLock) { DejavuTracer.inspectionTables.toSet() }

internal actual fun createInspectionTables(): MutableSet<CompositionData> =
    CopyOnWriteArraySet()

// JVM Desktop uses CommonTagMapping which does a single-pass Group tree walk.
// Android uses its own TagMapping with a two-pass algorithm that handles
// sub-compositions (e.g., LazyColumn items) via asTree() + Group identity.
// CommonTagMapping is sufficient for JVM because Compose Multiplatform's
// CompositionGroup provides adequate sub-composition data without the
// Android-specific tooling APIs.
internal actual fun platformBuildTagMapping(compositionData: Set<CompositionData>) {
    CommonTagMapping.buildTagMapping(compositionData)
}

internal actual class PlatformThreadLocal<T> actual constructor(private val initial: () -> T) {
    private val tl = object : ThreadLocal<T>() {
        override fun initialValue(): T = initial()
    }

    actual fun get(): T = tl.get()!!
}

internal actual fun onComposableTraced(qualifiedName: String) {}
internal actual fun describeInvalidationCauses(qualifiedName: String): String? = null
internal actual fun describeStateDependencies(qualifiedName: String): String? = null
internal actual fun isObserverAvailable(): Boolean = false
internal actual fun resetObserver() {}
