package tz.co.hugotech.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Everything the three home styles share. */
class HomeArgs(
    val apps: List<AppInfo>,
    val prefs: Prefs,
    val music: MusicController,
    val musicAccess: Boolean,
    val accent: Accent,
    val theme: String,
    val uniform: Boolean,
    val badges: Map<String, Int>,
    val onOpenDrawer: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onLaunch: (AppInfo) -> Unit
)

fun Modifier.card(border: Color = HC.Border): Modifier =
    this
        .clip(RoundedCornerShape(22.dp))
        .background(HC.Card)
        .border(1.dp, border, RoundedCornerShape(22.dp))

@Composable
fun rememberNow(): Long {
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    return now
}

@Composable
fun rememberTimeFormat(): SimpleDateFormat {
    val context = LocalContext.current
    return remember { SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", Locale.ENGLISH) }
}

/** Screen frame with swipe up (all apps) and swipe down (notifications). */
@Composable
fun HomeFrame(
    onOpenDrawer: () -> Unit,
    horizontal: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val openDrawer by rememberUpdatedState(onOpenDrawer)
    val swipe = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                acc += available.y
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (acc < -150f) openDrawer() else if (acc > 150f) AppRepository.expandNotifications(context)
                acc = 0f
                return Velocity.Zero
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = horizontal)
            .nestedScroll(swipe)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -120f) openDrawer()
                        else if (total > 120f) AppRepository.expandNotifications(context)
                    }
                ) { _, dy -> total += dy }
            },
        content = content
    )
}

/** Home style 1: Classic (verse, tasks, Audiomack, dock). */
@Composable
fun ClassicHome(a: HomeArgs) {
    val now = rememberNow()
    val timeFmt = rememberTimeFormat()
    val dateFmt = remember { SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH) }
    HomeFrame(a.onOpenDrawer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Text(timeFmt.format(Date(now)), color = HC.Text, fontSize = 68.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
                Text(dateFmt.format(Date(now)), color = a.accent.soft, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            SettingsButton(a.accent, a.onOpenSettings)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VerseCard(a.prefs, a.accent)
            TasksSection(a.prefs, a.accent, now)
            MusicCard(a.music, a.musicAccess, a.accent, now)
            Spacer(Modifier.height(4.dp))
        }
        DockBar(a)
        SwipeHint(a.accent, a.onOpenDrawer)
    }
}

@Composable
fun SettingsButton(accent: Accent, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Glyph(G.SETTINGS, 18.dp, accent.soft) }
}

@Composable
fun SwipeHint(accent: Accent, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Glyph(G.UP, 14.dp, accent.soft)
        Spacer(Modifier.width(6.dp))
        Text("Swipe up for all apps", color = accent.soft, fontSize = 12.sp)
    }
}

