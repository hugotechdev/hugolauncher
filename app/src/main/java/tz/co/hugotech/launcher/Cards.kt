package tz.co.hugotech.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* ---------------------------------- Clock ---------------------------------- */

/** Current time; ticks once a minute (or every second when asked). */
@Composable
fun rememberNow(everySecond: Boolean = false): Long {
    val now by produceState(System.currentTimeMillis(), everySecond) {
        while (true) {
            value = System.currentTimeMillis()
            delay(if (everySecond) 1000L else 60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    return now
}

@Composable
fun timeFormat(): SimpleDateFormat {
    val ctx = LocalContext.current
    return remember { SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm", Locale.ENGLISH) }
}

fun dateText(s: LauncherState, now: Long, short: Boolean = false): String {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    if (s.lang.value != "sw") {
        return SimpleDateFormat(if (short) "EEE, d MMM" else "EEEE, d MMMM", Locale.ENGLISH).format(Date(now))
    }
    val days = arrayOf("Jumapili", "Jumatatu", "Jumanne", "Jumatano", "Alhamisi", "Ijumaa", "Jumamosi")
    val months = arrayOf("Januari", "Februari", "Machi", "Aprili", "Mei", "Juni", "Julai", "Agosti", "Septemba", "Oktoba", "Novemba", "Desemba")
    return days[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + c.get(Calendar.DAY_OF_MONTH) + " " + months[c.get(Calendar.MONTH)]
}

@Composable
fun ClockText(
    timeSize: TextUnit = 64.sp,
    center: Boolean = false,
    weight: FontWeight = FontWeight.Light,
    dateColor: Color = accent().soft
) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val now = rememberNow()
    val fmt = timeFormat()
    Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(fmt.format(Date(now)), color = pal.text, fontSize = timeSize, fontWeight = weight, letterSpacing = (-2).sp, lineHeight = timeSize * 1.05f)
        Text(dateText(s, now), color = dateColor, fontSize = 14.sp, fontWeight = FontWeight.Light, letterSpacing = 0.3.sp)
    }
}

/* ---------------------------------- Verse ---------------------------------- */

fun verseOfDay(): Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % Verses.list.size

@Composable
fun VerseCard(compact: Boolean = false, plain: Boolean = false) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val a = accent()
    var index by remember { mutableIntStateOf(verseOfDay()) }
    val v = Verses.list[index % Verses.list.size]
    val fav = v.ref in s.favVerses.value
    val body: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (!compact && !plain) {
                    Text(tr("VERSE OF THE DAY", "NENO LA LEO"), color = a.main, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    "“${v.text}”",
                    color = if (pal.dark) a.soft else pal.text,
                    fontSize = if (compact) 15.sp else 18.sp,
                    lineHeight = if (compact) 20.sp else 24.sp,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    textAlign = if (plain) TextAlign.Center else TextAlign.Start,
                    modifier = if (plain) Modifier.fillMaxWidth() else Modifier
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (plain) v.ref.uppercase() else v.ref,
                    color = pal.muted,
                    fontSize = 12.sp,
                    letterSpacing = if (plain) 1.2.sp else 0.sp,
                    textAlign = if (plain) TextAlign.Center else TextAlign.Start,
                    modifier = if (plain) Modifier.fillMaxWidth() else Modifier
                )
            }
            if (!compact && !plain) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { s.favVerses.toggle(v.ref) },
                    contentAlignment = Alignment.Center
                ) { Glyph(G.HEART, 17.dp, if (fav) a.main else pal.muted, fill = fav) }
            }
        }
    }
    if (plain) {
        Box(Modifier.fillMaxWidth().clickable { index = (index + 1) % Verses.list.size }) { body() }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .card()
                .clickable { index = (index + 1) % Verses.list.size }
                .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp)
        ) { body() }
    }
}

