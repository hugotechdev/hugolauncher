package tz.co.hugotech.launcher

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(page: String, onPage: (String) -> Unit, onClose: () -> Unit) {
    val pal = LocalPal.current
    Box(Modifier.fillMaxSize().background(pal.bg), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButtonSoft(G.BACK) { if (page == "main") onClose() else onPage("main") }
                Spacer(Modifier.width(12.dp))
                Text(
                    when (page) {
                        "screen" -> tr("Screen time", "Muda wa skrini")
                        "ai" -> tr("Assistant", "Msaidizi")
                        else -> tr("Settings", "Mipangilio")
                    },
                    color = pal.text, fontSize = 24.sp, fontWeight = FontWeight.Light
                )
            }
            when (page) {
                "screen" -> ScreenTimePage()
                "ai" -> AssistantSettings()
                else -> MainSettings(onPage)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MainSettings(onPage: (String) -> Unit) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var choosingWork by remember { mutableStateOf(false) }
    var choosingPack by remember { mutableStateOf(false) }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && !saveCustomWallpaper(s, uri)) s.toast.value = s.tr("Couldn't use that picture", "Picha hiyo haikufaa")
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(s).toByteArray()) }
                s.toast.value = s.tr("Backup saved", "Backup imehifadhiwa")
            } catch (e: Exception) {
                s.toast.value = s.tr("Couldn't save the backup", "Imeshindwa kuhifadhi backup")
            }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = try {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: Exception) {
                null
            }
            if (text != null && Backup.import(s, text)) s.activity.recreate()
            else s.toast.value = s.tr("That file isn't a HUGO backup", "Faili hilo si backup ya HUGO")
        }
    }
    val r = refresh

    // Look
    Group(tr("Look", "Muonekano")) {
        Label(tr("Home style", "Mtindo wa Home"))
        Segmented(
            listOf("classic" to "Classic", "matte" to "Matte", "mono" to "Mono"),
            s.homeStyle.value
        ) {
            s.homeStyle.value = it
            if (it == "mono" && s.accent.value == "gold") s.accent.value = "red"
        }
        Text(
            when (s.homeStyle.value) {
                "matte" -> tr("Month calendar, folders and a search pill.", "Kalenda ya mwezi, folda na kitufe cha kutafuta.")
                "mono" -> tr("Black and white: thin clock above, controls below.", "Nyeusi na nyeupe: saa nyembamba juu, vitufe chini.")
                else -> tr("Verse, tasks, music and your apps, one under the other.", "Neno, kazi, muziki na apps zako, moja chini ya nyingine.")
            },
            color = pal.dim, fontSize = 12.sp
        )
        Label(tr("Icons", "Icons"))
        Segmented(
            listOf("original" to s.tr("Original", "Asili"), "matte" to "Matte", "gems" to s.tr("Gems", "Vito"), "mono" to "Mono"),
            s.iconTheme.value
        ) { s.iconTheme.value = it; IconCache.clear(); s.iconVersion.intValue++ }
        Label(tr("Colour", "Rangi"))
        Segmented(Accents.map { it.id to it.label }, s.accent.value) { s.accent.value = it }
        Label(tr("Wallpaper", "Wallpaper"))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WALLPAPERS.forEach { (id, names) ->
                WallThumb(id, s.tr(names.first, names.second), s.wallpaper.value == id) { s.wallpaper.value = id }
            }
            WallThumb("custom", tr("Your photo", "Picha yako"), s.wallpaper.value == "custom") {
                pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        Label(tr("Columns", "Safu"))
        Segmented(listOf("4" to "4", "5" to "5"), s.columns.value.toString()) { s.columns.value = it.toInt() }
        SettingToggle(tr("Show app names", "Onyesha majina ya apps"), null, s.showLabels.value) { s.showLabels.value = it }
        SettingToggle(tr("One-colour icons", "Icons za rangi moja"), tr("Paint every icon in the chosen colour.", "Paka kila icon rangi uliyochagua."), s.uniformIcons.value) { s.uniformIcons.value = it; IconCache.clear(); s.iconVersion.intValue++ }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Icon pack", "Icon pack"), color = pal.text, fontSize = 15.sp)
                Text(
                    if (s.iconPack.value.isEmpty()) tr("None", "Hakuna") else s.iconPack.value,
                    color = pal.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            TextLink(tr("Choose", "Chagua")) { choosingPack = true }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Language", "Lugha"), color = pal.text, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(top = 10.dp))
            Box(Modifier.width(180.dp)) {
                Segmented(listOf("en" to "English", "sw" to "Kiswahili"), s.lang.value) { s.lang.value = it }
            }
        }
    }

    // Phone mode
    Group(tr("Phone mode", "Hali ya simu")) {
        SettingToggle(
            tr("Change mode by time", "Badilisha hali kwa saa"),
            tr("Work on weekdays 8–18, Worship Sunday morning, Rest otherwise.", "Kazi siku za kazi 8–18, Ibada Jumapili asubuhi, Pumziko muda mwingine."),
            s.autoMode.value
        ) { s.autoMode.value = it }
        SettingToggle(
            tr("Worship changes the look", "Ibada inabadilisha muonekano"),
            tr("Cross of light wallpaper and do not disturb.", "Wallpaper ya msalaba na usisumbue."),
            s.modeChangesLook.value
        ) { s.modeChangesLook.value = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Hidden during Work", "Zimefichwa wakati wa Kazi"), color = pal.text, fontSize = 15.sp)
                Text(s.tr("${s.workHidden.value.size} apps", "apps ${s.workHidden.value.size}"), color = pal.dim, fontSize = 12.sp)
            }
            TextLink(tr("Choose", "Chagua")) { choosingWork = true }
        }
    }

    // Gestures
    Group(tr("Gestures", "Ishara")) {
        Text(
            tr("Swipe up: all apps · Swipe down: panel · Swipe sideways: Today and Faith pages.", "Swipe juu: apps · Swipe chini: paneli · Swipe pembeni: kurasa za Leo na Imani."),
            color = pal.dim, fontSize = 12.sp
        )
        SettingToggle(tr("Double-tap to lock", "Gusa mara mbili kufunga"), if (LockService.enabled()) tr("Ready", "Iko tayari") else tr("Needs Accessibility permission", "Inahitaji ruhusa ya Accessibility"), s.doubleTapLock.value) {
            s.doubleTapLock.value = it
            if (it && !LockService.enabled()) LockService.openSettings(ctx)
        }
    }

    // Permissions (re-read when the calendar answer comes back)
    key(r) { Group(tr("Setup", "Maandalizi")) {
        Permission(tr("Default home app", "Home app ya kwanza"), null) { AppRepository.safeStart(ctx, Intent(Settings.ACTION_HOME_SETTINGS)) }
        Permission(tr("Music controls and badges", "Udhibiti wa muziki na vitone"), s.music.hasAccess()) { s.music.openAccessSettings() }
        Permission(tr("Calendar", "Kalenda"), CalendarData.hasAccess(ctx)) { askCalendar.launch(Manifest.permission.READ_CALENDAR) }
        Permission(tr("Screen time", "Muda wa skrini"), Usage.hasAccess(ctx)) { Usage.openSettings(ctx) }
        Permission(tr("Do not disturb", "Usisumbue"), Controls.dndAccess(ctx)) { Controls.setDnd(ctx, Controls.dndOn(ctx)) }
        Permission(tr("Brightness slider", "Slider ya mwanga"), Controls.canWriteSettings(ctx)) { Controls.setBrightness(ctx, Controls.brightness(ctx)) }
        Permission(tr("Double-tap lock", "Kufunga kwa kugusa"), LockService.enabled()) { LockService.openSettings(ctx) }
    } }

    // Weather + assistant
    Group(tr("Weather and assistant", "Hali ya hewa na msaidizi")) {
        var city by remember { mutableStateOf(s.weatherCity.value) }
        OutlinedTextField(
            city, { city = it }, singleLine = true,
            label = { Text(tr("Weather city", "Mji wa hali ya hewa")) },
            modifier = Modifier.fillMaxWidth()
        )
        if (city != s.weatherCity.value) TextLink(tr("Save city", "Hifadhi mji")) { s.weatherCity.value = city.trim() }
        Row(Modifier.fillMaxWidth().clickable { onPage("ai") }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("AI assistant", "Msaidizi wa AI"), color = pal.text, fontSize = 15.sp)
                Text(
                    when {
                        !s.assistantOn.value -> tr("Off", "Imezimwa")
                        !s.assistantActive() -> tr("Paused", "Imesitishwa")
                        Keys.get(ctx, s.aiProvider.value).isBlank() -> tr("Add an API key", "Weka API key")
                        else -> Agent.providerName(s.aiProvider.value) + " · " + tr("ready", "iko tayari")
                    },
                    color = pal.dim, fontSize = 12.sp
                )
            }
            Glyph(G.RIGHT, 16.dp, pal.muted)
        }
        Row(Modifier.fillMaxWidth().clickable { onPage("screen") }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Screen time and limits", "Muda wa skrini na vikomo"), color = pal.text, fontSize = 15.sp)
                Text(s.tr("${s.limits.value.size} limits set", "vikomo ${s.limits.value.size}"), color = pal.dim, fontSize = 12.sp)
            }
            Glyph(G.RIGHT, 16.dp, pal.muted)
        }
    }

    // Hidden + locked apps
    if (s.hidden.value.isNotEmpty()) {
        Group(tr("Hidden apps", "Apps zilizofichwa")) {
            s.hidden.value.forEach { pkg ->
                val app = s.apps.value.firstOrNull { it.pkg == pkg }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(app?.label ?: pkg, color = pal.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextLink(tr("Show", "Onyesha")) { s.hidden.toggle(pkg) }
                }
            }
        }
    }

    // Backup
    Group(tr("Backup", "Backup")) {
        Text(
            tr("Saves your settings, tasks, folders, verses and notes to a file.", "Inahifadhi settings, kazi, folda, mistari na maandishi kwenye faili."),
            color = pal.dim, fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextLink(tr("Save backup", "Hifadhi backup")) { exportFile.launch("hugo-launcher-backup.json") }
            TextLink(tr("Restore", "Rudisha")) { importFile.launch(arrayOf("application/json", "text/plain", "*/*")) }
        }
    }

    Text("HUGO Launcher 2.2 · HUGO TECH", color = pal.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

    if (choosingWork) {
        var chosen by remember { mutableStateOf(s.workHidden.value.ifEmpty { s.apps.value.map { it.pkg }.filter { isSocial(it) }.toSet() }) }
        AlertDialog(
            onDismissRequest = { choosingWork = false },
            title = { Text(tr("Hide during Work", "Ficha wakati wa Kazi")) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    s.apps.value.distinctBy { it.pkg }.forEach { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { chosen = if (app.pkg in chosen) chosen - app.pkg else chosen + app.pkg },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(app.pkg in chosen, { chosen = if (it) chosen + app.pkg else chosen - app.pkg })
                            Text(app.label, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { s.workHidden.value = chosen; choosingWork = false }) { Text(tr("Save", "Hifadhi")) } },
            dismissButton = { TextButton(onClick = { choosingWork = false }) { Text(tr("Cancel", "Ghairi")) } }
        )
    }

    if (choosingPack) {
        val packs = remember { IconPacks.installed(ctx) }
        AlertDialog(
            onDismissRequest = { choosingPack = false },
            title = { Text(tr("Icon pack", "Icon pack")) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    MenuRow(tr("None (app icons)", "Hakuna (icons za apps)")) {
                        s.iconPack.value = ""
                        IconCache.clear()
                        s.iconVersion.intValue++
                        choosingPack = false
                    }
                    if (packs.isEmpty()) Text(tr("No icon packs installed. Get one from Play Store.", "Hakuna icon pack. Pakua kutoka Play Store."), fontSize = 13.sp)
                    packs.forEach { p ->
                        MenuRow(p.label) {
                            s.iconPack.value = p.pkg
                            IconPacks.clear()
                            IconCache.clear()
                            s.iconVersion.intValue++
                            choosingPack = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingPack = false }) { Text(tr("Close", "Funga")) } }
        )
    }
}

@Composable
private fun ScreenTimePage() {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val a = accent()
    val ctx = LocalContext.current
    var limitFor by remember { mutableStateOf<AppInfo?>(null) }
    if (!Usage.hasAccess(ctx)) {
        Text(tr("Allow usage access so HUGO can show how long you use each app.", "Ruhusu kuona matumizi ili HUGO ionyeshe muda unaotumia kila app."), color = pal.muted, fontSize = 14.sp)
        Pill(tr("Allow", "Ruhusu")) { Usage.openSettings(ctx) }
        return
    }
    val usage = remember { Usage.todayMinutes(ctx) }
    val rows = usage.entries
        .filter { it.key != ctx.packageName }
        .sortedByDescending { it.value }
        .mapNotNull { e -> s.apps.value.firstOrNull { it.pkg == e.key }?.let { it to e.value } }
        .take(20)
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val total = rows.sumOf { it.second }
    Text("${total / 60}h ${total % 60}m", color = pal.text, fontSize = 34.sp, fontWeight = FontWeight.ExtraLight)
    Text(tr("Today. Tap an app to set a daily limit.", "Leo. Gusa app kuweka kikomo cha siku."), color = pal.dim, fontSize = 12.sp)
    rows.forEach { (app, min) ->
        val limit = s.limits.value[app.pkg]
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { limitFor = app }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(app, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(app.label, color = pal.text, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${min}m" + (limit?.let { " / ${it}m" } ?: ""), color = if (limit != null && min >= (limit.toIntOrNull() ?: 0)) a.main else pal.muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(pal.border)) {
                    Box(Modifier.fillMaxWidth(min.toFloat() / max).height(3.dp).background(a.main))
                }
            }
        }
    }
    limitFor?.let { app -> AppMenu(app, startPage = "limit") { limitFor = null } }
}

