@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package fr.randommediapicker

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import fr.randommediapicker.domain.PlaybackSettings
import fr.randommediapicker.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaybackInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsExposeStopResumePreviousNextReloadAndSound() {
        val calls = mutableListOf<String>()
        compose.setContent {
            var playing by remember { mutableStateOf(true) }
            var muted by remember { mutableStateOf(false) }
            PickerTheme {
                PlaybackControls(playing, muted, true,
                    stop = { calls.add("stop"); playing = false }, resume = { calls.add("resume"); playing = true },
                    previous = { calls.add("previous") }, next = { calls.add("next") },
                    reload = { calls.add("reload") }, sound = { calls.add("sound"); muted = !muted })
            }
        }
        compose.onNodeWithContentDescription("Stop").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Reprendre la lecture").performClick()
        compose.onNodeWithContentDescription("Précédent").performClick()
        compose.onNodeWithContentDescription("Next : suivant").performClick()
        compose.onNodeWithContentDescription("Reload : recommencer le cycle").performClick()
        compose.onNodeWithContentDescription("Son : couper").performClick()
        compose.onNodeWithContentDescription("Son : activer").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("stop", "resume", "previous", "next", "reload", "sound"), calls) }
    }

    @Test fun rightAdvancesLeftReturnsAndDraggingDoesNotTriggerTap() {
        var next = 0
        var previous = 0
        var taps = 0
        compose.setContent {
            Box(Modifier.size(300.dp).testTag("media").navigationSwipes({ previous++ }, { next++ })) {
                Box(Modifier.fillMaxSize().combinedClickable(onClick = { taps++ }, onLongClick = {}))
            }
        }
        compose.onNodeWithTag("media").performTouchInput { swipeRight() }
        compose.onNodeWithTag("media").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, next); assertEquals(1, previous); assertEquals(0, taps) }
        compose.onNodeWithTag("media").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, taps) }
    }

    @Test fun libraryMusicSelectionSupportsChoosingReplacingAndRemoving() {
        var choices = 0
        compose.setContent {
            var settings by remember { mutableStateOf(PlaybackSettings()) }
            PickerTheme {
                MusicSelection(settings,
                    chooseMusic = { choices++; settings = settings.copy(musicUri = "content://local/background.ogg", musicEnabled = true) },
                    removeMusic = { settings = settings.copy(musicUri = null, musicEnabled = false) },
                    enableMusic = { settings = settings.copy(musicEnabled = it) })
            }
        }
        compose.onNodeWithText("Aucun son de fond").assertIsDisplayed()
        compose.onNodeWithText("Choisir un son de fond").performClick()
        compose.onNodeWithText("Changer le son de fond").performClick()
        compose.runOnIdle { assertEquals(2, choices) }
        compose.onNodeWithText("Retirer le son de fond").performClick()
        compose.onNodeWithText("Aucun son de fond").assertIsDisplayed()
    }
}
