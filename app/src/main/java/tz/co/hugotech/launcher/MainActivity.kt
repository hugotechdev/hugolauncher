package tz.co.hugotech.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Screen { Home, Drawer, Settings }

class MainActivity : ComponentActivity() {
    private val screen = mutableStateOf(Screen.Home)
    private val reloadKey = mutableIntStateOf(0)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reloadKey.intValue = reloadKey.intValue + 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        setContent {
            LauncherApp(screen, reloadKey.intValue)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
            // already gone
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home always brings you back to the home screen.
        screen.value = Screen.Home
    }
}

@Composable
fun LauncherApp(screenState: MutableState<Screen>, reloadKey: Int) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var screen by screenState

    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(reloadKey) {
        allApps = withContext(Dispatchers.Default) { AppRepository.loadApps(context) }
    }

    var theme by remember { mutableStateOf(prefs.iconTheme) }
    var accentId by remember { mutableStateOf(prefs.accent) }
    var showLabels by remember { mutableStateOf(prefs.showLabels) }
    var hidden by remember { mutableStateOf(prefs.hidden()) }
    val accent = accentOf(accentId)
    val apps = remember(allApps, hidden) { allApps.filter { it.pkg !in hidden } }

    val music = remember { MusicController(context) }
    var musicAccess by remember { mutableStateOf(music.hasAccess()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    musicAccess = music.hasAccess()
                    music.start()
                }
                Lifecycle.Event.ON_PAUSE -> music.stop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            music.stop()
        }
    }
    val badges = NotificationState.counts.value

    val launch: (AppInfo) -> Unit = { app ->
        prefs.recordLaunch(app.pkg)
        AppRepository.launch(context, app)
        screen = Screen.Home
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent.main,
            onPrimary = HC.Bg,
            surface = Color(0xFF1C1B1E),
            onSurface = HC.Text,
            surfaceContainer = Color(0xFF1C1B1E),
            surfaceContainerHigh = Color(0xFF232226),
            background = HC.Bg,
            onBackground = HC.Text
        )
    ) {
        Box(Modifier.fillMaxSize().background(HC.Bg)) {
            GoldenWallpaper(accent)
            HomeScreen(
                apps = apps,
                prefs = prefs,
                music = music,
                musicAccess = musicAccess,
                accent = accent,
                theme = theme,
                badges = badges,
                onOpenDrawer = { screen = Screen.Drawer },
                onOpenSettings = { screen = Screen.Settings },
                onLaunch = launch
            )
            AnimatedVisibility(
                visible = screen == Screen.Drawer,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                DrawerScreen(
                    apps = apps,
                    prefs = prefs,
                    theme = theme,
                    accent = accent,
                    showLabels = showLabels,
                    badges = badges,
                    onLaunch = launch,
                    onClose = { screen = Screen.Home },
                    onSettings = { screen = Screen.Settings },
                    onHide = { app ->
                        hidden = hidden + app.pkg
                        prefs.setHidden(hidden)
                    }
                )
            }
            AnimatedVisibility(
                visible = screen == Screen.Settings,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                SettingsScreen(
                    apps = apps,
                    allApps = allApps,
                    theme = theme,
                    accent = accent,
                    showLabels = showLabels,
                    hidden = hidden,
                    musicAccess = musicAccess,
                    onTheme = { theme = it; prefs.iconTheme = it },
                    onAccent = { accentId = it; prefs.accent = it },
                    onLabels = { showLabels = it; prefs.showLabels = it },
                    onUnhide = { pkg ->
                        hidden = hidden - pkg
                        prefs.setHidden(hidden)
                    },
                    onOpenMusicAccess = { music.openAccessSettings() },
                    onClose = { screen = Screen.Home }
                )
            }
        }
    }

    // Back goes to Home; on Home it does nothing (a launcher never closes).
    BackHandler(enabled = true) {
        if (screen != Screen.Home) screen = Screen.Home
    }
}