/* ---------------------------------- Tasks ---------------------------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TasksCard(compact: Boolean = false) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val a = accent()
    val tasks = s.tasks.value
    var adding by remember { mutableStateOf(false) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank() && s.tasks.value.size < 4) {
                s.tasks.value = s.tasks.value + Task(text.replaceFirstChar { it.uppercase() }, "", false)
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .card()
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Today’s tasks", "Kazi za leo") + if (tasks.isNotEmpty()) "  ${tasks.count { it.done }}/${tasks.size}" else "",
                color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
            )
            FocusCountdown()
            if (!compact) {
                RoundIcon(G.MIC, 34.dp, Color.Transparent, pal.muted, fill = false) {
                    try {
                        voice.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, s.tr("Say your task", "Sema kazi yako"))
                        )
                    } catch (e: ActivityNotFoundException) {
                        s.toast.value = s.tr("Voice typing is not available", "Kuandika kwa sauti hakupo")
                    }
                }
            }
            RoundIcon(G.PLUS, 34.dp, Color.Transparent, a.main, fill = false) {
                if (tasks.size < 4) adding = true else s.toast.value = s.tr("Up to 4 tasks a day", "Kazi 4 tu kwa siku")
            }
        }
        if (tasks.isEmpty()) {
            Text(
                tr("Nothing yet. Add up to four for today.", "Bado hakuna. Ongeza hadi nne za leo."),
                color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 8.dp)
            )
        }
        tasks.forEachIndexed { i, t ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(if (compact) 36.dp else 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .combinedClickable(
                        onClick = { s.tasks.value = tasks.mapIndexed { j, x -> if (j == i) x.copy(done = !x.done) else x } },
                        onLongClick = { s.tasks.value = tasks.filterIndexed { j, _ -> j != i } }
                    )
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (t.done) a.main else Color.Transparent)
                        .border(1.5.dp, if (t.done) a.main else pal.dim, CircleShape),
                    contentAlignment = Alignment.Center
                ) { if (t.done) Glyph(G.CHECK, 10.dp, pal.bg, stroke = 3.2f) }
                Spacer(Modifier.width(12.dp))
                Text(
                    t.title,
                    color = if (t.done) pal.dim else pal.text,
                    fontSize = 14.sp,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (t.time.isNotBlank()) Text(t.time, color = pal.muted, fontSize = 12.sp)
                if (!t.done && !compact) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { startFocus(s) },
                        contentAlignment = Alignment.Center
                    ) { Glyph(G.TIMER, 15.dp, pal.muted) }
                }
            }
        }
    }

    if (adding) AddTaskDialog { adding = false }
}

fun startFocus(s: LauncherState) {
    val now = System.currentTimeMillis()
    s.focusEnd.longValue = if (s.focusEnd.longValue > now) 0L else now + 25 * 60_000L
}

@Composable
fun AddTaskDialog(onClose: () -> Unit) {
    val s = LocalLauncher.current
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("New task", "Kazi mpya")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(tr("Task", "Kazi")) }, singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text(tr("Time (optional)", "Muda (si lazima)")) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank() && s.tasks.value.size < 4) s.tasks.value = s.tasks.value + Task(title.trim(), time.trim(), false)
                onClose()
            }) { Text(tr("Add", "Ongeza")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel", "Ghairi")) } }
    )
}

@Composable
fun FocusCountdown(big: Boolean = false) {
    val s = LocalLauncher.current
    val end = s.focusEnd.longValue
    if (end <= 0L) return
    val now = rememberNow(everySecond = true)
    if (end > now) {
        val left = (end - now) / 1000
        Text(
            "%d:%02d".format(left / 60, left % 60),
            color = accent().main,
            fontSize = if (big) 34.sp else 13.sp,
            fontWeight = if (big) FontWeight.ExtraLight else FontWeight.Medium,
            modifier = Modifier.padding(end = 6.dp)
        )
    }
}

/* ---------------------------------- Music ---------------------------------- */

