package dejavu.internal

import android.util.Log
import androidx.compose.runtime.tooling.CompositionData
import kotlinx.atomicfu.locks.synchronized
import java.util.concurrent.CopyOnWriteArraySet

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual fun platformLog(tag: String, message: String) {
    Log.d(tag, message)
}

internal actual fun platformWarnLog(tag: String, message: String) {
    Log.w(tag, message)
}

internal actual fun getPendingCause(): RecomposeCause? = Runtime.getPendingCause()

internal actual fun isLoggingEnabled(): Boolean = Runtime.isLoggingEnabled

internal actual fun currentCompositionsSnapshot(): Set<CompositionData> {
    val runtimeSnapshots = Runtime.currentCompositionsSnapshot()
    val explicitSnapshots = synchronized(DejavuTracer.inspectionTablesLock) {
        DejavuTracer.inspectionTables.toSet()
    }
    // The Android lifecycle tracker can be enabled by an earlier rule in the same process.
    // setTrackedContent supplies a separate inspection collection for its subcomposition;
    // activity tables alone do not include that collection. Keep both views of the tree.
    return runtimeSnapshots + explicitSnapshots
}

internal actual fun createInspectionTables(): MutableSet<CompositionData> =
    CopyOnWriteArraySet()

internal actual fun platformBuildTagMapping(compositionData: Set<CompositionData>) {
    TagMapping.buildTagMapping(compositionData)
    val mappedByAndroidTooling = synchronized(DejavuTracer.lastSeenTagsLock) {
        DejavuTracer.lastSeenTags.toSet()
    }
    CommonTagMapping.buildTagMapping(compositionData, excludedTags = mappedByAndroidTooling)
}

internal actual class PlatformThreadLocal<T> actual constructor(private val initial: () -> T) {
    private val tl = object : ThreadLocal<T>() {
        override fun initialValue(): T = initial()
    }

    actual fun get(): T = tl.get()!!
}

internal actual fun onComposableTraced(qualifiedName: String) {
    Runtime.observerDelegate.bindPendingScope(qualifiedName)
}

internal actual fun describeInvalidationCauses(qualifiedName: String): String? =
    Runtime.observerDelegate.describeInvalidationCauses(qualifiedName)

internal actual fun describeStateDependencies(qualifiedName: String): String? =
    Runtime.observerDelegate.describeStateDependencies(qualifiedName)

internal actual fun isObserverAvailable(): Boolean =
    Runtime.observerDelegate.isAvailable

internal actual fun resetObserver() {
    Runtime.observerDelegate.reset()
}
