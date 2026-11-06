package dejavu.internal

import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.runtime.tooling.IdentifiableRecomposeScope
import dejavu.UnexpectedRecompositionsError
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/** Matches anonymous-body commits to the same restart group's inspection identity. */
@OptIn(ComposeToolingApi::class)
internal object ExactTagMapping {
    private data class Owner(val identity: Any?, val key: Int, val name: String, val anonymous: Boolean)
    private data class Occurrence(val node: Any, val owner: Owner?)
    private val lock = SynchronizedObject()
    private val occurrences = mutableMapOf<String, Occurrence>()
    private val ambiguousTags = mutableSetOf<String>()

    fun refresh(compositions: Set<CompositionData>) {
        if (!ExactScopeCounts.isActive) {
            clear()
            return
        }
        val next = mutableMapOf<String, Occurrence>()
        val ambiguous = mutableSetOf<String>()

        fun walk(group: CompositionGroup, enclosing: Owner?) {
            val key = group.key as? Int
            val traced = key?.let {
                synchronized(DejavuTracer.keyToInfoLock) { DejavuTracer.keyToInfo[it] }
            }
            val identity = group.identity
            val current = when {
                traced != null && !DejavuTracer.isFrameworkComposable(traced.fullInfo) ->
                    Owner(identity, key, traced.qualifiedName, traced.simpleName == "<anonymous>")
                traced != null -> enclosing
                // An untraced restart scope is not proof that this is framework
                // content. Stop attribution rather than borrowing an outer body.
                identity != null && group.data.any {
                    it is RecomposeScope && (it as? IdentifiableRecomposeScope)?.identity == identity
                } -> null
                else -> enclosing
            }
            val tag = CommonTagMapping.extractTestTag(group)
            if (tag != null && tag !in ambiguous) {
                val occurrence = Occurrence(group.node ?: identity ?: group, current)
                val previous = next[tag]
                if (previous != null && previous.node !== occurrence.node) {
                    next.remove(tag)
                    ambiguous.add(tag)
                } else next[tag] = occurrence
            }
            group.compositionGroups.forEach { walk(it, current) }
        }

        compositions.forEach { data -> data.compositionGroups.forEach { walk(it, null) } }
        synchronized(lock) {
            occurrences.clear()
            occurrences.putAll(next)
            ambiguousTags.clear()
            ambiguousTags.addAll(ambiguous)
        }
        val anonymousOwners = next.mapNotNull { (tag, occurrence) ->
            occurrence.owner?.takeIf { it.anonymous }?.let { tag to it }
        }
        synchronized(DejavuTracer.testTagToFunctionLock) {
            anonymousOwners.forEach { (tag, owner) -> DejavuTracer.testTagToFunction[tag] = owner.name }
        }
        synchronized(DejavuTracer.testTagToKeyLock) {
            anonymousOwners.forEach { (tag, owner) -> DejavuTracer.testTagToKey[tag] = owner.key }
        }
    }

    /** Null permits legacy accounting only for a positively identified named body. */
    fun count(tag: String): Int? {
        if (!ExactScopeCounts.isActive) return null
        val owner = synchronized(lock) {
            if (tag in ambiguousTags) unavailable(tag, "multiple nodes use this tag")
            occurrences[tag]?.owner
        } ?: unavailable(tag, "its composable owner could not be verified")
        if (!owner.anonymous) return null
        return owner.identity?.let { ExactScopeCounts.count(it, owner.key) }
            ?: unavailable(tag, "its lambda scope has no verified committed-execution counter")
    }

    private fun unavailable(tag: String, reason: String): Nothing =
        throw UnexpectedRecompositionsError(
            "Recomposition count unavailable for testTag='$tag': $reason.\n" +
                "  Exact lambda counting requires the owning composer to be captured by setTrackedContent.\n" +
                "  Independently created subcompositions and activity-owned content may require " +
                "an explicit capture boundary. Shared totals and sampled parameters are not exact counts."
        )

    fun clear() = synchronized(lock) {
        occurrences.clear()
        ambiguousTags.clear()
    }
}
