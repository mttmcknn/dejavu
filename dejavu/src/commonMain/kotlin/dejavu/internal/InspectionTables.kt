package dejavu.internal

import androidx.compose.runtime.tooling.CompositionData

/**
 * Compose registers this mutable collection in a HashSet of inspection collections. Its identity
 * must stay stable as subcompositions add and remove tables. Delegating only MutableSet operations
 * keeps Any's identity equality/hash instead of the backing set's changing content hash.
 *
 * This collection is a registration handle, not a value set: two empty collections must remain
 * distinct, and Compose must still find the same collection after its contents change.
 */
internal class InspectionTables(
    backing: MutableSet<CompositionData>,
) : MutableSet<CompositionData> by backing
