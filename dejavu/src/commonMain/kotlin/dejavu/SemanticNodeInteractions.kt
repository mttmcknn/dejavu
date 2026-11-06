package dejavu

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.printToString
import dejavu.internal.ChangeType
import dejavu.internal.DejavuTracer
import dejavu.internal.RecomposeTracker
import dejavu.internal.describeInvalidationCauses
import dejavu.internal.describeStateDependencies
import dejavu.internal.isObserverAvailable

/**
 * Error thrown when recomposition count assertions fail.
 */
internal class UnexpectedRecompositionsError(
    message: String,
) : AssertionError(message)

/**
 * Asserts the number of recompositions for the composable associated with this node.
 *
 * Provide exactly ONE of:
 * - [exactly] — count must equal this value
 * - [atLeast] and/or [atMost] — count must fall within this range
 *
 * @return The same [SemanticsNodeInteraction] for chaining
 * @throws UnexpectedRecompositionsError if the actual count doesn't match
 */
public fun SemanticsNodeInteraction.assertRecompositions(
    exactly: Int? = null,
    atLeast: Int? = null,
    atMost: Int? = null,
): SemanticsNodeInteraction {
    require(exactly != null || atLeast != null || atMost != null) {
        "At least one of exactly, atLeast, or atMost must be specified"
    }
    require(exactly == null || (atLeast == null && atMost == null)) {
        "exactly cannot be combined with atLeast or atMost"
    }
    require((exactly ?: 0) >= 0) { "exactly must be non-negative, was $exactly" }
    require((atLeast ?: 0) >= 0) { "atLeast must be non-negative, was $atLeast" }
    require((atMost ?: 0) >= 0) { "atMost must be non-negative, was $atMost" }
    if (atLeast != null && atMost != null) {
        require(atLeast <= atMost) {
            "atLeast ($atLeast) must be <= atMost ($atMost)"
        }
    }

    return onRecompositions { count, functionName, testTag, node, interaction ->
        val failed = when {
            exactly != null -> count != exactly
            atLeast != null && atMost != null -> count < atLeast || count > atMost
            atLeast != null -> count < atLeast
            atMost != null -> count > atMost
            else -> false
        }
        if (failed) {
            val description = when {
                exactly != null -> "exactly $exactly"
                atLeast != null && atMost != null -> "between $atLeast and $atMost"
                atLeast != null -> "at least $atLeast"
                atMost != null -> "at most $atMost"
                else -> "unknown"
            }
            failRecompositionsExpectation(
                predicateDescription = description,
                actualCount = count,
                functionName = functionName,
                testTag = testTag,
                node = node,
                interaction = interaction,
            )
        }
    }
}

/**
 * Asserts that this composable was not recomposed (stable after initial composition).
 * Convenience alias for `assertRecompositions(exactly = 0)`.
 */
public fun SemanticsNodeInteraction.assertStable(): SemanticsNodeInteraction =
    assertRecompositions(exactly = 0)

// ── Internal helpers ─────────────────────────────────────────────

/**
 * Resolves the recomposition count for this semantics node and passes it
 * to [block] along with the resolved function name and testTag.
 */
private inline fun SemanticsNodeInteraction.onRecompositions(
    block: (count: Int, functionName: String?, testTag: String?, node: SemanticsNode, interaction: SemanticsNodeInteraction) -> Unit,
): SemanticsNodeInteraction {
    val node = fetchSemanticsNode("Failed to get semantics node for recomposition assertion")
    val testTag: String? = try {
        node.config[SemanticsProperties.TestTag]
    } catch (_: Throwable) {
        null
    }

    val count: Int
    val functionName: String?

    if (testTag != null) {
        val snapshots = DejavuTracer.getCompositionSnapshots()
        if (snapshots.isNotEmpty() || dejavu.internal.ExactScopeCounts.isActive) {
            DejavuTracer.refreshTagMapping(snapshots)
        }
        functionName = DejavuTracer.getFunctionNameForTag(testTag)
        val exactCount = dejavu.internal.ExactTagMapping.count(testTag)
        if (exactCount == null) DejavuTracer.requireSupportedTagCount(testTag)
        // Use per-tag fingerprint counting only for multi-instance functions
        val perTagCount = exactCount ?: if (functionName != null && DejavuTracer.isMultiInstanceFunction(functionName)) {
            DejavuTracer.getPerTagRecompositionCount(testTag)
        } else null
        count = if (perTagCount != null) {
            perTagCount
        } else if (functionName != null) {
            DejavuTracer.getRecompositionCount(functionName)
        } else {
            DejavuTracer.getRecompositionCount(testTag)
        }
    } else {
        throw UnexpectedRecompositionsError(
            "Recomposition assertion failed: node has no testTag.\n" +
                "  Add Modifier.testTag(\"...\") to the composable you want to assert on.\n\n" +
                "  Node:\n" +
                printToString().lines().joinToString("\n") { "    $it" }
        )
    }

    block(count, functionName, testTag, node, this)
    return this
}

