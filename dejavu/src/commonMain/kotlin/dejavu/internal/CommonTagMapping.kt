package dejavu.internal

import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.platform.InspectableValue
import kotlinx.atomicfu.locks.synchronized

/**
 * Cross-platform tag mapping that walks [CompositionGroup] trees directly
 * (no dependency on `ui-tooling-data`).
 *
 * Extracts testTag → composable function name mappings and tracks
 * per-instance recompositions using [CompositionGroup.identity].
 */
internal object CommonTagMapping {

    /**
     * Walks all [CompositionData] snapshots and builds tag → function mappings.
     * Updates [DejavuTracer]'s tag-related maps.
     */
    fun buildTagMapping(
        compositionData: Set<CompositionData>,
        excludedTags: Set<String> = emptySet(),
    ) {
        for (cd in compositionData) {
            for (group in cd.compositionGroups) {
                walkGroup(group, enclosingFunctionName = null, enclosingKey = null, excludedTags)
            }
        }
    }

    /**
     * Recursively walks the [CompositionGroup] tree.
     *
     * @param group The current composition group
     * @param enclosingFunctionName The nearest ancestor user composable's qualified name
     * @param enclosingKey The nearest ancestor user composable's compiler key (for per-instance tracking)
     */
    private fun walkGroup(
        group: CompositionGroup,
        enclosingFunctionName: String?,
        enclosingKey: Int?,
        excludedTags: Set<String>,
    ) {
        val resolved = resolveUserComposable(group)
        val currentFunctionName: String?
        val currentKey: Int?
        if (resolved != null) {
            currentFunctionName = resolved
            currentKey = group.key as? Int ?: enclosingKey
        } else {
            currentFunctionName = enclosingFunctionName
            currentKey = enclosingKey
        }

        // Check if this group has a LayoutNode with testTag
        val testTag = extractTestTag(group)
        if (testTag != null && currentFunctionName != null && testTag !in excludedTags) {
            registerTag(testTag, currentFunctionName, currentKey, group)
        }

        // Recurse into children
        for (child in group.compositionGroups) {
            walkGroup(child, currentFunctionName, currentKey, excludedTags)
        }
    }

    /**
     * Resolves the user composable qualified name for a [CompositionGroup].
     *
     * Tries two approaches:
     * 1. Parse [CompositionGroup.sourceInfo] (available when source information collection is enabled)
     * 2. Look up [CompositionGroup.key] in [DejavuTracer.keyToInfo] (populated by traceEventStart)
     *
     * Returns the qualified function name if this group represents a user composable, null otherwise.
     */
    private fun resolveUserComposable(group: CompositionGroup): String? {
        // Approach 1: sourceInfo (most reliable when available)
        val sourceInfoName = extractFunctionName(group.sourceInfo)
        if (sourceInfoName != null && !DejavuTracer.isFrameworkComposable(sourceInfoName)) {
            return sourceInfoName
        }

        // Approach 2: match group key against tracer's key→info map
        val key = group.key
        if (key is Int) {
            val traced = synchronized(DejavuTracer.keyToInfoLock) {
                DejavuTracer.keyToInfo[key]
            }
            if (traced != null && !DejavuTracer.isFrameworkComposable(traced.fullInfo)) {
                return traced.qualifiedName
            }
        }

        return null
    }

    /**
     * Extracts the composable function name from [CompositionGroup.sourceInfo].
     *
     * sourceInfo format: `C(FunctionName)...` or `CC(FunctionName)...`
     * where C = call, CC = inline call.
     */
    private fun extractFunctionName(sourceInfo: String?): String? {
        if (sourceInfo == null) return null

        // Find "C(" or "CC(" prefix
        val parenStart = when {
            sourceInfo.startsWith("C(") -> 2
            sourceInfo.startsWith("CC(") -> 3
            else -> return null
        }

        val parenEnd = sourceInfo.indexOf(')', parenStart)
        if (parenEnd <= parenStart) return null

        return sourceInfo.substring(parenStart, parenEnd).takeIf { it.isNotEmpty() }
    }

    /**
     * Extracts the testTag value from a [CompositionGroup]'s layout node modifiers.
     *
     * Walks [LayoutInfo.getModifierInfo] and checks for testTag via:
     * 1. [InspectableValue] API (preferred, Compose 1.3+)
     * 2. Semantics configuration fallback
     */
    private fun extractTestTag(group: CompositionGroup): String? {
        val node = group.node
        if (node !is LayoutInfo) return null

        for (modifierInfo in node.getModifierInfo()) {
            val tag = extractTagFromModifier(modifierInfo)
            if (tag != null) return tag
        }
        return null
    }

