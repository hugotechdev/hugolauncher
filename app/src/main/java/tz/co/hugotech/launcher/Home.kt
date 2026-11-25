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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun Modifier.card(border: Color = HC.Border): Modifier =
    this
        .clip(RoundedCornerShape(22.dp))
        .background(HC.Card)
        .border(1.dp, border, RoundedCornerShape(22.dp))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    apps: List<AppInfo>,
    prefs: Prefs,
    music: MusicController,
    musicAccess: Boolean,
    accent: Accent,
    theme: String,
    badges: Map<String, Int>,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val timeFmt = remember { SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", Locale.ENGLISH) }
    val dateFmt = remember { SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH) }

    var tasks by remember { mutableStateOf(prefs.loadTasks()) }
    fun saveTasks(list: List<Task>) {
        tasks = list
        prefs.saveTasks(list)
    }
    var showAdd by remember { mutableStateOf(false) }
    var focusEnd by remember { mutableLongStateOf(0L) }

    var dock by remember { mutableStateOf(prefs.dock()) }
    LaunchedEffect(apps) {
        if (dock.isEmpty() && apps.isNotEmpty()) {
            val defaults = AppRepository.defaultDock(context).toMutableList()
            for (a in apps) {
                if (defaults.size >= 4) break
                if (a.pkg !in defaults) defaults.add(a.pkg)
            }
            dock = defaults.take(4)
            prefs.setDock(dock)
        }
    }
    var pickSlot by remember { mutableIntStateOf(-1) }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank() && tasks.size < 4) {
                saveTasks(tasks + Task(text.replaceFirstChar { it.uppercase() }, "", false))
            }
        }
    }

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
            .padding(horizontal = 18.dp)
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
            }
    ) {
        // Clock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    timeFmt.format(Date(now)),
                    color = HC.Text,
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-2).sp
                )
                Text(dateFmt.format(Date(now)), color = accent.soft, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) { Glyph(G.SETTINGS, 18.dp, accent.soft) }
                Spacer(Modifier.height(6.dp))
                Text("${tasks.count { it.done }}/${tasks.size} done", color = HC.Muted, fontSize = 12.sp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VerseCard(prefs, accent)
            TasksCard(
                tasks = tasks,
                accent = accent,
                now = now,
                focusEnd = focusEnd,
                onToggle = { i -> saveTasks(tasks.mapIndexed { j, t -> if (j == i) t.copy(done = !t.done) else t }) },
                onDelete = { i -> saveTasks(tasks.filterIndexed { j, _ -> j != i }) },
                onAdd = { showAdd = true },
                onVoice = {
                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your task")
                    try {
                        voice.launch(i)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "Voice typing is not available", Toast.LENGTH_SHORT).show()
                    }
                },
                onFocus = { focusEnd = if (focusEnd > now) 0L else System.currentTimeMillis() + 25 * 60 * 1000L },
                onClearDone = { saveTasks(tasks.filter { !it.done }) }
            )
            MusicCard(music, musicAccess, accent, now)
            Spacer(Modifier.height(4.dp))
        }

        // Dock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(HC.Card)
                .border(1.dp, accent.main.copy(alpha = 0.22f), RoundedCornerShape(28.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (slot in 0 until 4) {
                val app = dock.getOrNull(slot)?.let { pkg -> apps.firstOrNull { it.pkg == pkg } }
                Box(
                    Modifier.combinedClickable(
                        onClick = { if (app != null) onLaunch(app) else pickSlot = slot },
                        onLongClick = { pickSlot = slot }
                    )
                ) {
                    AppIcon(app, 58.dp, theme, accent, badge = app?.let { badges[it.pkg] } ?: 0)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp)
                .clickable { onOpenDrawer() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Glyph(G.UP, 14.dp, accent.soft)
            Spacer(Modifier.width(6.dp))
            Text("Swipe up for all apps", color = accent.soft, fontSize = 12.sp)
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
                    if (title.isNotBlank() && tasks.size < 4) saveTasks(tasks + Task(title.trim(), time.trim(), false))
                    showAdd = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
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
                    apps.forEach { a ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val list = MutableList(4) { i -> dock.getOrNull(i) ?: "" }
                                    list[pickSlot] = a.pkg
                                    dock = list
                                    prefs.setDock(list)
                                    pickSlot = -1
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(a.icon, null, Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, color = HC.Text)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickSlot = -1 }) { Text("Close") } }
        )
    }
}

