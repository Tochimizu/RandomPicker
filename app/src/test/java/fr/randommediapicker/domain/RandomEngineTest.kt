package fr.randommediapicker.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RandomEngineTest {
    @Test fun emptySetDoesNotStartPlayback() = runTest {
        val engine = SessionRandomEngine(Random(1))
        engine.initialize(emptyList(), RandomMode.SHUFFLE)
        assertNull(engine.next())
        assertTrue(engine.nextBatch(9).isEmpty())
    }

    @Test fun shuffleDistributesThirtyFilesExactlyOncePerCycle() = runTest {
        val engine = SessionRandomEngine(Random(2))
        val ids = (1..30).map(Int::toString)
        engine.initialize(ids, RandomMode.SHUFFLE)
        repeat(4) { epoch ->
            val picks = List(30) { engine.next()!! }
            assertEquals(ids.toSet(), picks.map { it.mediaId }.toSet())
            assertEquals(setOf(epoch + 1L), picks.map { it.cycleNumber }.toSet())
        }
    }

    @Test fun simultaneousRequestsConsumeUniqueQueueEntries() = runTest {
        val engine = SessionRandomEngine(Random(3))
        engine.initialize((1..100).map(Int::toString), RandomMode.SHUFFLE)
        val picks = (1..100).map { async(Dispatchers.Default) { engine.next()!!.mediaId } }.awaitAll()
        assertEquals(100, picks.toSet().size)
    }

    @Test fun globalNextHasNoDuplicatesEvenAcrossCycleBoundary() = runTest {
        val engine = SessionRandomEngine(Random(4))
        engine.initialize((1..11).map(Int::toString), RandomMode.SHUFFLE)
        repeat(30) { assertEquals(9, engine.nextBatch(9).map { it.mediaId }.toSet().size) }
    }

    @Test fun randomModeAvoidsOtherVisibleCellsWhenPossible() = runTest {
        val engine = SessionRandomEngine(Random(5))
        engine.initialize(listOf("a", "b", "c"), RandomMode.PURE_RANDOM)
        repeat(100) { assertEquals("c", engine.next(setOf("a", "b"))!!.mediaId) }
    }

    @Test fun randomModeAllowsRepeatsBeforeExhaustion() = runTest {
        val engine = SessionRandomEngine(Random(6))
        engine.initialize(listOf("a", "b"), RandomMode.PURE_RANDOM)
        val picks = List(50) { engine.next()!!.mediaId }
        assertTrue(picks.zipWithNext().any { it.first == it.second })
    }

    @Test fun singleFileFillsGridWithDocumentedFallback() = runTest {
        val engine = SessionRandomEngine(Random(7))
        engine.initialize(listOf("a", "a"), RandomMode.SHUFFLE)
        assertEquals(List(16) { "a" }, engine.nextBatch(16).map { it.mediaId })
    }

    @Test fun changingEligibilityDropsRemovedIds() = runTest {
        val engine = SessionRandomEngine(Random(8))
        engine.initialize(listOf("old", "new"), RandomMode.SHUFFLE)
        engine.next()
        engine.initialize(listOf("new"), RandomMode.SHUFFLE)
        assertEquals("new", engine.next()!!.mediaId)
    }

    @Test fun shuffleDefersVisibleEntriesWithoutSkippingCycle() = runTest {
        val engine = SessionRandomEngine(Random(9))
        engine.initialize(listOf("a", "b", "c"), RandomMode.SHUFFLE)
        assertEquals("c", engine.next(setOf("a", "b"))!!.mediaId)
        val remaining = List(2) { engine.next(setOf("a", "b"))!!.mediaId }.toSet()
        assertEquals(setOf("a", "b"), remaining)
        assertEquals(2L, engine.next()!!.cycleNumber)
    }

    @Test fun temporaryShuffleExclusionExpiresAtNextCycle() = runTest {
        val engine = SessionRandomEngine(Random(10))
        val ids = listOf("a", "b", "c", "d")
        engine.initialize(ids, RandomMode.SHUFFLE)
        val first = engine.next()!!.mediaId
        val excluded = ids.first { it != first }
        engine.excludeForCurrentCycle(excluded)
        val rest = List(2) { engine.next()!! }
        assertTrue(rest.none { it.mediaId == excluded })
        assertEquals(setOf(1L), rest.map { it.cycleNumber }.toSet())
        val nextCycle = engine.nextBatch(4)
        assertEquals(ids.toSet(), nextCycle.map { it.mediaId }.toSet())
        assertEquals(setOf(2L), nextCycle.map { it.cycleNumber }.toSet())
    }

    @Test fun pureRandomExclusionsAreRestoredByReset() = runTest {
        val engine = SessionRandomEngine(Random(11))
        engine.initialize(listOf("a"), RandomMode.PURE_RANDOM)
        engine.excludeForCurrentCycle("a")
        assertNull(engine.next())
        engine.reset()
        assertEquals("a", engine.next()!!.mediaId)
    }

    @Test fun exclusionImmediatelyAfterChangingModeAffectsFirstCycle() = runTest {
        val engine = SessionRandomEngine(Random(12))
        engine.initialize(listOf("a", "b", "c"), RandomMode.SHUFFLE)
        engine.excludeForCurrentCycle("a")
        assertEquals(setOf("b", "c"), engine.nextBatch(2).map { it.mediaId }.toSet())
        assertEquals(2L, engine.next()!!.cycleNumber)
    }
}
