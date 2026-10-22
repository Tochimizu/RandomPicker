package fr.randommediapicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import fr.randommediapicker.ui.PickerApp
import fr.randommediapicker.ui.PickerViewModel

class MainActivity : ComponentActivity() {
    private val model: PickerViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PickerApp(model, this) }
    }
}
