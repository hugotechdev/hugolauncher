package tz.co.hugotech.launcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Soft fade at the top and bottom of a scrolling list, so items don't look cut off. */
fun Modifier.fadeEdges(top: Dp = 28.dp, bottom: Dp = 28.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height
        if (h <= 0f) return@drawWithContent
        val t = (top.toPx() / h).coerceIn(0f, 0.5f)
        val b = (bottom.toPx() / h).coerceIn(0f, 0.5f)
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, t to Color.Black, 1f - b to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn
        )
    }

private sealed class Cell {
    data class Header(val title: String) : Cell()
    data class App(val app: AppInfo, val rare: Boolean) : Cell()
}

/** All apps: search, ask AI, a clean grid, and rarely used apps at the bottom. */
@Composable
fun DrawerScreen(focusSearch: Boolean, onClose: () -> Unit) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val a = accent()
    val nav = LocalNav.current
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf<AppInfo?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            try {
                focus.requestFocus()
            } catch (e: Exception) {
                // not attached yet
            }
        }
    }
    val apps = s.visibleApps()
    val results = if (query.isBlank()) emptyList() else apps.filter { it.label.contains(query.trim(), true) }
    val cells = remember(apps, query) {
        if (query.isNotBlank()) {
            results.map { Cell.App(it, false) }
        } else {
            val now = System.currentTimeMillis()
            val fortnight = 14L * 86_400_000L
            val (rare, active) = apps.partition { app ->
                val last = s.lastLaunch(app.pkg)
                if (last == 0L) now - s.installedAt > fortnight else now - last > fortnight
            }
            buildList<Cell> {
                active.forEach { add(Cell.App(it, false)) }
                if (rare.isNotEmpty()) {
                    add(Cell.Header(s.tr("Rarely used · not opened in 14 days", "Hazitumiki sana · hazijafunguliwa siku 14")))
                    rare.forEach { add(Cell.App(it, true)) }
                }
            }
        }
    }
    val badges = NotificationState.counts.value

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("All apps", "Apps zote"), color = pal.text, fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            IconButtonSoft(G.SETTINGS) { nav.settings("main") }
            Spacer(Modifier.width(8.dp))
            IconButtonSoft(G.DOWN) { onClose() }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(pal.cardSolid)
                .border(1.dp, pal.border, RoundedCornerShape(25.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Glyph(G.SEARCH, 18.dp, pal.muted)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(tr("Search apps or ask…", "Tafuta app au uliza…"), color = pal.dim, fontSize = 15.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = pal.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(a.main),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Text),
                    keyboardActions = KeyboardActions(onGo = {
                        val first = results.firstOrNull()
                        if (first != null) AppRepository.open(s, first) else if (query.isNotBlank()) nav.assistant(query)
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                TextLink(tr("Ask AI", "Uliza AI")) { nav.assistant(query) }
            }
        }

        val grid = rememberLazyGridState()
        val scope = rememberCoroutineScope()
        // A–Z rail: first position of each letter among the apps
        val letters = remember(cells) {
            val m = LinkedHashMap<Char, Int>()
            cells.forEachIndexed { i, c ->
                if (c is Cell.App && !c.rare) {
                    val ch = c.app.label.firstOrNull()?.uppercaseChar()?.let { if (it.isLetter()) it else '#' } ?: '#'
                    if (ch !in m) m[ch] = i
                }
            }
            m
        }
        var scrub by remember { mutableStateOf<Char?>(null) }

        Row(Modifier.weight(1f).fillMaxWidth()) {
        Box(Modifier.weight(1f).fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(s.columns.value),
            state = grid,
            contentPadding = PaddingValues(top = 22.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize().fadeEdges()
        ) {
            items(
                count = cells.size,
                key = { i -> when (val c = cells[i]) { is Cell.Header -> "h:" + c.title; is Cell.App -> c.app.key } },
                contentType = { i -> if (cells[i] is Cell.Header) 0 else 1 },
                span = { i -> if (cells[i] is Cell.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
            ) { i ->
                when (val c = cells[i]) {
                    is Cell.Header -> Text(c.title, color = pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
                    is Cell.App -> DrawerItem(c.app, badges[c.app.pkg] ?: 0, c.rare) { menu = c.app }
                }
            }
            if (query.isNotBlank() && results.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        tr("No app found. Press “Ask AI” to ask the assistant.", "Hakuna app. Bonyeza “Uliza AI” kumuuliza msaidizi."),
                        color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
        scrub?.let { ch ->
            Box(
                Modifier.align(Alignment.Center).size(72.dp).clip(RoundedCornerShape(22.dp)).background(pal.cardSolid)
                    .border(1.dp, pal.border, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) { Text(ch.toString(), color = a.main, fontSize = 32.sp, fontWeight = FontWeight.Light) }
        }
        }
        if (query.isBlank() && letters.size > 4) {
            val keys = letters.keys.toList()
            Column(
                Modifier
                    .width(20.dp)
                    .fillMaxHeight()
                    .padding(vertical = 22.dp)
                    .pointerInput(keys) {
                        fun pick(y: Float) {
                            val idx = (y / size.height * keys.size).toInt().coerceIn(0, keys.size - 1)
                            val ch = keys[idx]
                            if (scrub != ch) {
                                scrub = ch
                                scope.launch { grid.scrollToItem(letters[ch] ?: 0) }
                            }
                        }
                        detectVerticalDragGestures(
                            onDragStart = { pick(it.y) },
                            onDragEnd = { scrub = null },
                            onDragCancel = { scrub = null }
                        ) { change, _ -> pick(change.position.y) }
                    }
                    .pointerInput(keys) {
                        detectTapGestures { pos ->
                            val ch = keys[(pos.y / size.height * keys.size).toInt().coerceIn(0, keys.size - 1)]
                            scope.launch { grid.animateScrollToItem(letters[ch] ?: 0) }
                        }
                    },
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                keys.forEach { ch ->
                    Text(ch.toString(), color = if (ch == scrub) a.main else pal.dim, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        }
    }

    menu?.let { app -> AppMenu(app) { menu = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerItem(app: AppInfo, badge: Int, dimmed: Boolean, onMenu: () -> Unit) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    // Each icon rises in softly as it scrolls into view.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val v = appear.value
                alpha = v
                scaleX = 0.86f + 0.14f * v
                scaleY = 0.86f + 0.14f * v
                translationY = (1f - v) * 18.dp.toPx()
            }
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = { AppRepository.open(s, app) }, onLongClick = onMenu)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(app, 54.dp, badge, dimmed)
        if (s.showLabels.value) {
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                color = if (dimmed) pal.dim else pal.text,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 80.dp)
            )
        }
    }
}

/** Everything you can do with one app. */
@Composable
fun AppMenu(app: AppInfo, startPage: String = "main", onClose: () -> Unit) {
    val s = LocalLauncher.current
    var page by remember { mutableStateOf(startPage) }
    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && !IconCache.saveCustom(s, app, uri)) s.toast.value = s.tr("Couldn't use that picture", "Picha hiyo haikufaa")
        onClose()
    }
    val onHome = s.home.value.contains("app:" + app.key)
    val locked = app.key in s.locked.value
    val workHidden = app.pkg in s.workHidden.value

    when (page) {
        "rename" -> {
            var name by remember { mutableStateOf(app.label) }
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(tr("Rename", "Badilisha jina")) },
                text = { OutlinedTextField(name, { name = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        s.renamed.put(app.key, name.trim().ifBlank { null }?.takeIf { it != app.realLabel })
                        s.apps.value = s.apps.value.map { if (it.key == app.key) it.copy(label = name.trim().ifBlank { app.realLabel }) else it }
                        onClose()
                    }) { Text(tr("Save", "Hifadhi")) }
                },
                dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel", "Ghairi")) } }
            )
        }
        "limit" -> {
            var minutes by remember { mutableStateOf(s.limits.value[app.pkg] ?: "") }
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(tr("Daily time limit", "Kikomo cha muda kwa siku")) },
                text = {
                    Column {
                        Text(
                            tr("When you pass it, HUGO asks before opening the app. Leave empty for no limit.", "Ukipita kikomo, HUGO itauliza kabla ya kufungua. Acha tupu kama hutaki kikomo."),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            minutes, { v -> minutes = v.filter { it.isDigit() }.take(3) },
                            singleLine = true, label = { Text(tr("Minutes", "Dakika")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        if (!Usage.hasAccess(s.context)) {
                            Spacer(Modifier.height(8.dp))
                            TextLink(tr("Allow usage access first", "Ruhusu kuona matumizi kwanza")) { Usage.openSettings(s.context) }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        s.limits.put(app.pkg, minutes.toIntOrNull()?.takeIf { it > 0 }?.toString())
                        onClose()
                    }) { Text(tr("Save", "Hifadhi")) }
                },
                dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel", "Ghairi")) } }
            )
        }
        "folder" -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(tr("Add to folder", "Weka kwenye folda")) },
                text = {
                    Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                        if (s.folders.value.isEmpty()) Text(tr("No folders yet. Create one on Home.", "Bado hakuna folda. Tengeneza moja Nyumbani."), fontSize = 13.sp)
                        s.folders.value.forEach { f ->
                            MenuRow(f.name) {
                                s.folders.value = s.folders.value.map { if (it.id == f.id && app.key !in it.apps) it.copy(apps = it.apps + app.key) else it }
                                onClose()
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onClose) { Text(tr("Close", "Funga")) } }
            )
        }
        else -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(app.label) },
                text = {
                    Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                        MenuRow(if (onHome) tr("Remove from Home", "Ondoa Nyumbani") else tr("Add to Home", "Weka Nyumbani")) {
                            s.home.value = if (onHome) s.home.value - ("app:" + app.key) else s.home.value + ("app:" + app.key)
                            onClose()
                        }
                        MenuRow(tr("Add to folder", "Weka kwenye folda")) { page = "folder" }
                        MenuRow(tr("Rename", "Badilisha jina")) { page = "rename" }
                        MenuRow(tr("Change icon (from gallery)", "Badilisha icon (kutoka picha)")) {
                            pickIcon.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        if (s.customIcons.value.containsKey(app.key)) {
                            MenuRow(tr("Use original icon", "Rudisha icon ya asili")) { IconCache.resetCustom(s, app); onClose() }
                        }
                        MenuRow(if (locked) tr("Remove fingerprint lock", "Ondoa kufuli ya kidole") else tr("Lock with fingerprint", "Funga kwa alama ya kidole")) {
                            if (locked) AppRepository.unlock(s, app.label) { s.locked.toggle(app.key) } else s.locked.toggle(app.key)
                            onClose()
                        }
                        MenuRow(tr("Daily time limit", "Kikomo cha muda")) { page = "limit" }
                        MenuRow(if (workHidden) tr("Show in Work mode", "Onyesha wakati wa Kazi") else tr("Hide in Work mode", "Ficha wakati wa Kazi")) {
                            s.workHidden.toggle(app.pkg)
                            onClose()
                        }
                        MenuRow(tr("Hide app", "Ficha app")) { s.hidden.toggle(app.pkg); onClose() }
                        MenuRow(tr("App info", "Taarifa za app")) { AppRepository.openInfo(s.context, app.pkg); onClose() }
                        MenuRow(tr("Uninstall", "Ondoa app")) { AppRepository.uninstall(s.context, app.pkg); onClose() }
                    }
                },
                confirmButton = { TextButton(onClick = onClose) { Text(tr("Close", "Funga")) } }
            )
        }
    }
}
