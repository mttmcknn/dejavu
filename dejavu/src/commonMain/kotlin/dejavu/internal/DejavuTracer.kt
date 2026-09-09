package dejavu.internal

import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.tooling.CompositionData
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

@OptIn(InternalComposeTracingApi::class)
internal object DejavuTracer : CompositionTracer {
    private const val TAG = "Dejavu"

    @kotlin.concurrent.Volatile
    var enabled = false

    // ── Shared state (internal so platform tag-mapping code can access) ──

    /** Count traceEventStart calls per composable key (int).
     *  First call is initial composition, subsequent are recompositions. */
    internal val compositionCounts = mutableMapOf<Int, Int>()
    /** Baseline for key-based per-tag counting, set at resetCounts() time.
     *  Stores maxOf(0, compositionCounts[key] - 1) at reset so post-reset
     *  queries subtract it to get only post-reset recompositions. */
    internal val compositionCountBaseline = mutableMapOf<Int, Int>()
    internal val compositionCountsLock = SynchronizedObject()

    /** Map from key (int) to parsed info: functionName, fullInfo, sourceLocation. */
    internal val keyToInfo = mutableMapOf<Int, TracedComposable>()
    internal val keyToInfoLock = SynchronizedObject()

    /** Reverse index: simple name -> traced composables for O(1) qualified-name lookup. */
    internal val simpleNameIndex = mutableMapOf<String, MutableList<TracedComposable>>()
    internal val simpleNameIndexLock = SynchronizedObject()

    /** Map from qualified function name to recomposition count (excludes initial composition).
     *  This is the primary query API; query methods support simple name fallback. */
    internal val recompositionCounts = mutableMapOf<String, Int>()
    internal val recompositionCountsLock = SynchronizedObject()

    /** Timestamped recomposition events per qualified function name. */
    internal val recompositionEvents = mutableMapOf<String, MutableList<RecompositionEvent>>()
    internal val recompositionEventsLock = SynchronizedObject()

    /** Per-tag recomposition counting via Group tree parameter change detection. */
    internal val perTagRecompCounts = mutableMapOf<String, Int>()
    internal val tagParamFingerprints = mutableMapOf<String, Int>()
    internal val perTagRecompEvents = mutableMapOf<String, MutableList<RecompositionEvent>>()
    internal val perTagLock = SynchronizedObject()

    /** Parameter snapshots for change tracking (replaces raw fingerprint comparison). */
    internal val tagParamSnapshots = mutableMapOf<String, List<ParamSnapshot>>()
    internal val tagParameterChanges = mutableMapOf<String, MutableList<List<ParameterChange>>>()
    internal val tagParamLock = SynchronizedObject()

    /** Tags seen during the most recent buildTagMapping pass; used to detect stale entries. */
    internal val lastSeenTags = mutableSetOf<String>()
    internal val lastSeenTagsLock = SynchronizedObject()

    /** Stable per-instance identifier (Group.identity / Anchor) for each tag. */
    internal val tagToIdentity = mutableMapOf<String, Any>()
    internal val tagToIdentityLock = SynchronizedObject()

    /** Tags that have had at least one fingerprint comparison during a frame-loop
     *  buildTagMapping pass. When a tag is in this set, per-tag tracking is reliable. */
    internal val tagsWithFingerprint = mutableSetOf<String>()
    internal val tagsWithFingerprintLock = SynchronizedObject()

    /** True while buildTagMapping is running from the frame callback.
     *  Gates tagsWithFingerprint population. */
    @kotlin.concurrent.Volatile
    var isFrameLoopPass = false

    /** Cache of testTag -> composable function name. */
    internal val testTagToFunction = mutableMapOf<String, String>()
    internal val testTagToFunctionLock = SynchronizedObject()

    /** Cache of testTag -> composable key (int) for per-instance recomposition counting. */
    internal val testTagToKey = mutableMapOf<String, Int>()
    internal val testTagToKeyLock = SynchronizedObject()

    /**
     * Shared inspection tables populated directly by Compose when [LocalInspectionTables] is provided.
     * JVM platforms use a copy-on-write set because Compose can mutate it concurrently with assertions.
     */
    internal val inspectionTables = InspectionTables(createInspectionTables())
    internal val inspectionTablesLock = SynchronizedObject()

    /** Track parent-child causality: stack of qualified names for the current composition. */
    private val composableStack = PlatformThreadLocal { ArrayDeque<String>() }

    internal data class TracedComposable(
        val key: Int,
        val simpleName: String,      // e.g., "CounterValue"
        val qualifiedName: String,   // e.g., "demo.app.ui.CounterValue"
        val sourceLocation: String,  // e.g., "Counter.kt:29"
        val fullInfo: String         // raw info string
    )

