package dejavu

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.ControlledComposition
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.runtime.tooling.IdentifiableRecomposeScope
import androidx.compose.runtime.tooling.setObserver
import dejavu.internal.DejavuTracer
import dejavu.internal.ExactScopeCounts
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Direct runtime control separates execution, successful application, and abandonment. */
@OptIn(ExperimentalComposeRuntimeApi::class, InternalComposeApi::class, InternalComposeTracingApi::class, ComposeToolingApi::class)
class ExactScopeCommitTest {
    @Test
    fun bodyExecution_isCountedOnlyAfterChangesAreApplied() = withComposition { fixture ->
        fixture.composeAndApply()
        val owner = fixture.owner()
        val baseline = fixture.effects.value
        assertEquals(0, owner.count())
        fixture.recompose()
        assertEquals(baseline, fixture.effects.value)
        assertEquals(0, owner.count(), "An uncommitted body trace must not count")
        fixture.apply()
        assertEquals(1, fixture.effects.value - baseline)
        assertEquals(1, owner.count())
    }

    @Test
    fun abandonedChanges_doNotCountTheirExecutedBody() = withComposition { fixture ->
        fixture.composeAndApply()
        val owner = fixture.owner()
        val baseline = fixture.effects.value
        fixture.recompose()
        fixture.composition.abandonChanges()
        assertEquals(baseline, fixture.effects.value)
        assertEquals(0, owner.count())
        fixture.recompose()
        fixture.apply()
        assertEquals(1, fixture.effects.value - baseline)
        assertEquals(1, owner.count())
    }

    @Test
    fun thrownBody_doesNotCommitItsTrace() = withComposition { fixture ->
        fixture.composeAndApply()
        val owner = fixture.owner()
        val baseline = fixture.effects.value
        fixture.fail = true
        assertFailsWith<ProbeFailure> { fixture.recompose() }
        assertEquals(baseline, fixture.effects.value)
        assertEquals(0, owner.count())
    }

    @Test
    fun closedSession_rejectsPendingEffectsAndLateAttachment() = withComposition { fixture ->
        fixture.composeAndApply()
        val owner = fixture.owner()
        fixture.recompose()
        ExactScopeCounts.clear()
        fixture.capturedComposer?.let { ExactScopeCounts.attachComposer(it) }
        fixture.apply()
        assertNull(owner.count())
        assertEquals(false, ExactScopeCounts.isActive)
    }

    @Test
    fun reusableGroup_replacesTheScopeLifetimeWithoutInheritingItsCount() = withComposition { fixture ->
        fixture.composeAndApply()
        val originalScope = assertNotNull(fixture.scope)
        val originalOwner = fixture.owner()
        repeat(2) { fixture.recompose(); fixture.apply() }
        assertEquals(2, originalOwner.count())
        fixture.visible = false
        fixture.recompose()
        fixture.apply()
        assertNull(originalOwner.count(), "Disposal must discard the old lifetime")
        fixture.visible = true
        fixture.recompose()
        fixture.apply()
        val replacementOwner = fixture.owner()
        assertNotSame(originalScope, fixture.scope)
        assertEquals(originalOwner.identity, replacementOwner.identity, "Exercise actual anchor reuse")
        assertEquals(0, replacementOwner.count())
        val baseline = fixture.effects.value
        fixture.recompose()
        fixture.apply()
        assertEquals(1, fixture.effects.value - baseline)
        assertEquals(1, replacementOwner.count())
    }

    private fun withComposition(block: (Fixture) -> Unit) {
        val previousEnabled = DejavuTracer.enabled
        DejavuTracer.enabled = true
        DejavuTracer.reset()
        Composer.setTracer(DejavuTracer)
        ExactScopeCounts.start()
        val fixture = Fixture()
        val observer = fixture.composition.setObserver(ExactScopeCounts)
        try {
            block(fixture)
        } finally {
            ExactScopeCounts.clear()
            observer?.dispose()
            fixture.composition.dispose()
            fixture.recomposer.cancel()
            fixture.job.cancel()
            DejavuTracer.enabled = previousEnabled
            Composer.setTracer(if (previousEnabled) DejavuTracer else null)
        }
    }

    private inner class Fixture {
        val job = Job()
        val recomposer = Recomposer(job)
        val composition = ControlledComposition(EmptyApplier(), recomposer)
        val effects = atomic(0)
        var visible = true
        var fail = false
        var scope: RecomposeScope? = null
        var capturedComposer: Composer? = null

        fun composeAndApply() {
            composition.composeContent {
                val composer = currentComposer
                capturedComposer = composer
                ExactScopeCounts.attachComposer(composer)
                ReusableContentHost(visible) { commitBody(this@Fixture) }
            }
            apply()
        }

        fun recompose() {
            composition.invalidateAll()
            assertTrue(composition.recompose(), "The fixture must execute and queue changes")
        }

        fun apply() {
            composition.applyChanges()
            composition.applyLateChanges()
            composition.changesApplied()
        }

        fun owner(): Owner {
            val identity = assertNotNull((scope as? IdentifiableRecomposeScope)?.identity)
            fun findKey(group: CompositionGroup): Int? {
                if (group.identity == identity) return group.key as? Int
                return group.compositionGroups.firstNotNullOfOrNull { findKey(it) }
            }
            val key = assertNotNull(capturedComposer?.compositionData?.compositionGroups
                ?.firstNotNullOfOrNull { findKey(it) })
            return Owner(identity, key)
        }
    }

    private data class Owner(val identity: Any, val key: Int) {
        fun count() = ExactScopeCounts.count(identity, key)
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    private class ProbeFailure : RuntimeException("Deliberate composition failure")

    private val commitBody: @Composable (Fixture) -> Unit = { fixture ->
        fixture.scope = currentRecomposeScope
        SideEffect { fixture.effects.incrementAndGet() }
        if (fixture.fail) throw ProbeFailure()
    }
}
