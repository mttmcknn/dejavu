@file:OptIn(androidx.compose.runtime.ExperimentalComposeRuntimeApi::class)

package dejavu.internal

import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.ObservableComposition
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TrackingCompositionObserversConcurrencyTest {
    @Test
    fun releasingOneOwnerPreservesObserverUntilTheOtherOwnerStops() {
        val composition = BlockingObservableComposition()
        val runtimeLease = TrackingCompositionObservers.acquire(composition)
        val observer = assertNotNull(composition.currentObserver.get())
        var helperLease: CompositionObserverHandle? = null
        try {
            val acquiredHelperLease = TrackingCompositionObservers.acquire(composition)
            helperLease = acquiredHelperLease
            assertEquals(1, composition.installCalls.get())
            acquiredHelperLease.dispose()
            assertEquals(0, composition.disposeCalls.get())
            assertSame(observer, composition.currentObserver.get())
            runtimeLease.dispose()
            assertEquals(1, composition.disposeCalls.get())
            assertNull(composition.currentObserver.get())
        } finally {
            helperLease?.dispose()
            runtimeLease.dispose()
        }
    }

    @Test
    fun pendingInstallerKeepsItsLeaseWhenConcurrentLeaseReturnsAndIsReleased() {
        val composition = BlockingObservableComposition(blockFirstInstall = true)
        val executor = Executors.newFixedThreadPool(2)
        val creator = executor.submit<CompositionObserverHandle> {
            TrackingCompositionObservers.acquire(composition)
        }
        var concurrentLease: CompositionObserverHandle? = null
        var concurrent: Future<CompositionObserverHandle>? = null
        try {
            composition.installEntered.awaitChecked("first installation did not begin")
            val concurrentAcquisition = executor.submit<CompositionObserverHandle> {
                TrackingCompositionObservers.acquire(composition)
            }
            concurrent = concurrentAcquisition
            // The creator is blocked inside Compose. A concurrent caller may itself hold a
            // composition lock, so it must return without waiting or installing a second observer.
            val returnedLease = concurrentAcquisition.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            concurrentLease = returnedLease
            assertEquals(1, composition.installCalls.get())
            assertNull(composition.currentObserver.get())
            returnedLease.dispose()
            returnedLease.dispose()
            assertEquals(0, composition.disposeCalls.get(), "the unreturned creator lease still owns the entry")

            composition.continueInstall.countDown()
            val creatorLease = creator.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertNotNull(composition.currentObserver.get())
            assertEquals(1, composition.installCalls.get())
            creatorLease.dispose()
            assertEquals(1, composition.disposeCalls.get())
            assertNull(composition.currentObserver.get())
            creatorLease.dispose()
            assertEquals(1, composition.disposeCalls.get(), "lease disposal is idempotent")
        } finally {
            composition.releaseAll()
            concurrentLease?.dispose()
            runCatching { creator.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).dispose() }
            runCatching { concurrent?.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)?.dispose() }
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS), "test workers must exit")
        }
    }

    @Test
    fun delayedOldDisposalCannotRemoveReplacementEntryObserver() {
        val composition = BlockingObservableComposition(blockFirstDispose = true)
        val oldLease = TrackingCompositionObservers.acquire(composition)
        val oldObserver = assertNotNull(composition.currentObserver.get())
        val executor = Executors.newFixedThreadPool(2)
        val oldDisposal = executor.submit { oldLease.dispose() }
        var replacementLease: CompositionObserverHandle? = null
        var replacement: Future<CompositionObserverHandle>? = null
        try {
            composition.disposeEntered.awaitChecked("old underlying disposal did not begin")
            val replacementAcquisition = executor.submit<CompositionObserverHandle> {
                TrackingCompositionObservers.acquire(composition)
            }
            replacement = replacementAcquisition
            // This must complete while Compose is still disposing the old observer. Holding
            // the registry lock during disposal would block this acquire and can invert locks.
            val returnedLease = replacementAcquisition.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            replacementLease = returnedLease
            val replacementObserver = assertNotNull(composition.currentObserver.get())
            assertNotSame(oldObserver, replacementObserver, "retired entries require distinct observer identities")
            assertEquals(2, composition.installCalls.get())

            composition.continueDispose.countDown()
            oldDisposal.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertSame(replacementObserver, composition.currentObserver.get(), "stale cleanup must preserve the new installation")
            assertEquals(1, composition.disposeCalls.get())
            returnedLease.dispose()
            assertNull(composition.currentObserver.get())
            assertEquals(2, composition.disposeCalls.get())
        } finally {
            composition.releaseAll()
            runCatching { oldDisposal.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            oldLease.dispose()
            replacementLease?.dispose()
            runCatching { replacement?.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)?.dispose() }
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS), "test workers must exit")
        }
    }

    @Test
    fun failedInstallationReleasesCreatorEntrySoLaterAcquireCanInstall() {
        val composition = BlockingObservableComposition(failFirstInstall = true)
        assertFailsWith<IllegalStateException> { TrackingCompositionObservers.acquire(composition) }
        val lease = TrackingCompositionObservers.acquire(composition)
        try {
            assertEquals(2, composition.installCalls.get(), "a failed creator must not leave a permanently pending entry")
            assertNotNull(composition.currentObserver.get())
        } finally {
            lease.dispose()
        }
        assertNull(composition.currentObserver.get())
        assertEquals(1, composition.disposeCalls.get())
    }

    /** Models Compose's handle contract: disposal compares observer identity, not a generation. */
    private class BlockingObservableComposition(
        private val blockFirstInstall: Boolean = false,
        private val blockFirstDispose: Boolean = false,
        private val failFirstInstall: Boolean = false,
    ) : ObservableComposition {
        val installCalls = AtomicInteger()
        val disposeCalls = AtomicInteger()
        val currentObserver = AtomicReference<CompositionObserver?>()
        val installEntered = CountDownLatch(1)
        val continueInstall = CountDownLatch(1)
        val disposeEntered = CountDownLatch(1)
        val continueDispose = CountDownLatch(1)

        override fun setObserver(observer: CompositionObserver): CompositionObserverHandle {
            val installation = installCalls.incrementAndGet()
            if (installation == 1) {
                installEntered.countDown()
                if (blockFirstInstall) continueInstall.awaitChecked("test did not release initial installation")
                if (failFirstInstall) error("expected installation failure")
            }
            currentObserver.set(observer)
            return object : CompositionObserverHandle {
                override fun dispose() {
                    val disposal = disposeCalls.incrementAndGet()
                    if (disposal == 1) {
                        disposeEntered.countDown()
                        if (blockFirstDispose) continueDispose.awaitChecked("test did not release old disposal")
                    }
                    currentObserver.compareAndSet(observer, null)
                }
            }
        }

        fun releaseAll() {
            continueInstall.countDown()
            continueDispose.countDown()
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L

        fun CountDownLatch.awaitChecked(message: String) {
            assertTrue(await(TIMEOUT_SECONDS, TimeUnit.SECONDS), message)
        }
    }
}
