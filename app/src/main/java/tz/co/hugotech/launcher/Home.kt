package tz.co.hugotech.launcher

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.util.Calendar
import kotlin.math.roundToInt

/** Where the home screen can send you. */
class Nav(
    val drawer: (search: Boolean) -> Unit,
    val panel: () -> Unit,
    val settings: (page: String) -> Unit,
    val assistant: (question: String) -> Unit
)

val LocalNav = staticCompositionLocalOf { Nav({}, {}, {}, {}) }

/* ------------------------------ Pages + gestures ------------------------------ */

/** Three pages: Today (left) · Home (middle) · Faith (right). Swipe up = apps, down = panel. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeRoot() {
    val s = LocalLauncher.current
    val nav = LocalNav.current
    val style = s.homeStyle.value
    val pal = if (style == "mono") LightPal else DarkPal
    val pager = rememberPagerState(initialPage = 1) { 3 }
    val navNow by rememberUpdatedState(nav)
    val openDrawer: () -> Unit = { navNow.drawer(false) }
    val openPanel: () -> Unit = { navNow.panel() }

    val swipe = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                acc += available.y
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (acc < -160f) openDrawer() else if (acc > 160f) openPanel()
                acc = 0f
                return Velocity.Zero
            }
        }
    }

    CompositionLocalProvider(LocalPal provides pal) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pal.bg)
                .nestedScroll(swipe)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -120f) openDrawer() else if (total > 120f) openPanel() }
                    ) { _, dy -> total += dy }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (s.doubleTapLock.value && !LockService.lock()) {
                            s.toast.value = s.tr(
                                "Turn on “HUGO Launcher screen lock” in Accessibility to lock by double-tap",
                                "Washa “HUGO Launcher screen lock” kwenye Accessibility ili kufunga kwa kugusa mara mbili"
                            )
                        }
                    })
                }
        ) {
            if (style != "mono") Wallpaper(s.effectiveWallpaper())
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
                when (page) {
                    0 -> TodayPage()
                    1 -> when (style) {
                        "matte" -> MatteHome()
                        "mono" -> MonoHome()
                        else -> ClassicHome()
                    }
                    else -> FaithPage()
                }
            }
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { i ->
                    val on = pager.currentPage == i
                    val dotColor = if (style == "mono" && pager.currentPage == 1) Color(0xFFB9B8B4) else pal.muted
                    Box(
                        Modifier
                            .size(width = if (on) 14.dp else 5.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(if (on) dotColor else dotColor.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

/** Shared page frame: safe areas and a comfortable reading width on tablets. */
@Composable
fun PageColumn(scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            content = content
        )
    }
}

@Composable
fun ModeChip() {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val nav = LocalNav.current
    val mode = s.effectiveMode()
    val label = when (mode) {
        "worship" -> tr("Worship", "Ibada")
        "rest" -> tr("Rest", "Pumziko")
        else -> tr("Work", "Kazi")
    }
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .border(1.dp, pal.border, RoundedCornerShape(15.dp))
            .clickable { nav.panel() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(label + if (s.autoMode.value) " · auto" else "", color = pal.muted, fontSize = 12.sp) }
}

/* ---------------------------------- Classic ---------------------------------- */

@Composable
fun ClassicHome() {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            PageColumn {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) { ClockText() }
                    Column(horizontalAlignment = Alignment.End) {
                        Row {
                            IconButtonSoft(G.SPARK) { nav.assistant("") }
                            Spacer(Modifier.width(8.dp))
                            IconButtonSoft(G.SETTINGS) { nav.settings("main") }
                        }
                        Spacer(Modifier.height(8.dp))
                        ModeChip()
                    }
                }
                Spacer(Modifier.height(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VerseCard()
                    TasksCard()
                    MusicCard()
                    HomeGrid()
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        BottomDock()
    }
}

/* ----------------------------------- Matte ----------------------------------- */

@Composable
fun MatteHome() {
    val nav = LocalNav.current
    val pal = LocalPal.current
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            PageColumn {
                Spacer(Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MonthCard()
                    VerseCard(compact = true)
                    TasksCard(compact = true)
                    HomeGrid()
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 10.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(pal.cardSolid)
                .border(1.dp, pal.border, RoundedCornerShape(18.dp))
                .clickable { nav.drawer(true) }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(G.SEARCH, 14.dp, pal.muted, stroke = 2.2f)
                Spacer(Modifier.width(8.dp))
                Text(tr("Search", "Tafuta"), color = pal.muted, fontSize = 13.sp)
            }
        }
        BottomDock()
    }
}