@Composable
fun MusicCard(pill: Boolean = false) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val a = accent()
    val ctx = LocalContext.current
    val np = s.music.state.value
    val access = remember(np) { s.music.hasAccess() }
    val bg = if (pill) Color(0xFF262628) else pal.cardSolid
    val fg = if (pill) Color(0xFFEDECE8) else pal.text
    val shape = RoundedCornerShape(if (pill) 32.dp else 20.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, if (pill) Color.Transparent else pal.border, shape)
    ) {
        if (!pill && np?.art != null) {
            Image(np.art, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().alpha(0.18f))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = if (pill) 18.dp else 14.dp, vertical = if (pill) 10.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable {
                            if (np != null) AppRepository.openPackage(ctx, np.pkg)
                            else if (!AppRepository.openPackage(ctx, MusicController.AUDIOMACK)) s.toast.value = s.tr("Audiomack is not installed", "Audiomack haijawekwa")
                        }
                ) {
                    Text(
                        if (np != null && np.pkg != MusicController.AUDIOMACK) tr("NOW PLAYING", "INACHEZA") else "AUDIOMACK",
                        color = if (pill) Color(0xFF9A9893) else a.main, fontSize = 10.sp, letterSpacing = 1.4.sp
                    )
                    Text(
                        when {
                            !access -> s.tr("Turn on music controls", "Washa udhibiti wa muziki")
                            np == null -> s.tr("Nothing playing", "Hakuna kinachocheza")
                            else -> np.title + if (np.artist.isNotBlank()) " · " + np.artist else ""
                        },
                        color = fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (!access) {
                    Pill(s.tr("Turn on", "Washa")) { s.music.openAccessSettings() }
                } else {
                    RoundIcon(G.PREV, 38.dp, Color.Transparent, fg) { s.music.previous() }
                    RoundIcon(if (np?.playing == true) G.PAUSE else G.PLAY, 42.dp, if (pill) Color(0xFFEDECE8) else a.main, if (pill) Color(0xFF262628) else pal.bg) {
                        if (np == null) AppRepository.openPackage(ctx, MusicController.AUDIOMACK) else s.music.playPause()
                    }
                    RoundIcon(G.NEXT, 38.dp, Color.Transparent, fg) { s.music.next() }
                }
            }
            if (np != null && np.duration > 0 && !pill) MusicProgress(np, fg)
        }
    }
}

@Composable
private fun MusicProgress(np: NowPlaying, fg: Color) {
    val now = rememberNow(everySecond = np.playing)
    val pos = remember(now, np) { np.currentPosition() }
    val frac = (pos.toFloat() / np.duration).coerceIn(0f, 1f)
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(fg.copy(alpha = 0.15f))
    ) {
        Box(Modifier.fillMaxWidth(frac).height(3.dp).background(fg.copy(alpha = 0.7f)))
    }
}

/* ---------------------------------- Weather ---------------------------------- */

