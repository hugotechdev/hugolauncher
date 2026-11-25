package tz.co.hugotech.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Verse(val text: String, val ref: String)

/** Short encouraging verses — plain-language Swahili renderings. */
object Verses {
    val list = listOf(
        Verse("Usiogope, kwa maana mimi niko pamoja nawe.", "Isaya 41:10"),
        Verse("Ninaweza kufanya yote kupitia Kristo anayenipa nguvu.", "Wafilipi 4:13"),
        Verse("Uwe hodari na jasiri; Bwana Mungu wako yu pamoja nawe kila uendako.", "Yoshua 1:9"),
        Verse("Bwana ndiye mchungaji wangu; sitapungukiwa na kitu.", "Zaburi 23:1"),
        Verse("Mtegemee Bwana kwa moyo wako wote, wala usitegemee akili zako mwenyewe.", "Mithali 3:5"),
        Verse("Njooni kwangu nyote mlio wachovu na wenye mizigo, nami nitawapa raha.", "Mathayo 11:28"),
        Verse("Ninayajua mawazo ninayowawazia: mawazo ya amani, ya kuwapa tumaini na mwisho mwema.", "Yeremia 29:11"),
        Verse("Mambo yote hufanya kazi pamoja kwa faida ya wale wampendao Mungu.", "Warumi 8:28"),
        Verse("Mungu ni kimbilio letu na nguvu yetu, msaada ulio karibu wakati wa shida.", "Zaburi 46:1"),
        Verse("Wanaomngoja Bwana watapata nguvu mpya; watapaa juu kama tai.", "Isaya 40:31"),
        Verse("Mtwikeni yeye fadhaa zenu zote, kwa sababu anawajali.", "1 Petro 5:7"),
        Verse("Hii ndiyo siku aliyoifanya Bwana; tufurahi na kushangilia ndani yake.", "Zaburi 118:24"),
        Verse("Lolote mfanyalo, lifanyeni kwa moyo wote kama kwa Bwana.", "Wakolosai 3:23"),
        Verse("Mkabidhi Bwana kazi zako, na mipango yako itafanikiwa.", "Mithali 16:3"),
        Verse("Amani nawaachieni; amani yangu nawapa. Msifadhaike mioyo yenu.", "Yohana 14:27"),
        Verse("Msaada wangu unatoka kwa Bwana, aliyeziumba mbingu na nchi.", "Zaburi 121:2"),
        Verse("Mungu hakutupa roho ya woga, bali ya nguvu, upendo na kiasi.", "2 Timotheo 1:7"),
        Verse("Rehema za Bwana hazikomi; ni mpya kila asubuhi.", "Maombolezo 3:22-23"),
        Verse("Mkabidhi Bwana njia yako, umtumaini, naye atatenda.", "Zaburi 37:5"),
        Verse("Utamlinda katika amani kamili yule ambaye moyo wake umekutegemea.", "Isaya 26:3"),
        Verse("Tusichoke kutenda mema; kwa wakati wake tutavuna.", "Wagalatia 6:9"),
        Verse("Bwana ni nuru yangu na wokovu wangu; nimwogope nani?", "Zaburi 27:1"),
        Verse("Msiisumbukie kesho; kila siku ina mambo yake ya kutosha.", "Mathayo 6:34"),
        Verse("Sitakuacha kamwe, wala sitakupungukia.", "Waebrania 13:5"),
        Verse("Onjeni mwone ya kuwa Bwana ni mwema.", "Zaburi 34:8"),
        Verse("Mimi ni nuru ya ulimwengu; anifuataye hatatembea gizani.", "Yohana 8:12"),
        Verse("Furahini katika tumaini, vumilieni katika dhiki, dumuni katika maombi.", "Warumi 12:12"),
        Verse("Mbingu zinatangaza utukufu wa Mungu.", "Zaburi 19:1"),
        Verse("Tenda haki, penda rehema, na tembea kwa unyenyekevu na Mungu wako.", "Mika 6:8"),
        Verse("Bwana mwenyewe anakutangulia; hatakuacha. Usiogope.", "Kumbukumbu 31:8"),
        Verse("Upitapo katika maji mengi, mimi nitakuwa pamoja nawe.", "Isaya 43:2")
    )
}

/** Reading plan: the four Gospels and Acts, one chapter a day (117 days). */
object ReadingPlan {
    private val books = listOf("Mathayo" to 28, "Marko" to 16, "Luka" to 24, "Yohana" to 21, "Matendo" to 28)
    val total = books.sumOf { it.second }

