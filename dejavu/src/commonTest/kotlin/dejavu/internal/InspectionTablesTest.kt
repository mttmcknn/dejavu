package dejavu.internal

import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InspectionTablesTest {
    @Test
    fun mutableInspectionCollectionsKeepTheirRegistrationIdentity() {
        val tables = InspectionTables(createInspectionTables())
        val other = InspectionTables(createInspectionTables())
        // Mirrors Compose's CompositionContextImpl.recordInspectionTable registry.
        val registry = hashSetOf<MutableSet<CompositionData>>(tables, other)
        assertEquals(2, registry.size, "Separate empty inspection collections are separate registrations")

        repeat(32) {
            tables.add(object : CompositionData {
                override val compositionGroups: Iterable<CompositionGroup> = emptyList()
                override val isEmpty: Boolean = true
            })
            assertTrue(registry.contains(tables), "Adding a subcomposition must not lose the registration")
            assertEquals(false, registry.add(tables), "Repeated registration must not duplicate the collection")
        }
        tables.clear()
        assertTrue(registry.remove(tables), "Compose must be able to unregister a mutated collection")
        assertEquals(1, registry.size)
    }
}
