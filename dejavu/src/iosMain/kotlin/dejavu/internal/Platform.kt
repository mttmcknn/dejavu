package dejavu.internal

import androidx.compose.runtime.tooling.CompositionData
import dejavu.Dejavu
import kotlinx.atomicfu.locks.synchronized
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.timeIntervalSince1970

internal actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

internal actual fun platformLog(tag: String, message: String) {
    NSLog("$tag: $message")
}

internal actual fun platformWarnLog(tag: String, message: String) {
    NSLog("WARN $tag: $message")
}

internal actual fun getPendingCause(): RecomposeCause? = null

internal actual fun isLoggingEnabled(): Boolean = Dejavu.loggingEnabled

internal actual fun currentCompositionsSnapshot(): Set<CompositionData> =
    synchronized(DejavuTracer.inspectionTablesLock) { DejavuTracer.inspectionTables.toSet() }

internal actual fun createInspectionTables(): MutableSet<CompositionData> = mutableSetOf()

internal actual fun platformBuildTagMapping(compositionData: Set<CompositionData>) {
    CommonTagMapping.buildTagMapping(compositionData)
}

// TODO: Not actually thread-local. Safe while Compose on iOS is single-threaded.
//  If multi-threaded composition is added, replace with platform-specific TLS
//  (e.g., pthread_key_create or dispatch_get_specific).
internal actual class PlatformThreadLocal<T> actual constructor(private val initial: () -> T) {
    private var value: T = initial()

    actual fun get(): T = value
}

internal actual fun onComposableTraced(qualifiedName: String) {}
internal actual fun describeInvalidationCauses(qualifiedName: String): String? = null
internal actual fun describeStateDependencies(qualifiedName: String): String? = null
internal actual fun isObserverAvailable(): Boolean = false
internal actual fun resetObserver() {}