/**
 * Formats a structured, multi-line error message and throws [UnexpectedRecompositionsError].
 */
private fun failRecompositionsExpectation(
    predicateDescription: String,
    actualCount: Int,
    functionName: String?,
    testTag: String?,
    node: SemanticsNode,
    interaction: SemanticsNodeInteraction,
): Nothing {
    val message = buildString {
        val hasCommittedLambdaCount = testTag?.let {
            dejavu.internal.ExactTagMapping.count(it) != null
        } ?: false
        // Header line
        if (testTag != null) {
            appendLine("Recomposition assertion failed for testTag='$testTag'")
        } else {
            appendLine("Recomposition assertion failed")
        }

        // Composable name + source location
        if (functionName != null) {
            val sourceLocation = DejavuTracer.getAllTracedComposables()
                .find { it.qualifiedName == functionName }
                ?.sourceLocation
            if (sourceLocation != null && sourceLocation.isNotEmpty()) {
                appendLine("  Composable: $functionName ($sourceLocation)")
            } else {
                appendLine("  Composable: $functionName")
            }
        } else if (testTag != null) {
            appendLine("  Warning: testTag '$testTag' could not be mapped to a composable function")
        }

        // Expected vs actual
        appendLine("  Expected: $predicateDescription recomposition(s)")
        if (actualCount == -1) {
            appendLine("  Actual: composable was never composed or isn't being tracked")
        } else {
            appendLine("  Actual: $actualCount recomposition(s)")
        }
        if (hasCommittedLambdaCount) {
            appendLine("  Count source: committed executions of this lambda instance")
        }

        // All tracked composables
        val allCounts = DejavuTracer.getAllRecompositionCounts()
        if (allCounts.isNotEmpty()) {
            appendLine()
            appendLine("  All tracked composables:")
            val maxNameLen = allCounts.keys.maxOf { simpleName(it).length }
            allCounts.entries.sortedBy { it.key }.forEach { (name, count) ->
                val simple = simpleName(name)
                val padded = simple.padEnd(maxNameLen)
                val marker = if (name == functionName) "  <-- FAILED" else ""
                appendLine("    $padded = $count$marker")
            }
        }

        // Recomposition timeline
        if (functionName != null) {
            val isMultiInstance = DejavuTracer.isMultiInstanceFunction(functionName)
            val perTagEvents = if (!hasCommittedLambdaCount && isMultiInstance && testTag != null) {
                DejavuTracer.getPerTagRecompositionEvents(testTag)
            } else {
                emptyList()
            }
            val events = perTagEvents.ifEmpty { DejavuTracer.getRecompositionEvents(functionName) }
            if (events.isNotEmpty()) {
                appendLine()
                if (hasCommittedLambdaCount) {
                    appendLine("  Function trace timeline (all instances; execution attempts):")
                } else if (isMultiInstance && perTagEvents.isNotEmpty()) {
                    appendLine("  Recomposition timeline (per-instance for testTag='$testTag'):")
                } else {
                    appendLine("  Recomposition timeline:")
                }
                val startTime = events.first().timestampMs
                events.forEachIndexed { index, event ->
                    val relativeMs = event.timestampMs - startTime
                    val dirtySlots = decodeDirtyBits(event.dirty1)
                    val parentInfo = if (event.parentName != null) " | parent: ${simpleName(event.parentName)}" else ""
                    appendLine("    #${index + 1} at +${relativeMs}ms — param slots changed: $dirtySlots$parentInfo")
                }
            }

            // Parameter change tracking section
            if (isMultiInstance && testTag != null) {
                val paramChanges = DejavuTracer.getParameterChanges(testTag)
                if (paramChanges.isNotEmpty()) {
                    appendLine()
                    appendLine("  Parameter changes (per-instance for testTag='$testTag'):")
                    paramChanges.forEachIndexed { index, changes ->
                        for (change in changes) {
                            val desc = when (change.changeType) {
                                ChangeType.VALUE_CHANGED ->
                                    "'${change.parameterName}' changed (${change.oldValue} → ${change.newValue})"
                                ChangeType.REFERENCE_CHANGED ->
                                    "'${change.parameterName}' reference changed"
                                ChangeType.ADDED ->
                                    "'${change.parameterName}' added (${change.newValue})"
                                ChangeType.REMOVED ->
                                    "'${change.parameterName}' removed (was ${change.oldValue})"
                            }
                            appendLine("    #${index + 1}: $desc")
                        }
                    }
                }
            }
        }

        // Causality info: prefer scope-level detail from CompositionObserver when
        // available; fall back to coarser snapshot-level info from RecomposeTracker.
        var hasObserverDetail = false
        if (functionName != null && isObserverAvailable()) {
            val invalidationDesc = describeInvalidationCauses(functionName)
            if (invalidationDesc != null) {
                appendLine()
                appendLine(invalidationDesc)
                hasObserverDetail = true
            }
            val depsDesc = describeStateDependencies(functionName)
            if (depsDesc != null) {
                appendLine(depsDesc)
                hasObserverDetail = true
            }
        }
        if (functionName != null && !hasObserverDetail) {
            val cause = RecomposeTracker.getCause(functionName)
            if (cause != null) {
                appendLine()
                appendLine("  Possible cause:")
                if (cause.stateChanges > 0) {
                    append("    ${cause.stateChanges} state change(s)")
                    if (cause.types.isNotEmpty()) {
                        append(" of type ${cause.types.distinct().joinToString(", ")}")
                    }
                    if (cause.sameValueWrites > 0) {
                        append(" (${cause.sameValueWrites} same-value write(s)!)")
                    }
                    appendLine()
                }
                if (cause.isParameterDriven) {
                    appendLine("    Parameter/parent change detected (dirty bits set)")
                }
            }
        }

        // Failed node detail dump
        appendLine()
        appendLine("  Node:")
        val nodeStr = interaction.printToString()
        for (line in nodeStr.lines()) {
            appendLine("    $line")
        }

        // Full semantic tree dump
        appendLine()
        appendLine("  Semantic tree:")
        var root = node
        while (root.parent != null) {
            root = root.parent!!
        }
        printSemanticsTree(this, root, node.id, indent = "    ")
    }

    throw UnexpectedRecompositionsError(message.trimEnd())
}

