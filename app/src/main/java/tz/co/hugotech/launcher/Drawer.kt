package tz.co.hugotech.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private sealed class Entry {
    data class Header(val title: String) : Entry()
    data class App(val app: AppInfo, val rare: Boolean) : Entry()
}

@Composable
fun DrawerScreen(
    apps: List<AppInfo>,
    prefs: Prefs,
    theme: String,
    accent: Accent,
    showLabels: Boolean,
    badges: Map<String, Int>,
    onLaunch: (AppInfo) -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onHide: (AppInfo) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val fourteenDays = 14L * 24 * 3600 * 1000
    val split = remember(apps) {
        val now = System.currentTimeMillis()
        apps.partition { a ->
            val last = prefs.lastLaunch(a.pkg)
            if (last == 0L) now - prefs.installedAt > fourteenDays else now - last > fourteenDays
        }
    }
    val rare = split.first
    val active = split.second
    val frequent = remember(apps) {
        val used = apps.filter { prefs.launchCount(it.pkg) > 0 }.sortedByDescending { prefs.launchCount(it.pkg) }
        (used + apps.filter { it !in used }).take(10)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(HC.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("All apps", color = HC.Text, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            HeaderButton(G.SETTINGS, accent, onSettings)
            Spacer(Modifier.width(8.dp))
            HeaderButton(G.DOWN, accent, onClose)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, accent.main.copy(alpha = 0.3f), RoundedCornerShape(25.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Glyph(G.SEARCH, 20.dp, accent.main)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search apps…", color = HC.Muted, fontSize = 15.sp)
                val results = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = HC.Text, fontSize = 15.sp),
                    cursorBrush = SolidColor(accent.main),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(onLaunch) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (query.isNotBlank()) {
            val results = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(results.size) { i ->
                    DrawerItem(results[i], theme, accent, showLabels, badges[results[i].pkg] ?: 0, false, onLaunch, onHide)
                }
            }
        } else {
            Ring3D(frequent, theme, accent, onLaunch)
            Box(Modifier.weight(1f)) {
                Grid3D(active, rare, theme, accent, showLabels, badges, onLaunch, onHide)
            }
        }
    }
}

@Composable
private fun HeaderButton(path: String, accent: Accent, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Glyph(path, 18.dp, accent.soft) }
}

/** Favourite apps on a spinning 3D ring. Drag left/right to turn it. */
@Composable
private fun Ring3D(apps: List<AppInfo>, theme: String, accent: Accent, onLaunch: (AppInfo) -> Unit) {
    if (apps.isEmpty()) return
    val n = apps.size
    val step = 360f / n
    val rot = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val radius = with(LocalDensity.current) { 118.dp.toPx() }
    val front = ((((-rot.value / step).roundToInt()) % n) + n) % n

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(n) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = (rot.value / step).roundToInt() * step
                                rot.animateTo(target, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
                            }
                        }
                    ) { change, drag ->
                        change.consume()
                        scope.launch { rot.snapTo(rot.value + drag * 0.35f) }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            apps.forEachIndexed { i, app ->
                val deg = i * step + rot.value
                val rad = Math.toRadians(deg.toDouble())
                val x = (sin(rad) * radius).toFloat()
                val z = cos(rad).toFloat()
                val depth = (z + 1f) / 2f
                Box(
                    Modifier
                        .zIndex(z)
                        .graphicsLayer {
                            translationX = x
                            translationY = z * 16.dp.toPx()
                            val s = 0.55f + 0.45f * depth
                            scaleX = s
                            scaleY = s
                            alpha = 0.25f + 0.75f * depth
                            rotationY = -sin(rad).toFloat() * 35f
                            cameraDistance = 12f * density
                        }
                        .clickable {
                            if (i == front) {
                                onLaunch(app)
                            } else {
                                scope.launch {
                                    val delta = (((-i * step - rot.value) % 360f) + 540f) % 360f - 180f
                                    rot.animateTo(rot.value + delta, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow))
                                }
                            }
                        }
                ) {
                    AppIcon(app, 66.dp, theme, accent)
                }
            }
        }
        Text(apps[front].label, color = HC.VerseGold, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text("Tap to open · drag to spin", color = HC.Muted, fontSize = 11.sp)
    }
}

/** All apps in a grid that bends in 3D as you scroll. */
@Composable
private fun Grid3D(
    active: List<AppInfo>,
    rare: List<AppInfo>,
    theme: String,
    accent: Accent,
    showLabels: Boolean,
    badges: Map<String, Int>,
    onLaunch: (AppInfo) -> Unit,
    onHide: (AppInfo) -> Unit
) {
    val entries = remember(active, rare) {
        buildList<Entry> {
            add(Entry.Header("All apps · ${active.size}"))
            active.forEach { add(Entry.App(it, false)) }
            if (rare.isNotEmpty()) {
                add(Entry.Header("Rarely used · not opened in 14 days"))
                rare.forEach { add(Entry.App(it, true)) }
            }
        }
    }
    val state = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = state,
        contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            count = entries.size,
            span = { i -> if (entries[i] is Entry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
        ) { i ->
            when (val e = entries[i]) {
                is Entry.Header -> Text(
                    e.title,
                    color = accent.soft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
                is Entry.App -> Box(
                    Modifier.graphicsLayer {
                        val info = state.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == i }
                        if (item != null) {
                            val vpH = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                            val center = item.offset.y + item.size.height / 2f - info.viewportStartOffset
                            val k = ((center - vpH / 2f) / (vpH / 2f)).coerceIn(-1f, 1f)
                            rotationX = -k * 48f
                            val s = 1f - abs(k) * 0.2f
                            scaleX = s
                            scaleY = s
                            alpha = 1f - abs(k) * 0.55f
                            cameraDistance = 10f * density
                        }
                    }
                ) {
                    DrawerItem(e.app, theme, accent, showLabels, badges[e.app.pkg] ?: 0, e.rare, onLaunch, onHide)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerItem(
    app: AppInfo,
    theme: String,
    accent: Accent,
    showLabels: Boolean,
    badge: Int,
    dimmed: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onHide: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { menu = true }),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIcon(app, 58.dp, theme, accent, badge, dimmed)
            if (showLabels) {
                Text(
                    app.label,
                    color = if (dimmed) HC.Dim else Color(0xFFE7E5E4),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("App info") }, onClick = {
                menu = false
                AppRepository.openInfo(context, app.pkg)
            })
            DropdownMenuItem(text = { Text("Hide app") }, onClick = {
                menu = false
                onHide(app)
            })
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = {
                menu = false
                AppRepository.uninstall(context, app.pkg)
            })
        }
    }
}
