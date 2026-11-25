package tz.co.hugotech.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* --------------------------------- Colours --------------------------------- */

/** Calm, faded colours: soft charcoal, warm grey, muted off-gold. Nothing bright. */
data class Pal(
    val bg: Color,
    val card: Color,
    val cardSolid: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val dim: Color,
    val tileTop: Color,
    val tileBottom: Color,
    val dark: Boolean
)

val DarkPal = Pal(
    bg = Color(0xFF1A1A1C),
    card = Color(0xE6232325),
    cardSolid = Color(0xFF232325),
    border = Color(0xFF2E2E31),
    text = Color(0xFFE6E4DF),
    muted = Color(0xFF9A9893),
    dim = Color(0xFF6C6A66),
    tileTop = Color(0xFF353438),
    tileBottom = Color(0xFF242326),
    dark = true
)

val LightPal = Pal(
    bg = Color(0xFFEDECE8),
    card = Color(0xFFF7F6F3),
    cardSolid = Color(0xFFF7F6F3),
    border = Color(0xFFDCDAD4),
    text = Color(0xFF232325),
    muted = Color(0xFF6E6C68),
    dim = Color(0xFF9C9A95),
    tileTop = Color(0xFFFFFFFF),
    tileBottom = Color(0xFFEDECE8),
    dark = false
)

val LocalPal = staticCompositionLocalOf { DarkPal }

data class Accent(val id: String, val label: String, val main: Color, val soft: Color, val rim: Brush)

val Accents = listOf(
    Accent("gold", "Off-gold", Color(0xFFB3A27C), Color(0xFFCBBF9F),
        Brush.linearGradient(listOf(Color(0xFFD8CDB2), Color(0xFFA8956E), Color(0xFF5C5140)))),
    Accent("silver", "Silver", Color(0xFFA8ACB1), Color(0xFFC8CBCF),
        Brush.linearGradient(listOf(Color(0xFFE2E4E7), Color(0xFF9DA2A8), Color(0xFF51555A)))),
    Accent("rose", "Rose", Color(0xFFB38F84), Color(0xFFCFB1A8),
        Brush.linearGradient(listOf(Color(0xFFE2CAC2), Color(0xFFA6796D), Color(0xFF583A32)))),
    Accent("red", "Muted red", Color(0xFFA9484C), Color(0xFFC27C7F),
        Brush.linearGradient(listOf(Color(0xFFD9A3A5), Color(0xFFA34A4E), Color(0xFF55262A))))
)

fun accentOf(id: String): Accent = Accents.firstOrNull { it.id == id } ?: Accents[0]

@Composable
fun accent(): Accent = accentOf(LocalLauncher.current.accent.value)

@Composable
fun tr(en: String, sw: String): String = LocalLauncher.current.tr(en, sw)

/* ---------------------------------- Gems ---------------------------------- */

private fun gem(light: Long, mid: Long, dark: Long): Brush =
    Brush.linearGradient(0f to Color(light), 0.45f to Color(mid), 0.85f to Color(dark), 1f to Color(mid))

object Gems {
    val Onyx = gem(0xFF57565C, 0xFF2A292D, 0xFF111113)
    val Sapphire = gem(0xFF7A8DB0, 0xFF2C3D5E, 0xFF121A2A)
    val Emerald = gem(0xFF729C88, 0xFF24503F, 0xFF0E231B)
    val Amethyst = gem(0xFF9586AE, 0xFF443556, 0xFF1C1525)

    private val talk = listOf("dialer", "phone", "contacts", "mms", "messag", "whatsapp", "telegram", "sms", "signal")
    private val work = listOf("mail", ".gm", "calendar", "docs", "office", "sheets", "slides", "drive", "keep", "notes", "files", "documents", "teams", "zoom", "outlook")
    private val media = listOf("music", "audiomack", "spotify", "photo", "gallery", "camera", "youtube", "video", "bible", "netflix")

    fun typeOf(pkg: String): String {
        val p = pkg.lowercase()
        return when {
            talk.any { p.contains(it) } -> "talk"
            work.any { p.contains(it) } -> "work"
            media.any { p.contains(it) } -> "media"
            else -> "other"
        }
    }

    fun forPackage(pkg: String): Brush = when (typeOf(pkg)) {
        "talk" -> Emerald
        "work" -> Sapphire
        "media" -> Amethyst
        else -> Onyx
    }
}

/* ---------------------------------- Icons ---------------------------------- */