@Composable
fun MonthCard() {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val a = accent()
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val now = rememberNow()
    val hourKey = now / 3_600_000L
    val cal = remember(hourKey) { Calendar.getInstance() }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val busy = remember(hourKey) { CalendarData.busyDaysThisMonth(ctx) }
    val cells = remember(hourKey) {
        val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        List(first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY) { 0 } + (1..cal.getActualMaximum(Calendar.DAY_OF_MONTH)).toList()
    }
    val fmt = timeFormat()
    val weekday = dateText(s, now).substringBefore(",")
    val month = dateText(s, now).substringAfterLast(" ") + " " + cal.get(Calendar.YEAR)
    Row(Modifier.fillMaxWidth().card().clickable { CalendarData.openCalendar(ctx) }.padding(16.dp)) {
        Column(Modifier.width(106.dp)) {
            Text(weekday, color = pal.muted, fontSize = 15.sp)
            Text(today.toString(), color = a.soft, fontSize = 58.sp, fontWeight = FontWeight.Light, lineHeight = 60.sp)
            Text(fmt.format(java.util.Date(now)), color = pal.text, fontSize = 15.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(8.dp))
            Row {
                IconButtonSoft(G.SPARK) { nav.assistant("") }
                Spacer(Modifier.width(6.dp))
                IconButtonSoft(G.SETTINGS) { nav.settings("main") }
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(month, color = pal.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                ModeChip()
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                (if (s.lang.value == "sw") listOf("J", "J", "J", "J", "A", "I", "J") else listOf("S", "M", "T", "W", "T", "F", "S")).forEach {
                    Text(it, color = pal.dim, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    week.forEach { d ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (d == today) {
                                Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(a.soft), contentAlignment = Alignment.Center) {
                                    Text(d.toString(), color = pal.bg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            } else if (d > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(d.toString(), color = pal.text.copy(alpha = 0.8f), fontSize = 11.sp)
                                    if (d in busy) Box(Modifier.size(3.dp).clip(CircleShape).background(a.main))
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/* ----------------------------------- Mono ----------------------------------- */

@Composable
fun MonoHome() {
    val s = LocalLauncher.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val red = accentOf("red").main
    val top = Color(0xFF262628)
    Column(Modifier.fillMaxSize().background(top)) {
        CompositionLocalProvider(LocalPal provides DarkPal) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("HUGO", color = Color(0xFF8E8D89), fontSize = 12.sp, letterSpacing = 3.sp, modifier = Modifier.weight(1f))
                    ModeChip()
                    IconButtonSoft(G.SPARK) { nav.assistant("") }
                    IconButtonSoft(G.SETTINGS) { nav.settings("main") }
                }
                ClockText(timeSize = 84.sp, center = true, weight = FontWeight.ExtraLight, dateColor = Color(0xFFC9C8C3))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    val tasks = s.tasks.value
                    repeat(maxOf(tasks.size, 4)) { i ->
                        val done = tasks.getOrNull(i)?.done == true
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (done) red else Color(0xFF5E5D5A)))
                    }
                }
                Box(Modifier.padding(vertical = 14.dp).width(160.dp).height(1.dp).background(Color.White.copy(alpha = 0.14f)))
                Box(Modifier.padding(end = 12.dp)) { VerseCard(plain = true) }
            }
        }
        Box(Modifier.weight(1f)) { CompositionLocalProvider(LocalPal provides LightPal) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(LightPal.bg)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MusicCard(pill = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1.45f)) { TasksCard(compact = true) }
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(top)
                            .clickable { startFocus(s) }
                            .padding(14.dp)
                    ) {
                        Text(tr("FOCUS", "UMAKINI"), color = Color(0xFF9A9893), fontSize = 10.sp, letterSpacing = 1.4.sp)
                        Spacer(Modifier.height(6.dp))
                        if (s.focusEnd.longValue > System.currentTimeMillis()) {
                            CompositionLocalProvider(LocalPal provides DarkPal) { FocusCountdown(big = true) }
                        } else {
                            Text("25:00", color = Color(0xFFEDECE8), fontSize = 34.sp, fontWeight = FontWeight.ExtraLight)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (s.focusEnd.longValue > System.currentTimeMillis()) tr("Tap to stop", "Gusa kusimamisha") else tr("Tap to start", "Gusa kuanza"),
                            color = Color(0xFF9A9893), fontSize = 11.sp
                        )
                    }
                }
                QuickToggles(dark = false)
                DockRow()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavItem(G.GRID, tr("Apps", "Apps")) { nav.drawer(false) }
                    RoundIcon(G.SEARCH, 56.dp, top, Color(0xFFEDECE8), fill = false) { nav.drawer(true) }
                    NavItem(G.PANEL, tr("Panel", "Paneli")) { nav.panel() }
                }
                if (ctx.resources.configuration.screenHeightDp < 640) Spacer(Modifier.height(4.dp))
            }
        } }
    }
}

