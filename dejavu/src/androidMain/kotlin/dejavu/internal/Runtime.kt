package dejavu.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.ui.tooling.data.Group
import androidx.compose.ui.tooling.data.UiToolingDataApi
import androidx.compose.ui.tooling.data.asTree
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.IdentityHashMap
import java.lang.ref.WeakReference
import java.lang.reflect.Method

internal object Runtime {
  private const val TAG = "Dejavu"
  @Volatile
  private var enabled: Boolean = false
  private var appRef: Application? = null
  private var mainScope: CoroutineScope? = null
  private val observedRecomposers = mutableSetOf<Any>()
  private val observerHandles = IdentityHashMap<Any, Any>()
  private val recomposerStateJobs = IdentityHashMap<Any, Job>()

  private var observer: ObserverDelegate = NoOpObserver

  private var lifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null
  @Volatile
  private var lastResumedRef: WeakReference<Activity>? = null
  // Retain only a weak hint across disable/enable: registering lifecycle callbacks
  // does not replay onResume for an activity that is already on screen.
  private var lastKnownActivityRef: WeakReference<Activity>? = null

  // Per-recomposer change count tracking
  private val recomposerChangeCount = IdentityHashMap<Any, Long>()
  @Volatile
  private var frameRefresh: FrameRefreshScheduler? = null

  // Latest composition snapshot cache (identity-based)
  private val latestSnapshots: MutableSet<CompositionData> =
    Collections.newSetFromMap(IdentityHashMap())

  private var logToLogcat: Boolean = false
  internal val isLoggingEnabled get() = logToLogcat

  internal val observerDelegate: ObserverDelegate get() = observer

  // Snapshot observer handle for cleanup
  private var snapshotObserverHandle: Any? = null

  // Previous value of isDebugInspectorInfoEnabled, restored in disable()
  private var previousInspectorInfoEnabled: Boolean = false

  @Volatile
  private var pendingCause: RecomposeCause? = null
  private val stateValueCache = Collections.synchronizedMap(IdentityHashMap<Any, Any?>())

  @OptIn(InternalComposeTracingApi::class)
  fun enable(
    app: Application,
    logToLogcat: Boolean = false,
  ) {
    if (enabled) return
    try {
      startSession(app, logToLogcat)
    } catch (failure: Throwable) {
      disable()
      throw failure
    }
  }