/** Line icons drawn from SVG paths on a 24×24 grid. */
object G {
    const val PHONE = "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"
    const val SEARCH = "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z M20 20l-4-4"
    const val SETTINGS = "M4 6h10 M18 6h2 M4 12h4 M12 12h8 M4 18h12 M16 4v4 M10 10v4 M18 16v4"
    const val DOWN = "M6 9l6 6 6-6"
    const val UP = "M6 15l6-6 6 6"
    const val BACK = "M15 5l-7 7 7 7"
    const val RIGHT = "M9 5l7 7-7 7"
    const val HEART = "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"
    const val MIC = "M12 3a3 3 0 0 1 3 3v6a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z M5 11a7 7 0 0 0 14 0 M12 18v3"
    const val PLUS = "M12 5v14 M5 12h14"
    const val TIMER = "M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16z M12 9v4l2.5 1.5 M10 2h4"
    const val CHECK = "M5 12l5 5 9-10"
    const val CROSS = "M12 3v18 M7 8h10"
    const val PLAY = "M7 4v16l13-8z"
    const val PAUSE = "M7 5h4v14H7z M13 5h4v14h-4z"
    const val PREV = "M6 5h2v14H6z M20 5v14L9 12z"
    const val NEXT = "M16 5h2v14h-2z M4 5v14l11-7z"
    const val GRID = "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z"
    const val PANEL = "M5 4h14v16H5z M5 9h14"
    const val SUN = "M12 4v2 M12 18v2 M4 12h2 M18 12h2 M6.3 6.3l1.4 1.4 M16.3 16.3l1.4 1.4 M6.3 17.7l1.4-1.4 M16.3 7.7l1.4-1.4 M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"
    const val CLOUD = "M7 18a4 4 0 0 1-.5-8A6 6 0 0 1 18 9a4.5 4.5 0 0 1-1 9z"
    const val RAIN = "M7 15a4 4 0 0 1-.5-8A6 6 0 0 1 18 6a4.5 4.5 0 0 1-1 9z M9 19v2 M13 19v2 M17 19v2"
    const val STORM = "M7 15a4 4 0 0 1-.5-8A6 6 0 0 1 18 6a4.5 4.5 0 0 1-1 9z M12 15l-2 4h4l-2 4"
    const val WIFI = "M2 9a15 15 0 0 1 20 0 M5 12.5a10 10 0 0 1 14 0 M8.5 16a5 5 0 0 1 7 0 M12 19.5h.01"
    const val BT = "M7 7l10 10-5 4V3l5 4L7 17"
    const val TORCH = "M8 3h8v4l-2 3v11h-4V10L8 7z"
    const val MOON = "M20 14A8 8 0 0 1 10 4a8 8 0 1 0 10 10z"
    const val BELL = "M6 17V11a6 6 0 0 1 12 0v6l2 2H4z M10 21h4"
    const val LOCK = "M6 11h12v10H6z M8 11V7a4 4 0 0 1 8 0v4"
    const val BOOK = "M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z M12 8v7 M9.5 10.5h5"
    const val CHART = "M4 20h16 M7 16v-5 M12 16V7 M17 16v-8"
    const val SPARK = "M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z"
    const val CAL = "M4 6h16v14H4z M4 10h16 M8 3v4 M16 3v4"
    const val FOLDER = "M4 6h6l2 2h8v11H4z"
    const val USER = "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4 21a8 8 0 0 1 16 0"
    const val SCREEN = "M7 3h10v18H7z M11 18h2"
    const val MUSIC_VOL = "M4 9v6h4l5 4V5L8 9z M16 9a4 4 0 0 1 0 6"
}

@Composable
fun Glyph(path: String, size: Dp, color: Color, fill: Boolean = false, stroke: Float = 1.9f) {
    val p = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(Modifier.size(size)) {
        val k = this.size.width / 24f
        scale(k, k, pivot = Offset.Zero) {
            if (fill) drawPath(p, color, style = Fill)
            else drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/* ------------------------------ Small pieces ------------------------------ */

@Composable
fun Modifier.card(): Modifier {
    val pal = LocalPal.current
    return this
        .clip(RoundedCornerShape(20.dp))
        .background(pal.card)
        .border(1.dp, pal.border, RoundedCornerShape(20.dp))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = LocalPal.current.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = modifier)
}

@Composable
fun TextLink(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = accent().main,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
fun Pill(text: String, onClick: () -> Unit) {
    val a = accent()
    val pal = LocalPal.current
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(a.main)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (pal.dark) Color(0xFF1A1A1C) else Color(0xFFF7F6F3), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun RoundIcon(path: String, size: Dp, bg: Color, fg: Color, fill: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Glyph(path, size * 0.42f, fg, fill = fill) }
}

@Composable
fun IconButtonSoft(path: String, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(pal.text.copy(alpha = 0.06f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Glyph(path, 18.dp, pal.text) }
}

/** Row of mutually exclusive options. */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val pal = LocalPal.current
    val a = accent()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(pal.text.copy(alpha = 0.05f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (id, label) ->
            val on = id == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) a.main.copy(alpha = 0.22f) else Color.Transparent)
                    .border(1.dp, if (on) a.main.copy(alpha = 0.7f) else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(id) },
                contentAlignment = Alignment.Center
            ) { Text(label, color = if (on) pal.text else pal.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
        }
    }
}