    /**
     * Attempts to extract a testTag value from a single [ModifierInfo].
     */
    private fun extractTagFromModifier(modifierInfo: ModifierInfo): String? {
        val modifier = modifierInfo.modifier

        // Approach 1: InspectableValue (works when isDebugInspectorInfoEnabled = true)
        if (modifier is InspectableValue) {
            if (modifier.nameFallback == "testTag") {
                for (element in modifier.inspectableElements) {
                    if (element.name == "tag") {
                        val value = element.value
                        if (value is String) return value
                    }
                }
            }
        }

        // Approach 2: Check if modifier class name suggests it's a testTag
        val className = modifier::class.simpleName ?: return null
        if ("TestTag" in className || "SemanticsModifier" in className) {
            return extractTagViaStringFallback(modifier)
        }

        return null
    }

    /**
     * Fallback: try to extract testTag from modifier's toString().
     * Format is typically: `SemanticsModifier(...TestTag = value...)` or similar.
     */
    private fun extractTagViaStringFallback(modifier: Any): String? {
        val str = try { modifier.toString() } catch (_: Exception) { return null }
        // Look for TestTag = "value" pattern
        val idx = str.indexOf("TestTag")
        if (idx < 0) return null
        val eqIdx = str.indexOf('=', idx)
        if (eqIdx < 0) return null
        val rest = str.substring(eqIdx + 1).trim()
        // Value might be quoted or unquoted
        return if (rest.startsWith('"')) {
            val endQuote = rest.indexOf('"', 1)
            if (endQuote > 0) rest.substring(1, endQuote) else null
        } else {
            rest.takeWhile { it != ',' && it != ')' && it != ']' }.trim().takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Registers a discovered testTag → function mapping and tracks per-instance recomposition.
     */
    private fun registerTag(tag: String, functionName: String, composableKey: Int?, group: CompositionGroup) {
        // Store tag → function name
        synchronized(DejavuTracer.testTagToFunctionLock) {
            DejavuTracer.testTagToFunction.getOrPut(tag) { functionName }
        }

        // Store tag → composable key for per-instance recomposition counting
        if (composableKey != null) {
            synchronized(DejavuTracer.testTagToKeyLock) {
                DejavuTracer.testTagToKey[tag] = composableKey
            }
        }

        // Track that this tag was seen in this mapping pass
        synchronized(DejavuTracer.lastSeenTagsLock) {
            DejavuTracer.lastSeenTags.add(tag)
        }

        // Per-instance tracking via identity
        val identity = group.identity
        synchronized(DejavuTracer.tagToIdentityLock) {
            val prevIdentity = DejavuTracer.tagToIdentity[tag]
            if (identity != null) {
                DejavuTracer.tagToIdentity[tag] = identity
            }

            // Detect per-tag recomposition
            detectTagRecomposition(tag, functionName, group, prevIdentity, identity)
        }
    }

    /**
     * Detects whether a tagged composable instance recomposed since the last mapping pass.
     *
     * Uses a data fingerprint (hash of slot data) to detect changes.
     * If the identity changed, this is a new instance — reset tracking.
     */
    private fun detectTagRecomposition(
        tag: String,
        functionName: String,
        group: CompositionGroup,
        prevIdentity: Any?,
        currentIdentity: Any?,
    ) {
        // Compute a fingerprint from the group's slot data
        val fingerprint = computeDataFingerprint(group)

        synchronized(DejavuTracer.perTagLock) {
            if (prevIdentity != null && prevIdentity != currentIdentity) {
                // Identity changed → new instance, reset this tag's tracking
                DejavuTracer.tagParamFingerprints[tag] = fingerprint
                return
            }

            val prevFingerprint = DejavuTracer.tagParamFingerprints[tag]
            if (prevFingerprint == null) {
                // First time seeing this tag — store fingerprint, don't count
                DejavuTracer.tagParamFingerprints[tag] = fingerprint
                return
            }

            // Mark reliable fingerprint comparison (frame-loop passes only)
            if (DejavuTracer.isFrameLoopPass) {
                synchronized(DejavuTracer.tagsWithFingerprintLock) {
                    DejavuTracer.tagsWithFingerprint.add(tag)
                }
            }

            if (fingerprint != prevFingerprint) {
                // Fingerprint changed → recomposition occurred
                DejavuTracer.tagParamFingerprints[tag] = fingerprint
                DejavuTracer.perTagRecompCounts[tag] =
                    (DejavuTracer.perTagRecompCounts[tag] ?: 0) + 1

                // Record event
                val events = DejavuTracer.perTagRecompEvents.getOrPut(tag) { mutableListOf() }
                (events as MutableList).add(
                    DejavuTracer.RecompositionEvent(
                        timestampMs = currentTimeMillis(),
                        dirty1 = 0,
                        qualifiedName = functionName,
                    )
                )
            }
        }
    }

    /**
     * Computes a fingerprint from a [CompositionGroup]'s slot data.
     * Used to detect whether parameters or state changed between mapping passes.
     */
    private fun computeDataFingerprint(group: CompositionGroup): Int {
        var hash = 17
        for (datum in group.data) {
            hash = hash * 31 + (datum?.hashCode() ?: 0)
        }
        return hash
    }
}
