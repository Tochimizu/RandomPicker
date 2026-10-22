@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package fr.randommediapicker.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.randommediapicker.domain.*

private class LocalTreePicker : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?) = super.createIntent(context, input)
        .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}
private class LocalAudioPicker : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>) = super.createIntent(context, input)
        .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

@Composable
fun PickerApp(model: PickerViewModel, activity: ComponentActivity) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val library by model.library.collectAsStateWithLifecycle()
    val scanning by model.scanning.collectAsStateWithLifecycle()
    val progress by model.progress.collectAsStateWithLifecycle()
    val permissionLost by model.permissionLost.collectAsStateWithLifecycle()
    val running by model.playback.running.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(0) }
    var returnScreen by rememberSaveable { mutableStateOf(0) }
    var immersive by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val snackbar = remember { SnackbarHostState() }
    val treePicker = rememberLauncherForActivityResult(LocalTreePicker(), model::chooseRootOrIgnore)
    val audioPicker = rememberLauncherForActivityResult(LocalAudioPicker()) { it?.let(model::chooseMusic) }
    val systemDark = isSystemInDarkTheme()
    val dark = settings.theme == ThemeMode.DARK || (settings.theme == ThemeMode.SYSTEM && systemDark)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { model.checkPermission(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { resumed = false; model.playback.setForeground(false) }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); model.playback.setForeground(false) }
    }
    LaunchedEffect(resumed, scanning, permissionLost) { model.playback.setForeground(resumed && !scanning && !permissionLost) }
    LaunchedEffect(screen) { model.playback.setVisible(screen == 1) }
    LaunchedEffect(model) { model.events.collect { snackbar.showSnackbar(it) } }
    SideEffect {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (immersive && screen == 1) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
        activity.window.decorView.keepScreenOn = running && settings.keepScreenOn
        val orientation = when (settings.rotation) {
            RotationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            RotationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            RotationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        if (activity.requestedOrientation != orientation) activity.requestedOrientation = orientation
    }
    BackHandler(screen != 0 || immersive) {
        if (immersive) immersive = false else screen = if (screen == 2) returnScreen else 0
    }

    PickerTheme(settings.theme) {
        Scaffold(
            topBar = {
                if (!immersive || screen != 1) TopAppBar(
                    title = {
                        Column {
                            Text(listOf("Bibliothèque", "Mosaïque", "Paramètres")[screen])
                            Text("Random Media Picker", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        if (screen != 0) IconButton(onClick = { screen = if (screen == 2) returnScreen else 0 }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                        }
                    },
                    actions = {
                        if (screen != 2) IconButton(onClick = { returnScreen = screen; screen = 2 }) { Icon(Icons.Default.Settings, "Paramètres") }
                    },
                )
            },
            bottomBar = {
                if (!immersive || screen != 1) NavigationBar {
                    NavigationBarItem(selected = screen == 0, onClick = { screen = 0 },
                        icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Bibliothèque") })
                    NavigationBarItem(selected = screen == 1, onClick = { screen = 1 },
                        icon = { Icon(Icons.Default.GridView, null) }, label = { Text("Mosaïque") })
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    0 -> LibraryScreen(library, scanning, progress, permissionLost,
                        chooseRoot = { treePicker.launch(library.root?.treeUri?.let(Uri::parse)) },
                        refresh = model::refresh, select = model::selectFolder,
                        play = { screen = 1 })
                    1 -> GridScreen(model, settings, library.eligibleCount, permissionLost,
                        immersive = immersive, toggleImmersive = { immersive = !immersive },
                        openLibrary = { screen = 0 }, openSettings = { returnScreen = 1; screen = 2 })
                    2 -> SettingsScreen(settings, { model.updateSettings(it) },
                        chooseMusic = { audioPicker.launch(arrayOf("audio/*")) },
                        chooseRoot = { treePicker.launch(library.root?.treeUri?.let(Uri::parse)) })
                }
            }
        }
    }
}

private fun PickerViewModel.chooseRootOrIgnore(uri: Uri?) { uri?.let(::chooseRoot) }