@Composable
fun WeatherCard() {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val a = accent()
    var w by remember { mutableStateOf(WeatherService.cached(s)) }
    val city = s.weatherCity.value
    LaunchedEffect(city) {
        val old = w
        if (old == null || System.currentTimeMillis() - old.at > 30 * 60_000L || !old.city.equals(city, true)) {
            withContext(Dispatchers.IO) { WeatherService.fetch(s) }?.let { w = it }
        }
    }
    val cur = w
    Column(Modifier.fillMaxWidth().card().padding(14.dp)) {
        if (cur == null) {
            Text(tr("Weather", "Hali ya hewa"), color = pal.text, fontSize = 14.sp)
            Text(tr("Loading… needs internet.", "Inapakia… inahitaji internet."), color = pal.muted, fontSize = 12.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(WeatherService.icon(cur.code), 30.dp, a.soft)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${cur.temp}°  " + WeatherService.label(cur.code, s), color = pal.text, fontSize = 18.sp, fontWeight = FontWeight.Light)
                    Text(cur.city, color = pal.muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                cur.days.forEach { d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.day, color = pal.muted, fontSize = 11.sp)
                        Spacer(Modifier.height(3.dp))
                        Glyph(WeatherService.icon(d.code), 16.dp, pal.muted)
                        Spacer(Modifier.height(3.dp))
                        Text("${d.max}°", color = pal.text, fontSize = 12.sp)
                        Text("${d.min}°", color = pal.dim, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/* ------------------------------ Calendar events ------------------------------ */

@Composable
fun EventsCard(onAsk: () -> Unit) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val has = CalendarData.hasAccess(ctx)
    val stamp = rememberNow()
    val events = remember(has, stamp / 600_000L) { if (has) CalendarData.today(ctx) else emptyList() }
    val fmt = timeFormat()
    Column(
        Modifier
            .fillMaxWidth()
            .card()
            .clickable { if (has) CalendarData.openCalendar(ctx) else onAsk() }
            .padding(14.dp)
    ) {
        Text(tr("Today’s calendar", "Kalenda ya leo"), color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        when {
            !has -> Text(tr("Tap to show your meetings here.", "Gusa ili mikutano yako ionekane hapa."), color = pal.muted, fontSize = 13.sp)
            events.isEmpty() -> Text(tr("No events today.", "Hakuna matukio leo."), color = pal.muted, fontSize = 13.sp)
            else -> events.take(5).forEach { e ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(
                        if (e.allDay) s.tr("All day", "Siku nzima") else fmt.format(Date(e.begin)),
                        color = accent().main, fontSize = 12.sp, modifier = Modifier.width(64.dp)
                    )
                    Text(e.title, color = pal.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/* -------------------------------- Screen time -------------------------------- */

@Composable
fun ScreenTimeCard(onOpen: () -> Unit) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val has = Usage.hasAccess(ctx)
    val stamp = rememberNow()
    val usage = remember(has, stamp / 300_000L) { if (has) Usage.todayMinutes(ctx) else emptyMap() }
    val total = usage.filterKeys { it != ctx.packageName }.values.sum()
    val social = usage.filterKeys { isSocial(it) }.values.sum()
    Column(Modifier.fillMaxWidth().card().clickable { onOpen() }.padding(14.dp)) {
        Text(tr("Screen time today", "Muda wa skrini leo"), color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        if (!has) {
            Text(tr("Tap to allow usage access.", "Gusa kuruhusu kuona matumizi."), color = pal.muted, fontSize = 13.sp)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${total / 60}h ${total % 60}m", color = pal.text, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.width(10.dp))
                Text(s.tr("social ${social}m", "mitandao ${social}m"), color = pal.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

/* --------------------------- Morning / evening note --------------------------- */

@Composable
fun DayNoteCard() {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val tasks = s.tasks.value
    val evening = hour >= 17
    var writing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().card().padding(14.dp)) {
        Text(
            if (evening) tr("Evening review", "Tathmini ya jioni") else tr("Good morning", "Habari za asubuhi"),
            color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (evening) s.tr("You finished ${tasks.count { it.done }} of ${tasks.size} tasks today.", "Umemaliza kazi ${tasks.count { it.done }} kati ya ${tasks.size} leo.")
            else s.tr("${tasks.count { !it.done }} tasks waiting. One verse for the road below.", "Kazi ${tasks.count { !it.done }} zinasubiri. Neno la leo liko chini."),
            color = pal.muted, fontSize = 13.sp
        )
        if (evening) {
            Spacer(Modifier.height(8.dp))
            TextLink(tr("Write a thank-you or prayer", "Andika shukrani au maombi")) { writing = true }
        }
    }
    if (writing) JournalDialog { writing = false }
}

@Composable
fun JournalDialog(onClose: () -> Unit) {
    val s = LocalLauncher.current
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Thanks and prayer", "Shukrani na maombi")) },
        text = { OutlinedTextField(text, { text = it }, minLines = 3, label = { Text(tr("Today I'm thankful for…", "Leo namshukuru Mungu kwa…")) }) },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) {
                    val d = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date())
                    s.journal.value = listOf(Journal(d, text.trim())) + s.journal.value.take(59)
                }
                onClose()
            }) { Text(tr("Save", "Hifadhi")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel", "Ghairi")) } }
    )
}