@Composable
private fun VerseCard(prefs: Prefs, accent: Accent) {
    val start = remember { Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % Verses.list.size }
    var index by remember { mutableIntStateOf(start) }
    var favs by remember { mutableStateOf(prefs.favouriteVerses()) }
    val v = Verses.list[index % Verses.list.size]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .card(accent.main.copy(alpha = 0.35f))
            .clickable { index = (index + 1) % Verses.list.size }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text("VERSE OF THE DAY", color = accent.main, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                "“${v.text}”",
                color = HC.VerseGold,
                fontSize = 19.sp,
                lineHeight = 24.sp,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(5.dp))
            Text(v.ref, color = HC.Muted, fontSize = 12.sp)
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TasksCard(
    tasks: List<Task>,
    accent: Accent,
    now: Long,
    focusEnd: Long,
    onToggle: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
    onVoice: () -> Unit,
    onFocus: () -> Unit,
    onClearDone: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .card()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Today’s tasks", color = HC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (focusEnd > now) {
                val left = (focusEnd - now) / 1000
                Text(
                    "Focus %d:%02d".format(left / 60, left % 60),
                    color = accent.soft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            SmallRoundButton(G.MIC, accent, onVoice)
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, accent.main.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable(enabled = tasks.size < 4) { onAdd() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text(if (tasks.size < 4) "+ Add" else "Max 4", color = accent.soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        if (tasks.isEmpty()) {
            Text(
                "No tasks yet. Add up to 4 for today.",
                color = HC.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 10.dp)
            )
        }
        tasks.forEachIndexed { i, t ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .combinedClickable(onClick = { onToggle(i) }, onLongClick = { onDelete(i) })
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (t.done) accent.main else Color.Transparent)
                        .border(2.dp, accent.main, CircleShape),
                    contentAlignment = Alignment.Center
                ) { if (t.done) Glyph(G.CHECK, 12.dp, HC.Bg, stroke = 3.5f) }
                Spacer(Modifier.width(12.dp))
                Text(
                    t.title,
                    color = if (t.done) HC.Dim else HC.Text,
                    fontSize = 14.sp,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(t.time, color = HC.Muted, fontSize = 12.sp)
                if (!t.done) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onFocus() },
                        contentAlignment = Alignment.Center
                    ) { Glyph(G.TIMER, 16.dp, accent.main) }
                }
            }
        }
        if (tasks.any { it.done }) {
            Text(
                "Clear done · long-press a task to delete",
                color = HC.Dim,
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                    .clickable { onClearDone() }
            )
        }
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

@Composable
private fun MusicCard(music: MusicController, access: Boolean, accent: Accent, now: Long) {
    val context = LocalContext.current
    val np = music.state.value
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF2A2024))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp))
    ) {
        if (np?.art != null && Build.VERSION.SDK_INT >= 31) {
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
                .background(HC.Bg.copy(alpha = 0.45f))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            when {
                !access -> {
                    Text("MUSIC · AUDIOMACK", color = HC.VerseGold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Turn on music controls to play, pause and skip Audiomack from here.", color = HC.Text, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Turn on", accent) { music.openAccessSettings() }
                }
                np == null -> {
                    Text("MUSIC · AUDIOMACK", color = HC.VerseGold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Nothing playing", color = HC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Open Audiomack", accent) {
                        if (!AppRepository.openPackage(context, MusicController.AUDIOMACK)) {
                            Toast.makeText(context, "Audiomack is not installed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                else -> if (np != null) {
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
fun RoundIcon(path: String, size: androidx.compose.ui.unit.Dp, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Glyph(path, size * 0.42f, fg, fill = true) }
}
