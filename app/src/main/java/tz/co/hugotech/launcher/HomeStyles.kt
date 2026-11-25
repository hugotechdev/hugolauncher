package tz.co.hugotech.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/* ---------------------------------------------------------------------------
 * Home style 2: MATTE — month calendar, folders, search pill, dock with badges
 * ------------------------------------------------------------------------- */

private val FOLDERS = listOf("work" to "Work", "talk" to "Talk", "personal" to "Media", "other" to "Tools")

@Composable
fun MatteHome(a: HomeArgs) {
    val now = rememberNow()
    var openFolder by remember { mutableStateOf<String?>(null) }
    val favourites = remember(a.apps) {
        val used = a.apps.filter { a.prefs.launchCount(it.pkg) > 0 }.sortedByDescending { a.prefs.launchCount(it.pkg) }
        (used + a.apps.filter { it !in used }).take(8)
    }
    val folders = remember(a.apps) { a.apps.groupBy { Gems.typeOf(it.pkg) } }

    HomeFrame(a.onOpenDrawer, horizontal = 16.dp) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CalendarCard(now, a.accent, a.onOpenSettings)
            VerseCard(a.prefs, a.accent, compact = true)
            TasksSection(a.prefs, a.accent, now, compact = true)

            val tiles: List<Pair<AppInfo?, String?>> =
                favourites.map { Pair<AppInfo?, String?>(it, null) } +
                    FOLDERS.filter { folders[it.first].orEmpty().isNotEmpty() }.map { Pair<AppInfo?, String?>(null, it.first) }
            tiles.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (app, folderKey) ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (app != null) {
                                LabeledIcon(app.label, { a.onLaunch(app) }) {
                                    AppIcon(app, 60.dp, a.theme, a.accent, badge = a.badges[app.pkg] ?: 0, uniform = a.uniform)
                                }
                            } else if (folderKey != null) {
                                val inside = folders[folderKey].orEmpty()
                                val name = FOLDERS.firstOrNull { it.first == folderKey }?.second ?: "Folder"
                                LabeledIcon(name, { openFolder = folderKey }) {
                                    FolderTile(inside, inside.sumOf { a.badges[it.pkg] ?: 0 })
                                }
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 10.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xCC1E1D21))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                .clickable { a.onOpenDrawer() }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(G.SEARCH, 14.dp, Color(0xFFD6D3D1), stroke = 2.4f)
                Spacer(Modifier.width(8.dp))
                Text("Search", color = Color(0xFFD6D3D1), fontSize = 13.sp)
            }
        }
        DockBar(a, iconSize = 60.dp)
        Spacer(Modifier.height(10.dp))
    }

    val key = openFolder
    if (key != null) {
        val inside = folders[key].orEmpty()
        AlertDialog(
            onDismissRequest = { openFolder = null },
            title = { Text(FOLDERS.firstOrNull { it.first == key }?.second ?: "Folder") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    inside.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { app ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                    LabeledIcon(app.label, {
                                        openFolder = null
                                        a.onLaunch(app)
                                    }) { AppIcon(app, 52.dp, a.theme, a.accent, badge = a.badges[app.pkg] ?: 0, uniform = a.uniform) }
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { openFolder = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun LabeledIcon(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = HC.Text,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp)
        )
    }
}

@Composable
private fun FolderTile(apps: List<AppInfo>, badge: Int) {
    val shape = RoundedCornerShape(17.dp)
    Box(Modifier.size(60.dp)) {
        Box(
            Modifier
                .size(60.dp)
                .shadow(8.dp, shape)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xBF504E54), Color(0xBF28272B))))
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape)
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                apps.take(9).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { app ->
                            Image(app.icon, null, Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)))
                        }
                    }
                }
            }
        }
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(HC.Badge),
                contentAlignment = Alignment.Center
            ) { Text(if (badge > 9) "9+" else badge.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun CalendarCard(now: Long, accent: Accent, onSettings: () -> Unit) {
    val hourKey = now / 3_600_000L
    val cal = remember(hourKey) { Calendar.getInstance() }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val weekday = remember(hourKey) { SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time) }
    val month = remember(hourKey) { SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.time) }
    val cells = remember(hourKey) {
        val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val offset = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        List(offset) { 0 } + (1..cal.getActualMaximum(Calendar.DAY_OF_MONTH)).toList()
    }
    val timeFmt = rememberTimeFormat()

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xD11E1D21))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(26.dp))
            .padding(16.dp)
    ) {
        Column(Modifier.width(104.dp)) {
            Text(weekday, color = Color(0xFFD6D3D1), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(today.toString(), color = accent.soft, fontSize = 60.sp, fontWeight = FontWeight.SemiBold, lineHeight = 62.sp)
            Text(timeFmt.format(Date(now)), color = HC.Muted, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { onSettings() },
                contentAlignment = Alignment.Center
            ) { Glyph(G.SETTINGS, 16.dp, accent.soft) }
        }
        Column(Modifier.weight(1f)) {
            Text(month, color = Color(0xFFD6D3D1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Text(it, color = Color(0xFF8A847C), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().height(21.dp), verticalAlignment = Alignment.CenterVertically) {
                    week.forEach { d ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (d == today) {
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(accent.soft),
                                    contentAlignment = Alignment.Center
                                ) { Text(d.toString(), color = HC.Bg, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            } else if (d > 0) {
                                Text(d.toString(), color = Color(0xFFD6D3D1), fontSize = 11.sp)
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------------------
 * Home style 3: SPLIT — widgets on the left, 3D app list on the right
 * ------------------------------------------------------------------------- */

@Composable
fun SplitHome(a: HomeArgs) {
    val now = rememberNow()
    val timeFmt = rememberTimeFormat()
    val dateFmt = remember { SimpleDateFormat("EEE, d MMMM", Locale.ENGLISH) }
    val metal = Brush.verticalGradient(listOf(Color(0xFF2E2E31), Color(0xFF1E1E20)))

    HomeFrame(a.onOpenDrawer, horizontal = 12.dp) {
        Row(
            Modifier
                .weight(1f)
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF232325))
                        .clickable { a.onOpenSettings() }
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(timeFmt.format(Date(now)), color = HC.Text, fontSize = 50.sp, fontWeight = FontWeight.Light, lineHeight = 52.sp)
                    Text(dateFmt.format(Date(now)), color = a.accent.main, fontSize = 12.sp)
                }
                VerseCard(a.prefs, a.accent, compact = true)
                TasksSection(a.prefs, a.accent, now, compact = true)
                MusicCard(a.music, a.musicAccess, a.accent, now, vinyl = true)
                Spacer(Modifier.height(4.dp))
            }
            SplitAppList(a, Modifier.width(148.dp).fillMaxHeight())
        }
        Spacer(Modifier.height(10.dp))
        DockBar(a, iconSize = 52.dp, count = 5, background = metal, reflection = true)
        Spacer(Modifier.height(8.dp))
    }
}

/** Two columns of apps that bend in 3D as you scroll, like a drum. */
@Composable
private fun SplitAppList(a: HomeArgs, modifier: Modifier) {
    val rows = remember(a.apps) { a.apps.chunked(2) }
    val state = rememberLazyListState()
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF232325))
    ) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(rows.size) { i ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val info = state.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.index == i }
                            if (item != null) {
                                val vpH = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                                val center = item.offset + item.size / 2f - info.viewportStartOffset
                                val k = ((center - vpH / 2f) / (vpH / 2f)).coerceIn(-1f, 1f)
                                rotationX = -k * 58f
                                val s = 1f - abs(k) * 0.22f
                                scaleX = s
                                scaleY = s
                                alpha = 1f - abs(k) * 0.55f
                                cameraDistance = 9f * density
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rows[i].forEach { app ->
                        Box(Modifier.clickable { a.onLaunch(app) }) {
                            MetalIcon(app, 58.dp, a.accent, a.uniform, a.badges[app.pkg] ?: 0)
                        }
                    }
                    if (rows[i].size == 1) Spacer(Modifier.size(58.dp))
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF232325), Color.Transparent)))
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(36.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF232325))))
        )
    }
}

/** Brushed-metal tile with the app inside (Split style). */
@Composable
private fun MetalIcon(app: AppInfo, size: Dp, accent: Accent, uniform: Boolean, badge: Int) {
    val outer = RoundedCornerShape(size * 0.25f)
    Box(Modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .shadow(6.dp, outer)
                .clip(outer)
                .background(Brush.verticalGradient(listOf(Color(0xFF3B3B3F), Color(0xFF1C1C1F))))
                .border(1.dp, Color.White.copy(alpha = 0.08f), outer),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(size * 0.72f)
                    .clip(RoundedCornerShape(size * 0.18f))
                    .background(Brush.verticalGradient(listOf(Color(0xFF19191C), Color(0xFF2A2A2E)))),
                contentAlignment = Alignment.Center
            ) {
                IconContent(app, size * 1.1f, accent, uniform)
            }
        }
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1C1C1F))
                    .border(2.dp, Color(0xFF8A847C), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(if (badge > 9) "9+" else badge.toString(), color = HC.Text, fontSize = 10.sp) }
        }
    }
}
