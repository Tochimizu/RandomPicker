package fr.randommediapicker.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class NavigationHistoryTest {
    @Test fun capacityAllowsExactlyFiveOrTenReturnsAfterLongSessions() {
        for (limit in listOf(5, 10)) {
            val history = NavigationHistory<Int>(limit)
            repeat(1000) { history.record(it) }
            assertEquals(limit + 1, history.size)
            repeat(limit) { assertEquals(998 - it, history.move(false).value) }
            val oldest = history.current
            repeat(3) { assertNull(history.move(false).value); assertEquals(oldest, history.current) }
            repeat(limit) { assertNotNull(history.move(true).value) }
            assertEquals(999, history.current)
            assertFalse(history.isPast)
        }
    }

    @Test fun forwardReplaysKnownStatesAndDoesNotConsumeShuffle() = runTest {
        val engine = SessionRandomEngine(Random(50))
        engine.initialize((1..30).map(Int::toString), RandomMode.SHUFFLE)
        val history = NavigationHistory<List<String>>(10)
        val first = engine.nextBatch(9).map { it.mediaId }
        val second = engine.nextBatch(9).map { it.mediaId }
        history.record(first); history.record(second)
        repeat(20) {
            assertEquals(first, history.move(false).value)
            assertTrue(history.isPast)
            assertEquals(second, history.move(true).value)
            assertFalse(history.isPast)
        }
        val remaining = engine.nextBatch(12)
        assertEquals(setOf(1L), remaining.map { it.cycleNumber }.toSet())
        assertEquals(30, (first + second + remaining.map { it.mediaId }).toSet().size)
    }

    @Test fun newStateAfterGoingBackDiscardsOldFuture() {
        val history = NavigationHistory<String>(10)
        listOf("a", "b", "c").forEach(history::record)
        history.move(false)
        history.record("new")
        assertEquals("b", history.move(false).value)
        assertEquals("new", history.move(true).value)
        assertNull(history.move(true).value)
    }

    @Test fun unrelatedCellUpdatesDoNotDiscardTheBrowsedCellsFuture() {
        val history = NavigationHistory<String>(10)
        listOf("a", "b", "c").forEach(history::record)
        history.move(false)
        history.record("b")
        assertTrue(history.isPast)
        assertEquals("c", history.move(true).value)
    }

    @Test fun inaccessibleStatesAreSkippedInBothDirections() {
        val history = NavigationHistory<String>(10)
        listOf("a", "bad", "c", "bad2", "e").forEach(history::record)
        assertEquals(NavigationHistory.Move("c", 1), history.move(false) { !it.startsWith("bad") })
        assertEquals(NavigationHistory.Move("a", 1), history.move(false) { !it.startsWith("bad") })
        assertEquals("c", history.move(true).value)
        assertEquals("e", history.move(true).value)
        assertEquals(3, history.size)
    }

    @Test fun invalidFutureDoesNotLoseTheDisplayedState() {
        val history = NavigationHistory<String>(10)
        listOf("a", "bad").forEach(history::record)
        history.move(false)
        assertEquals(NavigationHistory.Move<String>(null, 1), history.move(true) { it != "bad" })
        assertEquals("a", history.current)
        assertFalse(history.isPast)
    }

    @Test fun resetPreventsReturningToOldSelection() {
        val history = NavigationHistory<String>(10)
        history.record("old"); history.record("recent")
        history.clear(); history.record("new root")
        assertNull(history.move(false).value)
        assertEquals("new root", history.current)
        assertEquals(1, history.size)
    }

    @Test fun excludedMediaCannotBeRestoredUntilTheirCycleExpires() = runTest {
        for (mode in RandomMode.entries) {
            val engine = SessionRandomEngine(Random(10))
            engine.initialize(listOf("a", "b", "c"), mode)
            engine.excludeForCurrentCycle("a")
            assertFalse("a" in engine.restorableIds())
            if (mode == RandomMode.SHUFFLE) engine.nextBatch(3) else engine.reset()
            assertTrue("a" in engine.restorableIds())
        }
    }

    @Test fun removingUnreadableMediaPreservesTheOtherExclusions() = runTest {
        val engine = SessionRandomEngine(Random(10))
        engine.initialize(listOf("excluded", "unreadable", "valid", "other"), RandomMode.SHUFFLE)
        engine.excludeForCurrentCycle("excluded")
        engine.removeUnavailable("unreadable")
        assertEquals(setOf("valid", "other"), engine.restorableIds())
        assertEquals(setOf("valid", "other"), engine.nextBatch(2).map { it.mediaId }.toSet())
    }
}
