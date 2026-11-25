package tz.co.hugotech.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** home · drawer · drawer-search · panel · settings:<page> */
class MainActivity : FragmentActivity() {
    private lateinit var state: LauncherState
    private val screen = mutableStateOf("home")
    private val reloadKey = mutableIntStateOf(0)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reloadKey.intValue = reloadKey.intValue + 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = LauncherState(applicationContext)
        state.activity = this
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        WatchWorker.schedule(applicationContext)
        Reminders.reschedule(applicationContext)
        setContent { LauncherApp(state, screen, reloadKey.intValue) }
    }

    override fun onStart() {
        super.onStart()
        state.widgetHost.start()
    }

    override fun onStop() {
        state.widgetHost.stop()
        super.onStop()
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
        screen.value = "home"
    }
}

@Composable
fun LauncherApp(s: LauncherState, screenState: MutableState<String>, reloadKey: Int) {
    var screen by screenState
    val nav = remember {
        Nav(
            drawer = { search -> screenState.value = if (search) "drawer-search" else "drawer" },
            panel = { screenState.value = "panel" },
            settings = { page -> screenState.value = "settings:$page" },
            assistant = { q -> screenState.value = "assistant:$q" }
        )
    }

    // Load apps (cached list shows at once; this refreshes it)
    LaunchedEffect(reloadKey) {
        if (reloadKey > 0) {
            delay(1500)
            IconCache.clear()
            s.iconVersion.intValue++
        }
        s.apps.value = withContext(Dispatchers.Default) { AppRepository.load(s) }
    }

    // Music + live checks while the launcher is in front
    val owner = LocalLifecycleOwner.current
    var resumes by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> {
                    s.music.start()
                    resumes++
                }
                Lifecycle.Event.ON_PAUSE -> s.music.stop()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            s.music.stop()
        }
    }

    // Phone mode side effects (do not disturb in Worship)
    val now = rememberNow()
    val mode = remember(now / 300_000L, resumes, s.phoneMode.value, s.autoMode.value) { s.effectiveMode() }
    var lastMode by remember { mutableStateOf("") }
    LaunchedEffect(mode) {
        if (s.modeChangesLook.value && Controls.dndAccess(s.context)) {
            if (mode == "worship") Controls.setDnd(s.context, true)
            else if (lastMode == "worship") Controls.setDnd(s.context, false)
        }
        lastMode = mode
    }

    // Short messages
    val msg = s.toast.value
    LaunchedEffect(msg) {
        if (msg != null) {
            Toast.makeText(s.context, msg, Toast.LENGTH_LONG).show()
            s.toast.value = null
        }
    }

    val mono = s.homeStyle.value == "mono"
    val pal = if (mono) LightPal else DarkPal
    val a = accentOf(s.accent.value)
    val scheme = if (mono) {
        lightColorScheme(primary = a.main, onPrimary = pal.cardSolid, surface = pal.cardSolid, onSurface = pal.text, background = pal.bg, onBackground = pal.text, surfaceContainerHigh = pal.cardSolid)
    } else {
        darkColorScheme(primary = a.main, onPrimary = pal.bg, surface = pal.cardSolid, onSurface = pal.text, background = pal.bg, onBackground = pal.text, surfaceContainerHigh = pal.cardSolid)
    }

    CompositionLocalProvider(LocalLauncher provides s, LocalNav provides nav, LocalPal provides pal) {
        MaterialTheme(colorScheme = scheme) {
            Box(Modifier.fillMaxSize()) {
                HomeRoot()
                AnimatedVisibility(
                    visible = screen.startsWith("drawer"),
                    enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut()
                ) { DrawerScreen(focusSearch = screen == "drawer-search") { screen = "home" } }
                AnimatedVisibility(
                    visible = screen == "panel",
                    enter = slideInVertically(initialOffsetY = { -it / 3 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it / 3 }) + fadeOut()
                ) { QuickPanel { screen = "home" } }
                AnimatedVisibility(
                    visible = screen.startsWith("assistant:"),
                    enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut()
                ) {
                    val q = remember(screen) { screen.substringAfter("assistant:", "") }
                    AssistantScreen(initial = q) { screen = "home" }
                }
                AnimatedVisibility(
                    visible = screen.startsWith("settings"),
                    enter = slideInHorizontally(initialOffsetX = { it / 3 }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it / 3 }) + fadeOut()
                ) {
                    SettingsScreen(
                        page = screen.substringAfter("settings:", "main"),
                        onPage = { screen = "settings:$it" },
                        onClose = { screen = "home" }
                    )
                }
            }

            ConfirmDialog()

            s.pendingLimit.value?.let { app ->
                val used = Usage.todayMinutes(s.context)[app.pkg] ?: 0
                AlertDialog(
                    onDismissRequest = { s.pendingLimit.value = null },
                    title = { Text(s.tr("Time limit reached", "Muda umefika")) },
                    text = {
                        Text(
                            s.tr(
                                "You've used ${app.label} for $used minutes today. Your limit is ${s.limits.value[app.pkg]} minutes.",
                                "Umetumia ${app.label} dakika $used leo. Kikomo chako ni dakika ${s.limits.value[app.pkg]}."
                            )
                        )
                    },
                    confirmButton = { TextButton(onClick = { s.pendingLimit.value = null }) { Text(s.tr("Not now", "Sasa hapana")) } },
                    dismissButton = {
                        TextButton(onClick = {
                            s.pendingLimit.value = null
                            AppRepository.open(s, app, skipLimit = true)
                        }) { Text(s.tr("Open anyway", "Fungua tu")) }
                    }
                )
            }
        }
    }

    // Back always returns to Home; on Home it does nothing.
    BackHandler(enabled = true) {
        screen = when {
            screen.startsWith("settings:") && screen != "settings:main" -> "settings:main"
            else -> "home"
        }
    }
}