/* -------------------------------- Pieces -------------------------------- */

@Composable
fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = accent().main, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        content()
    }
}

@Composable
fun Label(text: String) {
    Text(text, color = LocalPal.current.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun SettingToggle(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalPal.current
    val a = accent()
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = pal.text, fontSize = 15.sp)
            if (sub != null) Text(sub, color = pal.dim, fontSize = 12.sp)
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = a.main, checkedThumbColor = pal.bg, uncheckedTrackColor = pal.border, uncheckedBorderColor = pal.border)
        )
    }
}

@Composable
fun Permission(title: String, granted: Boolean?, onClick: () -> Unit) {
    val pal = LocalPal.current
    val a = accent()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = pal.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            when (granted) {
                true -> tr("On", "Imewashwa")
                false -> tr("Turn on", "Washa")
                null -> tr("Open", "Fungua")
            },
            color = if (granted == true) pal.dim else a.main,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun WallThumb(id: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val a = accent()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Box(
            Modifier
                .size(width = 64.dp, height = 112.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(if (selected) 2.dp else 1.dp, if (selected) a.main else pal.border, RoundedCornerShape(12.dp))
                .clickable { onClick() }
        ) {
            if (id == "custom") {
                Box(Modifier.fillMaxSize().background(pal.cardSolid), contentAlignment = Alignment.Center) { Glyph(G.PLUS, 20.dp, pal.muted) }
            } else {
                Wallpaper(id, Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (selected) pal.text else pal.dim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