    internal data class RecompositionEvent(
        val timestampMs: Long,
        val dirty1: Int,
        val qualifiedName: String,
        val parentName: String? = null
    )

    // ── Framework filtering ─────────────────────────────────────────

    /** Framework packages/files to skip when walking the Group tree. */
    internal val frameworkPrefixes = setOf("androidx.", "kotlin.", "android.")


    // ── CompositionTracer implementation ─────────────────────────────

    override fun isTraceInProgress(): Boolean = enabled

    override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
        // Parse and cache info
        val traced = synchronized(keyToInfoLock) {
            keyToInfo.getOrPut(key) { parseInfo(key, info) }
        }

        // Push onto the composable stack BEFORE the framework check so that
        // traceEventEnd (which fires for every composable) can always pop.
        val stack = composableStack.get()
        val parentName = stack.lastOrNull()
        stack.add(traced.qualifiedName)

        // Bind the pending observer scope to this composable name
        onComposableTraced(traced.qualifiedName)

        // Skip framework composables for counting
        if (isFrameworkComposable(info)) return

        // Count total compositions for this key
        val totalCount = synchronized(compositionCountsLock) {
            val prev = compositionCounts.getOrPut(key) { 0 }
            val next = prev + 1
            compositionCounts[key] = next
            next
        }