    fun chapter(day: Int): String {
        var d = day % total
        for ((book, n) in books) {
            if (d < n) return "$book ${d + 1}"
            d -= n
        }
        return "Mathayo 1"
    }
}

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
private fun yesterday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(System.currentTimeMillis() - 86_400_000L))

fun markRead(s: LauncherState) {
    val t = today()
    if (s.lastRead.value == t) return
    s.streak.value = if (s.lastRead.value == yesterday()) s.streak.value + 1 else 1
    s.lastRead.value = t
    s.planDay.value = s.planDay.value + 1
}

private fun openBible(s: LauncherState) {
    val bible = s.apps.value.firstOrNull { it.realLabel.contains("bible", true) || it.realLabel.contains("biblia", true) }
    if (bible == null || !AppRepository.openPackage(s.context, bible.pkg)) {
        s.toast.value = s.tr("Install a Bible app (e.g. YouVersion) to read here", "Weka app ya Biblia (mfano YouVersion) ili usome")
    }
}

@Composable
fun FaithPage() {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val a = accent()
    var writing by remember { mutableStateOf(false) }
    val readToday = s.lastRead.value == today()
    PageColumn {
        Spacer(Modifier.height(28.dp))
        Text(tr("Faith", "Imani"), color = pal.text, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            VerseCard()

            Column(Modifier.fillMaxWidth().card().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Reading plan", "Mpango wa kusoma"), color = pal.muted, fontSize = 12.sp)
                        Text(ReadingPlan.chapter(s.planDay.value), color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Light)
                        Text(
                            s.tr("Day ${s.planDay.value + 1} of ${ReadingPlan.total} · Gospels and Acts", "Siku ${s.planDay.value + 1} kati ya ${ReadingPlan.total} · Injili na Matendo"),
                            color = pal.dim, fontSize = 12.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${s.streak.value}", color = a.soft, fontSize = 26.sp, fontWeight = FontWeight.Light)
                        Text(tr("day streak", "siku mfululizo"), color = pal.dim, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextLink(tr("Open Bible", "Fungua Biblia")) { openBible(s) }
                    Box(Modifier.weight(1f))
                    if (readToday) {
                        Text(tr("Read today ✓", "Umesoma leo ✓"), color = a.main, fontSize = 13.sp)
                    } else {
                        Pill(tr("Mark as read", "Nimesoma")) { markRead(s) }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().card().padding(16.dp)) {
                Text(tr("Saved verses", "Mistari uliyohifadhi"), color = pal.muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                val saved = Verses.list.filter { it.ref in s.favVerses.value }
                if (saved.isEmpty()) {
                    Text(tr("Tap ♥ on a verse to keep it here.", "Gusa ♥ kwenye mstari ili uuhifadhi hapa."), color = pal.dim, fontSize = 13.sp)
                }
                saved.forEach { v ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text("“${v.text}”", color = pal.text, fontSize = 14.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
                        Text(v.ref, color = pal.dim, fontSize = 11.sp)
                    }
                }
            }

            Column(Modifier.fillMaxWidth().card().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Thanks and prayer", "Shukrani na maombi"), color = pal.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextLink(tr("Write", "Andika")) { writing = true }
                }
                val entries = s.journal.value
                if (entries.isEmpty()) {
                    Text(tr("A line each evening adds up.", "Mstari mmoja kila jioni unajenga mengi."), color = pal.dim, fontSize = 13.sp)
                }
                entries.take(6).forEach { e ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Text(e.date, color = a.main, fontSize = 11.sp, modifier = Modifier.width(78.dp))
                        Text(e.text, color = pal.text, fontSize = 13.sp)
                    }
                }
                if (entries.size > 6) {
                    Text(s.tr("+${entries.size - 6} more", "+${entries.size - 6} zaidi"), color = pal.dim, fontSize = 12.sp)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(pal.text.copy(alpha = 0.04f))
                    .clickable { s.phoneMode.value = "worship"; s.autoMode.value = false }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Worship mode", "Hali ya Ibada"), color = pal.text, fontSize = 15.sp)
                    Text(
                        tr("Quiet notifications, faith wallpaper, social apps stay visible.", "Notifications kimya, wallpaper ya imani."),
                        color = pal.dim, fontSize = 12.sp
                    )
                }
                Glyph(G.CROSS, 20.dp, a.soft)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (writing) JournalDialog { writing = false }
}