/** Dock with 4 apps. Long-press a slot to change its app. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockBar(
    a: HomeArgs,
    iconSize: Dp = 58.dp,
    count: Int = 4,
    background: Brush? = null,
    reflection: Boolean = false
) {
    val context = LocalContext.current
    var dock by remember { mutableStateOf(a.prefs.dock()) }
    LaunchedEffect(a.apps) {
        if (dock.size < count && a.apps.isNotEmpty()) {
            val defaults = (dock + AppRepository.defaultDock(context)).distinct().toMutableList()
            for (app in a.apps) {
                if (defaults.size >= count) break
                if (app.pkg !in defaults) defaults.add(app.pkg)
            }
            dock = defaults.take(count)
            a.prefs.setDock(dock)
        }
    }
    var pickSlot by remember { mutableIntStateOf(-1) }
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (background != null) Modifier.background(background) else Modifier.background(HC.Card))
            .border(1.dp, a.accent.main.copy(alpha = 0.22f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        for (slot in 0 until count) {
            val app = dock.getOrNull(slot)?.let { pkg -> a.apps.firstOrNull { it.pkg == pkg } }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.combinedClickable(
                        onClick = { if (app != null) a.onLaunch(app) else pickSlot = slot },
                        onLongClick = { pickSlot = slot }
                    )
                ) {
                    AppIcon(app, iconSize, a.theme, a.accent, badge = app?.let { a.badges[it.pkg] } ?: 0, uniform = a.uniform)
                }
                if (reflection) {
                    Box(
                        Modifier
                            .size(iconSize, iconSize * 0.3f)
                            .clipToBounds()
                    ) {
                        Box(
                            Modifier.graphicsLayer {
                                scaleY = -1f
                                alpha = 0.16f
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                translationY = iconSize.toPx()
                            }
                        ) { AppIcon(app, iconSize, a.theme, a.accent, uniform = a.uniform) }
                    }
                }
            }
        }
    }

    if (pickSlot >= 0) {
        AlertDialog(
            onDismissRequest = { pickSlot = -1 },
            title = { Text("Choose app for dock") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    a.apps.forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val list = MutableList(count) { i -> dock.getOrNull(i) ?: "" }
                                    list[pickSlot] = app.pkg
                                    dock = list
                                    a.prefs.setDock(list)
                                    pickSlot = -1
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(app.icon, null, Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, color = HC.Text)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickSlot = -1 }) { Text("Close") } }
        )
    }
}

@Composable
fun VerseCard(prefs: Prefs, accent: Accent, compact: Boolean = false) {
    val start = remember { Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % Verses.list.size }
    var index by remember { mutableIntStateOf(start) }
    var favs by remember { mutableStateOf(prefs.favouriteVerses()) }
    val v = Verses.list[index % Verses.list.size]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .card(accent.main.copy(alpha = 0.35f))
            .clickable { index = (index + 1) % Verses.list.size }
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 10.dp else 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            if (!compact) {
                Text("VERSE OF THE DAY", color = accent.main, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(5.dp))
            }
            Text(
                "“${v.text}”",
                color = HC.VerseGold,
                fontSize = if (compact) 15.sp else 19.sp,
                lineHeight = if (compact) 19.sp else 24.sp,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(v.ref, color = HC.Muted, fontSize = if (compact) 11.sp else 12.sp)
        }
        if (!compact) {
            val isFav = v.ref in favs
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.main.copy(alpha = if (isFav) 0.35f else 0.12f))
                    .clickable { favs = prefs.toggleFavourite(v.ref) },
                contentAlignment = Alignment.Center
            ) { Glyph(G.HEART, 18.dp, accent.soft, fill = isFav) }
        }
    }
}

/** Today's tasks (max 4): add by typing or voice, focus timer, long-press to delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TasksSection(prefs: Prefs, accent: Accent, now: Long, compact: Boolean = false) {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf(prefs.loadTasks()) }
    fun save(list: List<Task>) {
        tasks = list
        prefs.saveTasks(list)
    }
    var showAdd by remember { mutableStateOf(false) }
    var focusEnd by remember { mutableLongStateOf(0L) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank() && tasks.size < 4) {
                save(tasks + Task(text.replaceFirstChar { it.uppercase() }, "", false))
            }
        }
    }
    val rowH = if (compact) 34.dp else 46.dp

    Column(
        Modifier
            .fillMaxWidth()
            .card()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (compact) "Tasks ${tasks.count { it.done }}/${tasks.size}" else "Today’s tasks",
                color = HC.Text,
                fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (focusEnd > now) {
                val left = (focusEnd - now) / 1000
                Text(
                    "%d:%02d".format(left / 60, left % 60),
                    color = accent.soft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            if (!compact) {
                SmallRoundButton(G.MIC, accent) {
                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your task")
                    try {
                        voice.launch(i)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "Voice typing is not available", Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            SmallRoundButton(G.PLUS, accent) {
                if (tasks.size < 4) showAdd = true
                else Toast.makeText(context, "Maximum 4 tasks", Toast.LENGTH_SHORT).show()
            }
        }
        if (tasks.isEmpty()) {
            Text(
                "No tasks yet. Add up to 4.",
                color = HC.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
            )
        }
        tasks.forEachIndexed { i, t ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowH)
                    .combinedClickable(
                        onClick = { save(tasks.mapIndexed { j, x -> if (j == i) x.copy(done = !x.done) else x }) },
                        onLongClick = { save(tasks.filterIndexed { j, _ -> j != i }) }
                    )
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(if (compact) 18.dp else 22.dp)
                        .clip(CircleShape)
                        .background(if (t.done) accent.main else Color.Transparent)
                        .border(2.dp, accent.main, CircleShape),
                    contentAlignment = Alignment.Center
                ) { if (t.done) Glyph(G.CHECK, 11.dp, HC.Bg, stroke = 3.5f) }
                Spacer(Modifier.width(10.dp))
                Text(
                    t.title,
                    color = if (t.done) HC.Dim else HC.Text,
                    fontSize = if (compact) 12.sp else 14.sp,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(t.time, color = HC.Muted, fontSize = if (compact) 10.sp else 12.sp, maxLines = 1)
                if (!t.done && !compact) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { focusEnd = if (focusEnd > now) 0L else System.currentTimeMillis() + 25 * 60 * 1000L },
                        contentAlignment = Alignment.Center
                    ) { Glyph(G.TIMER, 16.dp, accent.main) }
                }
            }
        }
        if (!compact && tasks.any { it.done }) {
            Text(
                "Clear done · long-press a task to delete",
                color = HC.Dim,
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                    .clickable { save(tasks.filter { !it.done }) }
            )
        }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var time by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task") }, singleLine = true)
                    OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time (e.g. 2:00 PM)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank() && tasks.size < 4) save(tasks + Task(title.trim(), time.trim(), false))
                    showAdd = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SmallRoundButton(path: String, accent: Accent, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(1.dp, accent.main.copy(alpha = 0.5f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Glyph(path, 14.dp, accent.soft, stroke = 2.2f) }
}

/** Now-playing card. vinyl = true shows a spinning record (Split style). */
@Composable
fun MusicCard(music: MusicController, access: Boolean, accent: Accent, now: Long, vinyl: Boolean = false) {
    val context = LocalContext.current
    val np = music.state.value
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (vinyl) Color(0xFF232325) else Color(0xFF2A2024))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp))
    ) {
        if (!vinyl && np?.art != null && Build.VERSION.SDK_INT >= 31) {
            Image(
                bitmap = np.art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(24.dp)
                    .alpha(0.55f)
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (vinyl) Color.Transparent else HC.Bg.copy(alpha = 0.45f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = if (vinyl) Alignment.CenterHorizontally else Alignment.Start
        ) {
            when {
                !access -> {
                    Text("MUSIC · AUDIOMACK", color = HC.VerseGold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Turn on music controls to play, pause and skip from here.", color = HC.Text, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Turn on", accent) { music.openAccessSettings() }
                }
                np == null -> {
                    Text("MUSIC · AUDIOMACK", color = HC.VerseGold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(4.dp))
                    if (vinyl) {
                        Vinyl(false, null, 110.dp)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("Nothing playing", color = HC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Open Audiomack", accent) {
                        if (!AppRepository.openPackage(context, MusicController.AUDIOMACK)) {
                            Toast.makeText(context, "Audiomack is not installed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                vinyl -> {
                    Text("AUDIOMACK", color = accent.main, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                    Text(np.title, color = HC.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(np.artist, color = HC.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.clickable { AppRepository.openPackage(context, np.pkg) }) {
                        Vinyl(np.playing, np.art, 116.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundIcon(G.PREV, 38.dp, Color(0xFF2E2E32), HC.Text) { music.previous() }
                        RoundIcon(if (np.playing) G.PAUSE else G.PLAY, 46.dp, Color(0xFF38383C), HC.VerseGold) { music.playPause() }
                        RoundIcon(G.NEXT, 38.dp, Color(0xFF2E2E32), HC.Text) { music.next() }
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accent.rim)
                                .clickable { AppRepository.openPackage(context, np.pkg) }
                        ) {
                            if (np.art != null) {
                                Image(np.art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "NOW PLAYING" + if (np.pkg == MusicController.AUDIOMACK) " · AUDIOMACK" else "",
                                color = HC.VerseGold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp
                            )
                            Text(np.title, color = HC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(np.artist, color = Color(0xFFD6D3D1), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        RoundIcon(G.PREV, 40.dp, Color.Transparent, HC.Text) { music.previous() }
                        RoundIcon(if (np.playing) G.PAUSE else G.PLAY, 44.dp, accent.main, HC.Bg) { music.playPause() }
                        RoundIcon(G.NEXT, 40.dp, Color.Transparent, HC.Text) { music.next() }
                    }
                    if (np.duration > 0) {
                        val pos = np.currentPosition()
                        val frac = (pos.toFloat() / np.duration).coerceIn(0f, 1f)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(fmt(pos), color = Color(0xFFD6D3D1), fontSize = 11.sp)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(frac)
                                        .height(4.dp)
                                        .background(HC.VerseGold)
                                )
                            }
                            Text(fmt(np.duration), color = Color(0xFFD6D3D1), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/** A record that spins while music plays. */
@Composable
fun Vinyl(playing: Boolean, art: androidx.compose.ui.graphics.ImageBitmap?, size: Dp) {
    val spin = rememberInfiniteTransition(label = "vinyl")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF141415)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(size * 0.88f)
                .graphicsLayer { rotationZ = if (playing) angle else 0f }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        0.0f to Color(0xFF26262A), 0.2f to Color(0xFF1B1B1D), 0.4f to Color(0xFF26262A),
                        0.6f to Color(0xFF1B1B1D), 0.8f to Color(0xFF26262A), 1.0f to Color(0xFF1B1B1D)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(size * 0.36f)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFF5E3A3), Color(0xFFB8902A), Color(0xFF6B4F12)))),
                contentAlignment = Alignment.Center
            ) {
                if (art != null) {
                    Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF141415)))
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun PillButton(text: String, accent: Accent, onClick: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(accent.main)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = HC.Bg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun RoundIcon(path: String, size: Dp, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Glyph(path, size * 0.42f, fg, fill = true) }
}