        // If this is NOT the first composition (totalCount > 1), it's a recomposition
        if (totalCount > 1) {
            val recompCount = synchronized(recompositionCountsLock) {
                val prev = recompositionCounts.getOrPut(traced.qualifiedName) { 0 }
                val next = prev + 1
                recompositionCounts[traced.qualifiedName] = next
                next
            }

            // Build cause from pending state changes and dirty bits
            val stateCause = getPendingCause()
            val cause = if (stateCause != null) {
                stateCause.copy(isParameterDriven = dirty1 != 0)
            } else if (dirty1 != 0) {
                RecomposeCause(isParameterDriven = true)
            } else {
                null
            }

            // Also feed into the existing RecomposeTracker so tests work
            RecomposeTracker.recordCause(traced.qualifiedName, cause)

            // Record timestamped event with dirty bit info and parent causality
            val event = RecompositionEvent(
                timestampMs = currentTimeMillis(),
                dirty1 = dirty1,
                qualifiedName = traced.qualifiedName,
                parentName = parentName
            )
            synchronized(recompositionEventsLock) {
                recompositionEvents.getOrPut(traced.qualifiedName) { mutableListOf() }
                    .add(event)
            }

            if (isLoggingEnabled()) {
                val hasTags = synchronized(testTagToFunctionLock) {
                    testTagToFunction.values.any { it == traced.qualifiedName }
                }
                if (!hasTags) {
                    val parentSuffix = if (parentName != null) ", parent=${parentName}" else ""
                    platformLog(TAG, "Recomposition #$recompCount: ${traced.qualifiedName} (${traced.sourceLocation})$parentSuffix")
                }
            }
        }
    }

    override fun traceEventEnd() {
        val stack = composableStack.get()
        if (stack.isNotEmpty()) {
            stack.removeAt(stack.size - 1)
        }
    }

    // ── Parsing ──────────────────────────────────────────────────────

    /** Parse info string like "demo.app.ui.CounterValue (Counter.kt:29)". */
    internal fun parseInfo(key: Int, info: String): TracedComposable {
        val parenIdx = info.indexOf(" (")
        val qualifiedName = if (parenIdx > 0) info.substring(0, parenIdx) else info
        val sourceLocation = if (parenIdx > 0) info.substring(parenIdx + 2).trimEnd(')') else ""
        val simpleName = qualifiedName.substringAfterLast('.')

        val traced = TracedComposable(
            key = key,
            simpleName = simpleName,
            qualifiedName = qualifiedName,
            sourceLocation = sourceLocation,
            fullInfo = info
        )
        synchronized(simpleNameIndexLock) {
            simpleNameIndex.getOrPut(simpleName) { mutableListOf() }.add(traced)
        }
        return traced
    }

    /** Skip framework composables (androidx.*) for recomposition counting. */
    internal fun isFrameworkComposable(info: String): Boolean {
        return info.startsWith("androidx.") ||
               info.startsWith("kotlin.") ||
               info.startsWith("<get-") ||       // property accessors
               info.startsWith("remember(") || info == "remember" // remember calls (not user composables like rememberMyState)
    }

    // ── Name resolution (used by tag mapping) ────────────────────────

    /**
     * Given a Group name, determine if it represents a user composable and
     * return its resolved qualified name. Returns [fallback] if the name is
     * null, a framework composable, or otherwise not a user composable.
     *
     * Uses dynamic resolution via [simpleNameIndex] instead of a hardcoded
     * list of framework composable names. Since [parseInfo] runs for every
     * composable in [traceEventStart] (before the framework filter), the
     * index contains qualified names for all composed functions — including
     * framework ones like `Card` or `OutlinedTextField`. This lets us
     * distinguish `androidx.compose.material3.Card` (framework) from
     * `com.myapp.Card` (user) at runtime, without maintaining a fragile
     * manual list.
     *
     * Names not found in the index are Compose compiler/runtime
     * infrastructure Group markers (e.g. `Content`, `ReusableComposeNode`)
     * that never trigger [traceEventStart]. These are conservatively
     * treated as framework composables.
     */
    internal fun resolveUserComposable(groupName: String?, fallback: String?): String? {
        if (groupName == null) return fallback
        if (groupName.startsWith("remember(") || groupName == "remember") return fallback
        if (frameworkPrefixes.any { groupName.startsWith(it) }) return fallback
        if (groupName.isEmpty() || !groupName.first().isUpperCase()) return fallback

        // Look up the simple name in the traced composable index
        val matches = synchronized(simpleNameIndexLock) { simpleNameIndex[groupName] }
        if (matches != null && matches.isNotEmpty()) {
            // If ANY match resolves to a user package, treat as user composable
            val userMatch = matches.firstOrNull { traced ->
                frameworkPrefixes.none { prefix -> traced.qualifiedName.startsWith(prefix) }
            }
            return userMatch?.qualifiedName ?: fallback  // all framework → skip
        }

        // Not in simpleNameIndex → Compose infrastructure Group name (Content, Layout,
        // ComposeNode, etc.) or a composable that wasn't traced. Conservatively treat
        // as framework to avoid stealing testTag mappings from the real user composable.
        return fallback
    }

    // ── Tag-to-function mapping (delegates to platform) ──────────────

    /**
     * Get the function name that a testTag maps to, or null if unknown.
     */
    fun getFunctionNameForTag(testTag: String): String? = synchronized(testTagToFunctionLock) {
        testTagToFunction[testTag]
    }

    /**
     * Walk the CompositionData Group trees to build a mapping from
     * Modifier.testTag values to the enclosing user composable function name.
     * Delegates to platform-specific implementation.
     */
    fun buildTagMapping(compositionData: Set<CompositionData>) {
        synchronized(lastSeenTagsLock) { lastSeenTags.clear() }
        platformBuildTagMapping(compositionData)
    }

    /**
     * Called from the Choreographer frame callback. Sets [isFrameLoopPass] so
     * that detectTagRecomposition populates [tagsWithFingerprint].
     */
    fun buildTagMappingFromFrameLoop(compositionData: Set<CompositionData>) {
        isFrameLoopPass = true
        try {
            buildTagMapping(compositionData)
        } finally {
            isFrameLoopPass = false
        }
    }

    // ── Query APIs ───────────────────────────────────────────────────

    fun getRecompositionCount(name: String): Int {
        synchronized(recompositionCountsLock) {
            // Try exact match first (qualified name)
            recompositionCounts[name]?.let { return it }
            // Fallback: try as simple name (match by suffix)
            val matches = recompositionCounts.entries.filter {
                it.key.endsWith(".$name") || it.key == name
            }
            return when {
                matches.size == 1 -> matches.first().value
                matches.size > 1 -> {
                    if (isLoggingEnabled()) {
                        platformLog(TAG, "Ambiguous name '$name' matches ${matches.size} composables, using first match")
                    }
                    matches.first().value
                }
                else -> 0
            }
        }
    }

    fun getAllRecompositionCounts(): Map<String, Int> {
        return synchronized(recompositionCountsLock) {
            recompositionCounts.toMap()
        }
    }

    fun getAllTracedComposables(): List<TracedComposable> {
        return synchronized(keyToInfoLock) {
            keyToInfo.values.toList()
        }
    }

    fun getRecompositionEvents(functionName: String): List<RecompositionEvent> {
        return synchronized(recompositionEventsLock) {
            recompositionEvents[functionName]?.toList() ?: emptyList()
        }
    }

    fun getPerTagRecompositionCount(tag: String): Int? {
        // Use the tag's mapped composable key for deterministic counting.
        // Key-based counting works for both single-instance and multi-instance
        // functions, as long as the tag's key is unique (no other tag shares it).
        // This is the common case for LazyColumn items with explicit key{}.
        // Falls back to fingerprint-based counting only for true key collisions.
        val key = synchronized(testTagToKeyLock) { testTagToKey[tag] }
        if (key != null) {
            val functionName = synchronized(testTagToFunctionLock) { testTagToFunction[tag] }
            if (functionName != null &&
                (!isMultiInstanceFunction(functionName) || isKeyUniqueForTag(tag))) {
                val (total, baseline) = synchronized(compositionCountsLock) {
                    (compositionCounts[key] ?: 0) to (compositionCountBaseline[key] ?: 0)
                }
                return maxOf(0, maxOf(0, total - 1) - baseline)
            }
        }
        // Key collision or no key: use fingerprint-based count from tree walk.
        val perTagCount = synchronized(perTagLock) { perTagRecompCounts[tag] }
        if (perTagCount != null) return perTagCount
        // If fingerprint tracking has compared this tag at least once but found no
        // change, return 0 (stable) rather than null. Null would cause fallback to
        // the function-level count which is shared across ALL instances.
        return if (synchronized(tagsWithFingerprintLock) { tag in tagsWithFingerprint }) 0 else null
    }

    fun getPerTagRecompositionEvents(tag: String): List<RecompositionEvent> =
        synchronized(perTagLock) {
            perTagRecompEvents[tag]?.toList() ?: emptyList()
        }

    fun getParameterChanges(tag: String): List<List<ParameterChange>> =
        synchronized(tagParamLock) {
            tagParameterChanges[tag]?.toList() ?: emptyList()
        }

    /**
     * Returns true if the given function has multiple tags mapped to it,
     * indicating multiple instances of the same composable in the tree.
     */
    fun isMultiInstanceFunction(functionName: String): Boolean {
        // Snapshot under individual locks to avoid nested lock acquisition
        val tagsSnapshot = synchronized(lastSeenTagsLock) { lastSeenTags.toSet() }
        val identitySnapshot = synchronized(tagToIdentityLock) { tagToIdentity.toMap() }
        synchronized(testTagToFunctionLock) {
            val identities = mutableSetOf<Any>()
            for ((tag, value) in testTagToFunction) {
                if (value == functionName && tag in tagsSnapshot) {
                    val id = identitySnapshot[tag]
                    if (id != null) {
                        identities.add(id)
                    } else {
                        identities.add(tag)
                    }
                    if (identities.size > 1) return true
                }
            }
            return false
        }
    }

    /**
     * Returns true if the tag's mapped composer key is unique (no other
     * currently-visible tag shares it). When true, compositionCounts[key]
     * is a per-instance count, so key-based deterministic counting is safe.
     */
    fun isKeyUniqueForTag(tag: String): Boolean {
        val key = synchronized(testTagToKeyLock) { testTagToKey[tag] } ?: return false
        val tagsSnapshot = synchronized(lastSeenTagsLock) { lastSeenTags.toSet() }
        synchronized(testTagToKeyLock) {
            for ((otherTag, otherKey) in testTagToKey) {
                if (otherTag != tag && otherKey == key && otherTag in tagsSnapshot) {
                    return false
                }
            }
        }
        return true
    }

    // ── Parameter diffing ────────────────────────────────────────────

    internal fun diffParams(old: List<ParamSnapshot>, new: List<ParamSnapshot>): List<ParameterChange> {
        val changes = mutableListOf<ParameterChange>()
        val oldByName = old.associateBy { it.name }
        val newByName = new.associateBy { it.name }

        for ((name, newSnap) in newByName) {
            val oldSnap = oldByName[name]
            if (oldSnap == null) {
                changes.add(ParameterChange(
                    parameterName = name ?: "<unnamed>",
                    oldValue = null,
                    newValue = newSnap.valueString,
                    changeType = ChangeType.ADDED
                ))
            } else if (oldSnap.valueHash != newSnap.valueHash) {
                val changeType = if (oldSnap.valueString == newSnap.valueString) {
                    ChangeType.REFERENCE_CHANGED
                } else {
                    ChangeType.VALUE_CHANGED
                }
                changes.add(ParameterChange(
                    parameterName = name ?: "<unnamed>",
                    oldValue = oldSnap.valueString,
                    newValue = newSnap.valueString,
                    changeType = changeType
                ))
            }
        }

        for ((name, oldSnap) in oldByName) {
            if (name !in newByName) {
                changes.add(ParameterChange(
                    parameterName = name ?: "<unnamed>",
                    oldValue = oldSnap.valueString,
                    newValue = null,
                    changeType = ChangeType.REMOVED
                ))
            }
        }

        return changes
    }

    // ── Reset ────────────────────────────────────────────────────────
    //
    // Lock ordering: when acquiring multiple locks in resetCounts(), always
    // use this order to prevent deadlocks:
    //   compositionCountsLock → recompositionCountsLock → recompositionEventsLock →
    //   testTagToFunctionLock → testTagToKeyLock → perTagLock → tagParamLock →
    //   lastSeenTagsLock → tagToIdentityLock → tagsWithFingerprintLock
    //
    // No method acquires more than one lock simultaneously; each lock is acquired
    // and released independently. Query methods use snapshot-based reads
    // (copy under one lock, then read under another) to avoid nested locking.

    /**
     * Clears all tracked state — recomposition counts, composition history,
     * tag mappings, and parsed composable info.
     *
     * Use between independent tests to ensure full isolation.
     */
    fun reset() {
        resetCounts()
        synchronized(compositionCountsLock) {
            compositionCounts.clear()
            compositionCountBaseline.clear()
        }
        synchronized(keyToInfoLock) { keyToInfo.clear() }
        synchronized(simpleNameIndexLock) { simpleNameIndex.clear() }
    }

    /**
     * Clears recomposition counts and tag mappings but preserves composition
     * history ([compositionCounts], [keyToInfo], [simpleNameIndex]).
     *
     * Use for mid-test resets where the same composition is still alive and
     * you want subsequent compositions of already-seen keys to still count
     * as recompositions rather than initial compositions.
     *
     * When a live composition is running, this also captures fingerprint
     * baselines from the current composition state so that per-tag tracking
     * can detect post-reset changes deterministically.
     */
    fun resetCounts() {
        // Snapshot current key-based recomposition counts as baseline so that
        // post-reset queries only report recompositions that occur after this point.
        synchronized(compositionCountsLock) {
            compositionCountBaseline.clear()
            for ((key, count) in compositionCounts) {
                compositionCountBaseline[key] = maxOf(0, count - 1)
            }
        }
        synchronized(recompositionCountsLock) { recompositionCounts.clear() }
        synchronized(recompositionEventsLock) { recompositionEvents.clear() }
        synchronized(testTagToFunctionLock) { testTagToFunction.clear() }
        synchronized(testTagToKeyLock) { testTagToKey.clear() }
        synchronized(perTagLock) {
            perTagRecompCounts.clear()
            tagParamFingerprints.clear()
            perTagRecompEvents.clear()
        }
        synchronized(tagParamLock) {
            tagParamSnapshots.clear()
            tagParameterChanges.clear()
        }
        synchronized(lastSeenTagsLock) { lastSeenTags.clear() }
        synchronized(tagToIdentityLock) { tagToIdentity.clear() }
        synchronized(tagsWithFingerprintLock) { tagsWithFingerprint.clear() }
        resetObserver()

        // Capture fingerprint baselines from current composition state.
        // On platforms with a running composition, this establishes a
        // deterministic reference point for per-tag change detection.
        val snapshots = currentCompositionsSnapshot()
        if (snapshots.isNotEmpty()) {
            try {
                buildTagMappingFromFrameLoop(snapshots)
            } catch (_: Throwable) {
                // On Android, this can fail during Activity transitions with
                // ComposeRuntimeError("Cannot start a writer when a reader is pending").
                // Silently skip baseline capture — the frame callback will
                // establish baselines later.
                return
            }
            // Clear counts from baseline capture — only post-reset changes should count.
            synchronized(recompositionCountsLock) { recompositionCounts.clear() }
            synchronized(recompositionEventsLock) { recompositionEvents.clear() }
            synchronized(perTagLock) {
                perTagRecompCounts.clear()
                perTagRecompEvents.clear()
            }
            synchronized(tagParamLock) { tagParameterChanges.clear() }
            // Update key-based baseline for compositions during baseline capture
            synchronized(compositionCountsLock) {
                compositionCountBaseline.clear()
                for ((key, count) in compositionCounts) {
                    compositionCountBaseline[key] = maxOf(0, count - 1)
                }
            }
        }
    }

    /**
     * Synchronously refresh the tag mapping from composition data.
     * Useful in tests where the asynchronous frame loop may not have
     * run yet when assertions are checked.
     */
    fun refreshTagMapping(compositionData: Set<CompositionData>) {
        buildTagMapping(compositionData)
    }

    /**
     * Returns a snapshot of the current composition data from the active activity.
     * Delegates to platform-specific runtime.
     */
    fun getCompositionSnapshots(): Set<CompositionData> =
        currentCompositionsSnapshot()
}
