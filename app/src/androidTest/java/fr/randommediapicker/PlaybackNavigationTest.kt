@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package fr.randommediapicker

import androidx.test.platform.app.InstrumentationRegistry
import fr.randommediapicker.data.MediaFile
import fr.randommediapicker.domain.*
import fr.randommediapicker.media.PlaybackCoordinator
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackNavigationTest {
    private fun playbackTest(test: suspend TestScope.(PlaybackCoordinator) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            runTest {
                val playback = PlaybackCoordinator(instrumentation.targetContext, backgroundScope, {}, {})
                try {
                    playback.updateSettings(PlaybackSettings(gridSize = 2, imageSeconds = 120))
                    playback.updateLibrary((1..100).map {
                        MediaFile("content://test/$it", "folder", "$it.jpg", "$it.jpg", "image/jpeg", MediaKind.IMAGE, 1, 0)
                    })
                    playback.setVisible(true)
                    playback.setForeground(true)
                    runCurrent()
                    test(playback)
                } finally { playback.release() }
            }
        }
    }

    @Test fun gridBackAndForwardRestoreExactIdsAndStopRemainsInForce() = playbackTest { playback ->
        val first = playback.cells.value.map { it.media?.id }
        playback.nextAll().join()
        val second = playback.cells.value.map { it.media?.id }
        assertNotEquals(first, second)
        playback.navigate(false).join()
        assertEquals(first, playback.cells.value.map { it.media?.id })
        assertEquals(setOf(0, 1, 2, 3), playback.historyPaused.value)
        playback.cells.value.forEach { playback.ready(it.index, it.token) }
        advanceTimeBy(130_000); runCurrent()
        assertEquals(first, playback.cells.value.map { it.media?.id })
        playback.stop()
        playback.navigate(true).join()
        assertEquals(second, playback.cells.value.map { it.media?.id })
        assertFalse(playback.playing.value)
        assertFalse(playback.running.value)
        playback.togglePause()
        assertTrue(playback.running.value)
    }

    @Test fun enlargedNavigationChangesOnlyItsCellAndKeepsTheViewOpen() = playbackTest { playback ->
        val first = playback.cells.value
        playback.setEnlarged(0)
        playback.navigate(true, 0).join()
        val next = playback.cells.value[0].media?.id
        assertNotEquals(first[0].media?.id, next)
        assertEquals(first.drop(1), playback.cells.value.drop(1))
        playback.navigate(false, 0).join()
        assertEquals(first[0].media?.id, playback.cells.value[0].media?.id)
        assertEquals(first.drop(1), playback.cells.value.drop(1))
        assertEquals(0, playback.enlarged.value)
        assertEquals(setOf(0), playback.historyPaused.value)
        playback.navigate(true, 0).join()
        assertEquals(next, playback.cells.value[0].media?.id)
        assertTrue(playback.historyPaused.value.isEmpty())
    }

    @Test fun oldestGridStateIsBoundedAndDoesNotWrap() = playbackTest { playback ->
        val states = mutableListOf(playback.cells.value.map { it.media?.id })
        repeat(15) {
            playback.nextAll().join()
            states.add(playback.cells.value.map { it.media?.id })
        }
        repeat(playback.historyBackLimit) { playback.navigate(false).join() }
        assertEquals(states[15 - playback.historyBackLimit], playback.cells.value.map { it.media?.id })
        val oldest = playback.cells.value
        playback.navigate(false).join()
        assertEquals(oldest, playback.cells.value)
        assertNotNull(playback.navigationNotice.value)
    }

    @Test fun reloadAndConfigurationChangesClearNavigationWithoutResumingStop() = playbackTest { playback ->
        playback.nextAll().join()
        playback.stop()
        playback.restartCycle().join()
        val restarted = playback.cells.value
        playback.navigate(false).join()
        assertEquals(restarted, playback.cells.value)
        assertFalse(playback.playing.value)
        playback.nextAll().join()
        playback.updateSettings(PlaybackSettings(gridSize = 1, randomMode = RandomMode.PURE_RANDOM))
        val resized = playback.cells.value
        playback.navigate(false).join()
        assertEquals(resized, playback.cells.value)
        assertEquals(1, resized.size)
        playback.nextAll().join()
        playback.resetNavigation().join()
        val reset = playback.cells.value
        playback.navigate(false).join()
        assertEquals(reset, playback.cells.value)
    }

    @Test fun automaticTransitionIsRecordedAndAStaleCallbackCannotReplaceRestoredMedia() = playbackTest { playback ->
        val first = playback.cells.value[0]
        playback.next(0, first.token)
        runCurrent()
        assertNotEquals(first.media?.id, playback.cells.value[0].media?.id)
        playback.setEnlarged(0)
        playback.navigate(false, 0).join()
        val restored = playback.cells.value[0]
        assertEquals(first.media?.id, restored.media?.id)
        playback.next(0, first.token)
        playback.next(0, restored.token)
        runCurrent()
        assertEquals(restored, playback.cells.value[0])
    }
}