  @OptIn(InternalComposeTracingApi::class)
  private fun startSession(app: Application, logToLogcat: Boolean) {
    val refresh = FrameRefreshScheduler(::refreshCompositionData)
    frameRefresh = refresh
    enabled = true
    this.logToLogcat = logToLogcat
    appRef = app

    lastResumedRef = lastKnownActivityRef?.get()
      ?.takeIf { it.application === app && !it.isDestroyed && !it.isFinishing }
      ?.let { WeakReference(it) }
    refresh.request()

    observer = DejavuCompositionObserver
    if (logToLogcat) Log.d(TAG, "Dejavu enabled — streaming recomposition events (filter: \"Dejavu\")")

    // Enable debug inspector info so InspectableValue.inspectableElements is populated
    previousInspectorInfoEnabled = isDebugInspectorInfoEnabled
    isDebugInspectorInfoEnabled = true

    // Wire up composition tracer for traceEventStart/End callbacks
    Composer.setTracer(DejavuTracer)
    DejavuTracer.enabled = true

    // Register snapshot apply observer to detect state changes per frame
    snapshotObserverHandle = Snapshot.registerApplyObserver { changedObjects, _ ->
      val types = mutableListOf<String>()
      var sameValueWrites = 0
      for (obj in changedObjects) {
        val stateValue = try { (obj as? MutableState<*>)?.value } catch (_: Exception) { null }
        val typeName = if (stateValue != null) {
          stateValue::class.simpleName ?: "Unknown"
        } else {
          obj::class.simpleName ?: "StateObject"
        }
        types.add(typeName)
        val cachedValue = stateValueCache[obj]
        if (cachedValue != null && cachedValue == stateValue) {
          sameValueWrites++
        }
        if (stateValue != null) {
          stateValueCache[obj] = stateValue
        }
      }
      pendingCause = RecomposeCause(
        stateChanges = changedObjects.size,
        types = types,
        sameValueWrites = sameValueWrites,
        isParameterDriven = false
      )
      refresh.request()
    }

    // Observe work rather than polling forever. An Idle transition supplies a
    // final refresh after applyChanges; pending work alone never rearms a frame.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    mainScope = scope
    scope.launch {
      Recomposer.runningRecomposers.collectLatest { currentSet ->
        val removed = observedRecomposers.filter { it !in currentSet }
        removed.forEach { info ->
          observedRecomposers.remove(info)
          recomposerStateJobs.remove(info)?.cancel()
          observerHandles.remove(info)?.let { observer.disposeHandle(it) }
          recomposerChangeCount.remove(info)
        }
        currentSet.forEach { info ->
          if (observedRecomposers.add(info)) {
            // Initialize change count baseline
            getChangeCount(info)?.let { recomposerChangeCount[info] = it }
            tryRegisterCompositionObserver(info)
            // Seed immediately and once asynchronously when a recomposer appears.
            lastResumedRef?.get()?.let { activity ->
              ensureInspectionTag(activity)
            }
            recomposerStateJobs[info] = scope.launch {
              info.state.collect { refresh.request() }
            }
            refresh.request()
          }
        }
      }
    }

    // Observe activity lifecycle to discover CompositionData
    val callbacks = object : Application.ActivityLifecycleCallbacks {
      override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?
      ) {
        ensureInspectionTag(activity)
      }

      override fun onActivityStarted(activity: Activity) {
        ensureInspectionTag(activity)
      }

      override fun onActivityResumed(activity: Activity) {
        seedActiveActivity(activity)
      }

      override fun onActivityPaused(activity: Activity) { /* no-op */
      }

      override fun onActivityStopped(activity: Activity) { /* no-op */
      }

      override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle
      ) { /* no-op */
      }