@Composable
private fun NavItem(path: String, label: String, onClick: () -> Unit) {
    val pal = LocalPal.current
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Glyph(path, 22.dp, pal.text)
        Text(label, color = pal.muted, fontSize = 11.sp)
    }
}

/* ------------------------------- Quick toggles ------------------------------- */

@Composable
fun QuickToggles(dark: Boolean) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val ctx = LocalContext.current
    var torch by remember { mutableStateOf(false) }
    var dnd by remember { mutableStateOf(Controls.dndOn(ctx)) }
    val on = if (dark) accent().main else Color(0xFF262628)
    val items = listOf(
        QuickToggle(G.WIFI, false) { Controls.wifiPanel(ctx) },
        QuickToggle(G.BT, false) { Controls.bluetoothSettings(ctx) },
        QuickToggle(G.TORCH, torch) { torch = Controls.torch(ctx) },
        QuickToggle(G.MOON, dnd) {
            Controls.setDnd(ctx, !dnd)
            dnd = Controls.dndOn(ctx)
        },
        QuickToggle(G.LOCK, false) {
            if (!LockService.lock()) {
                s.toast.value = s.tr("Turn on HUGO screen lock in Accessibility", "Washa HUGO screen lock kwenye Accessibility")
                LockService.openSettings(ctx)
            }
        }
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(pal.cardSolid)
            .border(1.dp, pal.border, RoundedCornerShape(32.dp)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { t ->
            val icon = t.icon
            val active = t.active
            val action = t.action
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (active) on else Color.Transparent)
                    .border(1.dp, if (active) on else pal.border, CircleShape)
                    .clickable { action() },
                contentAlignment = Alignment.Center
            ) { Glyph(icon, 18.dp, if (active) (if (dark) pal.bg else Color(0xFFEDECE8)) else pal.text) }
        }
    }
}

class QuickToggle(val icon: String, val active: Boolean, val action: () -> Unit)

/* ----------------------------------- Dock ----------------------------------- */

