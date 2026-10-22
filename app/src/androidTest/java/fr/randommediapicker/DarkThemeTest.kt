package fr.randommediapicker

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import fr.randommediapicker.ui.DarkColors
import fr.randommediapicker.ui.PickerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DarkThemeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun defaultThemeIsDarkWithoutConsultingSystemTheme() {
        var dark = false
        compose.setContent {
            PickerTheme {
                dark = MaterialTheme.colorScheme.background == DarkColors.background
                Surface { Text("Bibliothèque") }
            }
        }
        compose.onNodeWithText("Bibliothèque").assertIsDisplayed()
        compose.runOnIdle { assertTrue(dark) }
    }
}
