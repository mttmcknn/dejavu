package dejavu.internal

import androidx.compose.runtime.tooling.CompositionData
import dejavu.Dejavu
import kotlinx.atomicfu.locks.synchronized

internal actual fun currentTimeMillis(): Long =
    dateNow().toLong()

private fun dateNow(): Double = js("Date.now()")

internal actual fun platformLog(tag: String, message: String) {
    console.log("$tag: $message".toJsString())
}

internal actual fun platformWarnLog(tag: String, message: String) {
    console.warn("WARN $tag: $message".toJsString())
}

internal actual fun getPendingCause(): RecomposeCause? = null

internal actual fun isLoggingEnabled(): Boolean = Dejavu.loggingEnabled

internal actual fun currentCompositionsSnapshot(): Set<CompositionData> =
    synchronized(DejavuTracer.inspectionTablesLock) { DejavuTracer.inspectionTables.toSet() }

internal actual fun createInspectionTables(): MutableSet<CompositionData> = mutableSetOf()

internal actual fun platformBuildTagMapping(compositionData: Set<CompositionData>) {
    CommonTagMapping.buildTagMapping(compositionData)
}

// TODO: Not actually thread-local. Safe while Compose on WasmJs is single-threaded.
//  If multi-threaded composition is added (e.g., via Web Workers), replace with
//  a Map keyed by worker ID or equivalent TLS mechanism.
internal actual class PlatformThreadLocal<T> actual constructor(private val initial: () -> T) {
    private var value: T = initial()

    actual fun get(): T = value
}

internal actual fun onComposableTraced(qualifiedName: String) {}
internal actual fun describeInvalidationCauses(qualifiedName: String): String? = null
internal actual fun describeStateDependencies(qualifiedName: String): String? = null
internal actual fun isObserverAvailable(): Boolean = false
internal actual fun resetObserver() {}

private external object console {
    fun log(message: JsString)
    fun warn(message: JsString)
}