@Composable
fun BottomDock() {
    val pal = LocalPal.current
    Box(Modifier.navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
        Box(
            Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(pal.card)
                .border(1.dp, pal.border, RoundedCornerShape(26.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) { DockRow() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockRow() {
    val s = LocalLauncher.current
    val ctx = LocalContext.current
    val apps = s.apps.value
    var pick by remember { mutableIntStateOf(-1) }
    LaunchedEffect(apps.size) {
        if (s.dock.value.isEmpty() && apps.isNotEmpty()) {
            val pkgs = AppRepository.defaultDockPackages(ctx)
            val keys = pkgs.mapNotNull { p -> apps.firstOrNull { it.pkg == p }?.key }.toMutableList()
            for (app in apps) {
                if (keys.size >= 4) break
                if (app.key !in keys) keys.add(app.key)
            }
            s.dock.value = keys.take(4)
        }
    }
    val badges = NotificationState.counts.value
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (slot in 0 until 4) {
            val app = s.dock.value.getOrNull(slot)?.let { s.appByKey(it) }
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = { if (app != null) AppRepository.open(s, app) else pick = slot },
                        onLongClick = { pick = slot }
                    )
            ) { AppIcon(app, 54.dp, badge = app?.let { badges[it.pkg] } ?: 0) }
        }
    }
    if (pick >= 0) {
        AppPicker(
            title = tr("Choose a dock app", "Chagua app ya dock"),
            onPick = { app ->
                val list = MutableList(4) { i -> s.dock.value.getOrNull(i) ?: "" }
                list[pick] = app.key
                s.dock.value = list
                pick = -1
            },
            onClose = { pick = -1 }
        )
    }
}

@Composable
fun AppPicker(title: String, onPick: (AppInfo) -> Unit, onClose: () -> Unit) {
    val s = LocalLauncher.current
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, label = { Text(tr("Search", "Tafuta")) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    s.apps.value.filter { it.label.contains(q.trim(), true) }.forEach { app ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(app) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(app, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Close", "Funga")) } }
    )
}

/* ------------------------------- Home grid ------------------------------- */

/** Apps and folders you pinned to Home. Long-press for options; Arrange to drag them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeGrid() {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val cols = s.columns.value
    val items = s.home.value.filter { it.startsWith("folder:") || s.appByKey(it.removePrefix("app:")) != null }
    var arranging by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var editFolder by remember { mutableStateOf<Folder?>(null) }
    var dragIdx by remember { mutableIntStateOf(-1) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var cellW by remember { mutableFloatStateOf(1f) }
    var cellH by remember { mutableFloatStateOf(1f) }
    val badges = NotificationState.counts.value
    val gapPx = with(LocalDensity.current) { 14.dp.toPx() }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(tr("Home", "Nyumbani"), Modifier.weight(1f))
            if (arranging) {
                TextLink(tr("Done", "Imekamilika")) { arranging = false }
            } else {
                TextLink(tr("New folder", "Folda mpya")) { editFolder = Folder("f" + System.currentTimeMillis(), "", emptyList()) }
                if (items.size > 1) {
                    Spacer(Modifier.width(10.dp))
                    TextLink(tr("Arrange", "Panga")) { arranging = true }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text(
                tr("Pin apps here: in All apps, long-press an app → Add to Home.", "Weka apps hapa: kwenye Apps zote, bonyeza app kwa muda → Weka Nyumbani."),
                color = pal.muted, fontSize = 13.sp
            )
        }
        if (arranging) {
            Text(tr("Drag an icon to its new place.", "Buruta icon kwenda mahali pake mpya."), color = pal.muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }
        items.chunked(cols).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                row.forEachIndexed { c, item ->
                    val idx = r * cols + c
                    val dragging = idx == dragIdx
                    Box(
                        Modifier
                            .weight(1f)
                            .onSizeChanged {
                                cellW = it.width.toFloat()
                                cellH = it.height.toFloat() + gapPx
                            }
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                if (dragging) {
                                    translationX = drag.x
                                    translationY = drag.y
                                    scaleX = 1.1f
                                    scaleY = 1.1f
                                }
                            }
                            .then(
                                if (arranging) Modifier.pointerInput(idx, items) {
                                    detectDragGestures(
                                        onDragStart = { dragIdx = idx; drag = Offset.Zero },
                                        onDrag = { ch, amount -> ch.consume(); drag += amount },
                                        onDragEnd = {
                                            val dc = (drag.x / cellW).roundToInt()
                                            val dr = (drag.y / cellH).roundToInt()
                                            val target = (idx + dr * cols + dc).coerceIn(0, items.size - 1)
                                            if (target != idx) {
                                                val m = items.toMutableList()
                                                val moved = m.removeAt(idx)
                                                m.add(target, moved)
                                                s.home.value = m
                                            }
                                            dragIdx = -1
                                            drag = Offset.Zero
                                        },
                                        onDragCancel = { dragIdx = -1; drag = Offset.Zero }
                                    )
                                } else Modifier.combinedClickable(
                                    onClick = {
                                        if (item.startsWith("folder:")) openFolder = item.removePrefix("folder:")
                                        else s.appByKey(item.removePrefix("app:"))?.let { AppRepository.open(s, it) }
                                    },
                                    onLongClick = { menuFor = item }
                                )
                            ),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (item.startsWith("folder:")) {
                            val f = s.folders.value.firstOrNull { it.id == item.removePrefix("folder:") }
                            if (f != null) {
                                Labeled(f.name) {
                                    FolderTile(f, f.apps.sumOf { k -> s.appByKey(k)?.let { badges[it.pkg] } ?: 0 })
                                }
                            }
                        } else {
                            val app = s.appByKey(item.removePrefix("app:"))
                            if (app != null) Labeled(app.label) { AppIcon(app, 56.dp, badge = badges[app.pkg] ?: 0) }
                        }
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    menuFor?.let { item ->
        val isFolder = item.startsWith("folder:")
        val folder = if (isFolder) s.folders.value.firstOrNull { it.id == item.removePrefix("folder:") } else null
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(folder?.name ?: s.appByKey(item.removePrefix("app:"))?.label ?: "") },
            text = {
                Column {
                    if (folder != null) {
                        MenuRow(tr("Edit folder", "Hariri folda")) { menuFor = null; editFolder = folder }
                        MenuRow(tr("Delete folder", "Futa folda")) {
                            menuFor = null
                            s.folders.value = s.folders.value.filter { it.id != folder.id }
                            s.home.value = s.home.value - item
                        }
                    } else {
                        s.appByKey(item.removePrefix("app:"))?.let { app ->
                            MenuRow(tr("App info", "Taarifa za app")) { menuFor = null; AppRepository.openInfo(s.context, app.pkg) }
                        }
                    }
                    MenuRow(tr("Remove from Home", "Ondoa Nyumbani")) { menuFor = null; s.home.value = s.home.value - item }
                    MenuRow(tr("Arrange Home", "Panga Nyumbani")) { menuFor = null; arranging = true }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text(tr("Close", "Funga")) } }
        )
    }

    openFolder?.let { id ->
        val f = s.folders.value.firstOrNull { it.id == id }
        if (f == null) {
            openFolder = null
        } else {
            AlertDialog(
                onDismissRequest = { openFolder = null },
                title = { Text(f.name) },
                text = {
                    Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        f.apps.mapNotNull { s.appByKey(it) }.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { app ->
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { openFolder = null; AppRepository.open(s, app) }, contentAlignment = Alignment.TopCenter) {
                                        Labeled(app.label) { AppIcon(app, 48.dp, badge = badges[app.pkg] ?: 0) }
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { openFolder = null }) { Text(tr("Close", "Funga")) } },
                dismissButton = { TextButton(onClick = { openFolder = null; editFolder = f }) { Text(tr("Edit", "Hariri")) } }
            )
        }
    }

    editFolder?.let { f -> FolderEditor(f) { editFolder = null } }
}

@Composable
fun FolderEditor(start: Folder, onClose: () -> Unit) {
    val s = LocalLauncher.current
    var name by remember { mutableStateOf(start.name) }
    var chosen by remember { mutableStateOf(start.apps.toSet()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (start.name.isEmpty()) tr("New folder", "Folda mpya") else tr("Edit folder", "Hariri folda")) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(tr("Name", "Jina")) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    s.apps.value.forEach { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { chosen = if (app.key in chosen) chosen - app.key else chosen + app.key },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = app.key in chosen, onCheckedChange = { chosen = if (it) chosen + app.key else chosen - app.key })
                            Text(app.label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val f = Folder(start.id, name.ifBlank { s.tr("Folder", "Folda") }, s.apps.value.map { it.key }.filter { it in chosen })
                val exists = s.folders.value.any { it.id == f.id }
                s.folders.value = if (exists) s.folders.value.map { if (it.id == f.id) f else it } else s.folders.value + f
                if (!exists) s.home.value = s.home.value + ("folder:" + f.id)
                onClose()
            }) { Text(tr("Save", "Hifadhi")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel", "Ghairi")) } }
    )
}

@Composable
fun MenuRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp)
    )
}

@Composable
fun Labeled(label: String, icon: @Composable () -> Unit) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        if (s.showLabels.value) {
            Spacer(Modifier.height(6.dp))
            Text(label, color = pal.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 76.dp))
        }
    }
}

@Composable
fun FolderTile(f: Folder, badge: Int) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.size(56.dp)) {
        Column(
            Modifier
                .size(56.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(pal.tileTop, pal.tileBottom)))
                .border(0.5.dp, pal.text.copy(alpha = 0.1f), shape)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            f.apps.mapNotNull { s.appByKey(it) }.take(9).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { app ->
                        val ic = rememberAppIcons(app)
                        val bmp = ic?.full
                        if (bmp != null) {
                            androidx.compose.foundation.Image(bmp, null, Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)))
                        } else {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(pal.text.copy(alpha = 0.1f)))
                        }
                    }
                }
            }
        }
        if (badge > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(0.dp).size(18.dp).clip(CircleShape).background(Color(0xFFB4555A)),
                contentAlignment = Alignment.Center
            ) { Text(if (badge > 9) "9+" else badge.toString(), color = Color(0xFFF7F6F3), fontSize = 10.sp) }
        }
    }
}

/* --------------------------------- Today page --------------------------------- */

@Composable
fun TodayPage() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var calendarAsked by remember { mutableIntStateOf(0) }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calendarAsked++ }
    PageColumn {
        Spacer(Modifier.height(28.dp))
        Text(tr("Today", "Leo"), color = LocalPal.current.text, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DayNoteCard()
            WeatherCard()
            key(calendarAsked) { EventsCard { askCalendar.launch(Manifest.permission.READ_CALENDAR) } }
            ScreenTimeCard { if (Usage.hasAccess(ctx)) nav.settings("screen") else Usage.openSettings(ctx) }
            WidgetsSection()
        }
        Spacer(Modifier.height(24.dp))
    }
}
