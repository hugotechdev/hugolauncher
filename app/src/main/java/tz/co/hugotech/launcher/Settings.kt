package tz.co.hugotech.launcher

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    apps: List<AppInfo>,
    allApps: List<AppInfo>,
    theme: String,
    accent: Accent,
    showLabels: Boolean,
    hidden: Set<String>,
    musicAccess: Boolean,
    onTheme: (String) -> Unit,
    onAccent: (String) -> Unit,
    onLabels: (Boolean) -> Unit,
    onUnhide: (String) -> Unit,
    onOpenMusicAccess: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val sample = apps.take(4)
    Column(
        Modifier
            .fillMaxSize()
            .background(HC.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) { Glyph(G.BACK, 20.dp, HC.Text) }
            Spacer(Modifier.width(12.dp))
            Text("Themes & settings", color = HC.Text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        }

        // Live preview
        Row(
            Modifier
                .fillMaxWidth()
                .card(accent.main.copy(alpha = 0.3f))
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (sample.isEmpty()) {
                repeat(4) { AppIcon(null, 54.dp, theme, accent) }
            } else {
                sample.forEach { AppIcon(it, 54.dp, theme, accent) }
            }
        }

        Section("Icon theme")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Option("Gems", theme == "gems", accent, Modifier.weight(1f)) { onTheme("gems") }
            Option("Matte", theme == "matte", accent, Modifier.weight(1f)) { onTheme("matte") }
        }

        Section("Accent")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Accents.forEach { a ->
                Option(a.label, a.id == accent.id, a, Modifier.weight(1f), dot = a.main) { onAccent(a.id) }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .card()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Show app names", color = HC.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = showLabels,
                onCheckedChange = onLabels,
                colors = SwitchDefaults.colors(checkedTrackColor = accent.main, checkedThumbColor = HC.Bg)
            )
        }

        Section("Setup")
        ActionRow(
            title = "Make HUGO your home screen",
            subtitle = "Choose HUGO Launcher as the default Home app",
            button = "Open",
            accent = accent
        ) {
            AppRepository.safeStart(context, Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        ActionRow(
            title = "Music controls & badges",
            subtitle = if (musicAccess) "On — Audiomack controls and notification dots work" else "Off — allow HUGO Launcher under Notification access",
            button = if (musicAccess) "Manage" else "Turn on",
            accent = accent,
            onClick = onOpenMusicAccess
        )

        if (hidden.isNotEmpty()) {
            Section("Hidden apps")
            hidden.forEach { pkg ->
                val app = allApps.firstOrNull { it.pkg == pkg }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .card()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app, 40.dp, theme, accent)
                    Spacer(Modifier.width(12.dp))
                    Text(app?.label ?: pkg, color = HC.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    PillButton("Unhide", accent) { onUnhide(pkg) }
                }
            }
        }

        Text(
            "HUGO Smart Launcher · v1.0 · HUGO TECH",
            color = HC.Dim,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = HC.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Option(
    label: String,
    selected: Boolean,
    accent: Accent,
    modifier: Modifier,
    dot: Color? = null,
    onClick: () -> Unit
) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.main.copy(alpha = 0.16f) else HC.CardSolid)
            .border(2.dp, if (selected) accent.main else Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot != null) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (selected) accent.soft else HC.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, button: String, accent: Accent, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .card()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = HC.Text, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = HC.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        PillButton(button, accent, onClick)
    }
}
