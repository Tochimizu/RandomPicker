package fr.randommediapicker.domain

import org.junit.Assert.*
import org.junit.Test

class AudioPolicyTest {
    private val settings = PlaybackSettings(musicEnabled = true, musicUri = "content://music", audioMode = AudioMode.MIX)

    @Test fun defaultsMatchSpecificationAndUseDarkTheme() {
        val defaults = PlaybackSettings()
        assertEquals(ThemeMode.DARK, defaults.theme)
        assertEquals(3, defaults.gridSize)
        assertEquals(RandomMode.SHUFFLE, defaults.randomMode)
        assertEquals(5, defaults.imageSeconds)
        assertEquals(8, defaults.gifSeconds)
        assertEquals(VideoMode.COMPLETE, defaults.videoMode)
    }

    @Test fun duckingReducesMusicAndRestoresItWhenVideoStops() {
        val s = settings.copy(audioMode = AudioMode.DUCKING)
        assertEquals(.14f, AudioPolicy.levels(s, true, false).music, .001f)
        assertEquals(.7f, AudioPolicy.levels(s, false, false).music, .001f)
    }

    @Test fun priorityPausesMusicOnlyWhileVideoIsAudible() {
        val s = settings.copy(audioMode = AudioMode.VIDEO_PRIORITY)
        assertFalse(AudioPolicy.levels(s, true, false).playMusic)
        assertTrue(AudioPolicy.levels(s, false, false).playMusic)
        assertTrue(AudioPolicy.levels(s.copy(videoAudio = false), true, false).playMusic)
    }

    @Test fun masterMuteSilencesBothSources() {
        val levels = AudioPolicy.levels(settings, true, true)
        assertEquals(0f, levels.video, 0f)
        assertEquals(0f, levels.music, 0f)
    }

    @Test fun systemDuckingAndSoftwareVolumesMultiply() {
        val levels = AudioPolicy.levels(settings.copy(masterVolume = .5f, videoVolume = .8f), true, false, .2f)
        assertEquals(.08f, levels.video, .001f)
        assertEquals(.07f, levels.music, .001f)
    }

    @Test fun singleSourceModesSilenceTheOtherSource() {
        assertEquals(0f, AudioPolicy.levels(settings.copy(audioMode = AudioMode.MUSIC_ONLY), true, false).video, 0f)
        assertEquals(0f, AudioPolicy.levels(settings.copy(audioMode = AudioMode.VIDEO_ONLY), true, false).music, 0f)
    }
}