/** Extracts the simple name (last segment after '.') from a qualified name. */
private fun simpleName(qualifiedName: String): String =
    qualifiedName.substringAfterLast('.')

/** Decodes the dirty1 bitmask into a list of changed parameter slot indices. */
private fun decodeDirtyBits(dirty1: Int): String {
    if (dirty1 == 0) return "[]"
    // Each param uses 2 bits in the dirty mask, starting at bit 0.
    // Bits 00 = unchanged, anything else (01, 10, 11) = changed.
    // The first parameter occupies bits 0-1, second occupies bits 2-3, etc.
    val changedSlots = mutableListOf<Int>()
    for (slot in 0 until 16) { // max 16 params in dirty1
        val bits = (dirty1 shr (slot * 2)) and 0x3
        if (bits != 0) {
            changedSlots.add(slot)
        }
    }
    return changedSlots.toString()
}

/**
 * Recursively prints the semantics tree rooted at [node] into [sb].
 * The node matching [highlightId] is marked with ` <-- THIS NODE`.
 */
private fun printSemanticsTree(
    sb: StringBuilder,
    node: SemanticsNode,
    highlightId: Int,
    indent: String,
) {
    val marker = if (node.id == highlightId) "  <-- THIS NODE" else ""
    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
    val text = node.config.getOrNull(SemanticsProperties.Text)
        ?.joinToString(", ") { it.text }
    val props = buildList {
        if (tag != null) add("TestTag='$tag'")
        if (!text.isNullOrEmpty()) add("Text='$text'")
        // Include any other notable semantics properties
        node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(", ") { it }
            ?.let { if (it.isNotEmpty()) add("ContentDescription='$it'") }
        node.config.getOrNull(SemanticsProperties.Role)
            ?.let { add("Role=$it") }
    }
    val propsStr = if (props.isNotEmpty()) " [${props.joinToString(", ")}]" else ""
    sb.appendLine("${indent}Node #${node.id}$propsStr$marker")

    for (child in node.children) {
        printSemanticsTree(sb, child, highlightId, indent = "$indent  ")
    }
}
