package tz.co.hugotech.launcher

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pulled down from the home screen: phone mode, controls, focus, music, key people. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPanel(onClose: () -> Unit) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val a = accent()
    val ctx = LocalContext.current
    var brightness by remember { mutableFloatStateOf(Controls.brightness(ctx)) }
    var volume by remember { mutableFloatStateOf(Controls.volume(ctx)) }
    var sleep by remember { mutableIntStateOf(0) }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        if (r.resultCode == Activity.RESULT_OK && uri != null) {
            try {
                ctx.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val name = c.getString(0) ?: ""
                        val number = c.getString(1) ?: ""
                        if (number.isNotBlank()) s.contacts.value = (s.contacts.value + Contact(name, number)).takeLast(5)
                    }
                }
            } catch (e: Exception) {
                s.toast.value = s.tr("Couldn't read that contact", "Imeshindwa kusoma mtu huyo")
            }
        }
    }
    val sliderColors = SliderDefaults.colors(thumbColor = a.soft, activeTrackColor = a.main, inactiveTrackColor = pal.border)

    Box(Modifier.fillMaxSize().background(pal.bg), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ClockText(timeSize = 36.sp) }
                IconButtonSoft(G.BELL) { AppRepository.expandNotifications(ctx) }
                Spacer(Modifier.width(8.dp))
                IconButtonSoft(G.UP) { onClose() }
            }

            SectionTitle(tr("Phone mode", "Hali ya simu"))
            Segmented(
                listOf("work" to s.tr("Work", "Kazi"), "worship" to s.tr("Worship", "Ibada"), "rest" to s.tr("Rest", "Pumziko")),
                if (s.autoMode.value) "" else s.phoneMode.value
            ) {
                s.autoMode.value = false
                s.phoneMode.value = it
            }
            Text(
                when (s.effectiveMode()) {
                    "work" -> tr("Social apps you chose are hidden. Focus on work.", "Apps za mitandao ulizochagua zimefichwa. Zingatia kazi.")
                    "worship" -> tr("Do not disturb on, faith wallpaper.", "Usisumbue imewashwa, wallpaper ya imani.")
                    else -> tr("All apps, notifications on.", "Apps zote, notifications zimewashwa.")
                } + if (s.autoMode.value) s.tr("  (automatic by time)", "  (yenyewe kwa saa)") else "",
                color = pal.muted, fontSize = 12.sp
            )

            QuickToggles(dark = true)

            Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(G.SUN, 18.dp, pal.muted)
                    Spacer(Modifier.width(10.dp))
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        onValueChangeFinished = { Controls.setBrightness(ctx, brightness) },
                        colors = sliderColors,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(G.MUSIC_VOL, 18.dp, pal.muted)
                    Spacer(Modifier.width(10.dp))
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            Controls.setVolume(ctx, it)
                        },
                        colors = sliderColors,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier
                        .weight(1f)
                        .card()
                        .clickable { startFocus(s) }
                        .padding(14.dp)
                ) {
                    Text(tr("Focus", "Umakini"), color = pal.muted, fontSize = 12.sp)
                    if (s.focusEnd.longValue > System.currentTimeMillis()) FocusCountdown(big = true)
                    else Text("25:00", color = pal.text, fontSize = 34.sp, fontWeight = FontWeight.ExtraLight)
                    Text(
                        if (s.focusEnd.longValue > System.currentTimeMillis()) tr("Tap to stop", "Gusa kusimamisha") else tr("Tap to start", "Gusa kuanza"),
                        color = pal.dim, fontSize = 11.sp
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .card()
                        .clickable {
                            sleep = when (sleep) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 }
                            s.music.sleepAfter(sleep)
                        }
                        .padding(14.dp)
                ) {
                    Text(tr("Sleep timer", "Kipima usingizi"), color = pal.muted, fontSize = 12.sp)
                    Text(if (sleep == 0) tr("Off", "Zima") else "$sleep min", color = pal.text, fontSize = 34.sp, fontWeight = FontWeight.ExtraLight)
                    Text(tr("Pauses music. Tap to change.", "Inasimamisha muziki. Gusa kubadili."), color = pal.dim, fontSize = 11.sp)
                }
            }

            MusicCard()

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(tr("Key people", "Watu muhimu"), Modifier.weight(1f))
                TextLink(tr("Add", "Ongeza")) {
                    try {
                        pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                    } catch (e: Exception) {
                        s.toast.value = s.tr("No contacts app", "Hakuna app ya contacts")
                    }
                }
            }
            if (s.contacts.value.isEmpty()) {
                Text(tr("Add up to 5 people you call often. Long-press to remove.", "Ongeza hadi watu 5 unaowapigia mara kwa mara. Bonyeza kwa muda kuondoa."), color = pal.dim, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                s.contacts.value.forEach { c ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { Controls.dial(ctx, c.number) },
                                onLongClick = { s.contacts.value = s.contacts.value - c }
                            )
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, a.main, CircleShape)
                                .background(pal.cardSolid),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                c.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() },
                                color = a.soft, fontSize = 15.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(c.name.substringBefore(" "), color = pal.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 64.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