      override fun onActivityDestroyed(activity: Activity) {
        if (lastResumedRef?.get() === activity) {
          lastResumedRef = null
        }
        if (lastKnownActivityRef?.get() === activity) lastKnownActivityRef = null
      }
    }
    app.registerActivityLifecycleCallbacks(callbacks)
    lifecycleCallbacks = callbacks
  }

  @OptIn(InternalComposeTracingApi::class)
  fun disable() {
    if (!enabled) return
    enabled = false
    frameRefresh?.dispose()
    frameRefresh = null
    if (logToLogcat) Log.d(TAG, "Dejavu disabled")

    DejavuTracer.enabled = false
    Composer.setTracer(null)

    // Restore previous inspector info state
    isDebugInspectorInfoEnabled = previousInspectorInfoEnabled

    // Dispose snapshot observer
    (snapshotObserverHandle as? androidx.compose.runtime.snapshots.ObserverHandle)?.dispose()
    snapshotObserverHandle = null
    stateValueCache.clear()
    pendingCause = null

    observerHandles.values.forEach { observer.disposeHandle(it) }
    observerHandles.clear()
    observer.fullReset()
    observer.resetRegistrar()
    observer = NoOpObserver

    mainScope?.cancel()
    mainScope = null
    recomposerStateJobs.clear()
    appRef?.let { app ->
      lifecycleCallbacks?.let { app.unregisterActivityLifecycleCallbacks(it) }
    }
    lifecycleCallbacks = null
    observedRecomposers.clear()
    recomposerChangeCount.clear()
    latestSnapshots.clear()
    lastResumedRef = null
    appRef = null
  }

  /** The scheduler identity is also the ownership token for a rule's session. */
  internal fun beginTestSession(app: Application): FrameRefreshScheduler? {
    if (enabled) return null
    enable(app)
    return frameRefresh
  }

  internal fun endTestSession(session: FrameRefreshScheduler?) {
    if (session != null && frameRefresh === session) disable()
  }

  internal fun requestFrameRefresh() {
    frameRefresh?.request()
  }

  fun getPendingCause(): RecomposeCause? = pendingCause

  private fun ensureInspectionTag(activity: Activity) {
    val root = activity.window?.decorView ?: return
    val tagId = androidx.compose.ui.R.id.inspection_slot_table_set
    seedInspectionTagRecursively(root, tagId)
  }

  internal fun seedActiveActivity(activity: Activity) {
    if (!enabled) return
    if (lastResumedRef?.get() === activity) return
    lastResumedRef = WeakReference(activity)
    lastKnownActivityRef = lastResumedRef
    ensureInspectionTag(activity)
    requestFrameRefresh()
  }

  private fun seedInspectionTagRecursively(
    view: View,
    tagId: Int
  ) {
    // Set a mutable set tag if missing; safe on any View
    if (view.getTag(tagId) !is MutableSet<*>) {
      view.setTag(tagId, LinkedHashSet<CompositionData>())
    }
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        seedInspectionTagRecursively(view.getChildAt(i), tagId)
      }
    }
  }

  private fun refreshCompositionData() {
    if (!enabled) return
    lastResumedRef?.get()?.let { ensureInspectionTag(it) }

    // Check for composition changes for diagnostic logging only.
    var anyChanged = false
    val iterator = recomposerChangeCount.entries.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      val prev = entry.value
      val curr = getChangeCount(entry.key) ?: prev
      if (curr > prev) {
        anyChanged = true
        recomposerChangeCount[entry.key] = curr
      }
    }
    // A composition may execute without increasing Recomposer.changeCount.
    // Refresh on the notification itself, not just a change-count difference.
    latestSnapshots.clear()
    latestSnapshots.addAll(currentCompositionsSnapshot())
    DejavuTracer.buildTagMappingFromFrameLoop(latestSnapshots)
    if (logToLogcat) logFrameDetails(anyChanged)
  }

  private fun logFrameDetails(anyChanged: Boolean) {
    if (!anyChanged) return
    val snapshotSummary = latestSnapshots.mapNotNull { getRootGroupCount(it) }
    Log.d(TAG, "Composition changed (${snapshotSummary.size} snapshot(s), roots=${snapshotSummary.joinToString(",")})")
  }

  @Volatile
  private var changeCountMethod: Method? = null

  private fun getChangeCount(recomposerInfo: Any): Long? {
    return try {
      val method = changeCountMethod ?: recomposerInfo.javaClass.getDeclaredMethod("getChangeCount").also {
        it.isAccessible = true
        changeCountMethod = it
      }
      val v = method.invoke(recomposerInfo)
      when (v) {
        is Long -> v
        is Int -> v.toLong()
        else -> null
      }
    } catch (_: Throwable) {
      null
    }
  }

  internal fun currentCompositionsSnapshot(): Set<CompositionData> {
    if (!enabled) return emptySet()
    val activity = lastResumedRef?.get() ?: return emptySet()
    val root = activity.window?.decorView ?: return emptySet()
    val out: MutableSet<CompositionData> = Collections.newSetFromMap(IdentityHashMap())
    collectCompositionDataFromView(root).forEach { any ->
      if (any is CompositionData) out.add(any)
    }
    return out
  }

  private fun collectCompositionDataFromView(view: View): Set<Any> {
    val out: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())
    // Read the inspection slot table set tag populated by Compose (on this view)
    val tag = view.getTag(androidx.compose.ui.R.id.inspection_slot_table_set)
    if (tag is Collection<*>) {
      tag.forEach { item -> if (item is CompositionData) out.add(item) }
    }
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        out.addAll(collectCompositionDataFromView(view.getChildAt(i)))
      }
    }
    return out
  }

  @OptIn(UiToolingDataApi::class)
  private fun getRootGroupCount(data: CompositionData): Int? {
    return try {
      val root: Group = data.asTree()
      root.children.size
    } catch (_: Throwable) {
      null
    }
  }

  private fun tryRegisterCompositionObserver(recomposerInfo: Any) {
    val handle = observer.tryRegister(recomposerInfo) ?: return
    observerHandles[recomposerInfo] = handle
  }
}
