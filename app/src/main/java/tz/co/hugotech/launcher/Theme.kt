package tz.co.hugotech.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** Colours used across the launcher (charcoal + gold). */
object HC {
    val Bg = Color(0xFF0E0E10)
    val Card = Color(0xE61C1B1E)
    val CardSolid = Color(0xFF1C1B1E)
    val Border = Color(0x14FFFFFF)
    val Text = Color(0xFFF4F1EA)
    val Muted = Color(0xFFA8A29E)
    val Dim = Color(0xFF78716C)
    val TileTop = Color(0xFF3C3B40)
    val TileBottom = Color(0xFF1F1E22)
    val Badge = Color(0xFFE5484D)
    val VerseGold = Color(0xFFF5E3A3)
}

data class Accent(
    val id: String,
    val label: String,
    val main: Color,
    val soft: Color,
    val rim: Brush
)

val Accents = listOf(
    Accent(
        "gold", "Gold", Color(0xFFD4AF37), Color(0xFFE6D3A0),
        Brush.linearGradient(listOf(Color(0xFFF5E3A3), Color(0xFFB8902A), Color(0xFF6B4F12)))
    ),
    Accent(
        "silver", "Silver", Color(0xFFC8CCD2), Color(0xFFDDE0E4),
        Brush.linearGradient(listOf(Color(0xFFF2F4F7), Color(0xFF9EA3AA), Color(0xFF50545A)))
    ),
    Accent(
        "rose", "Rose gold", Color(0xFFD9A38F), Color(0xFFEBC5B6),
        Brush.linearGradient(listOf(Color(0xFFF6D5C8), Color(0xFFB9776A), Color(0xFF5E352D)))
    )
)

fun accentOf(id: String): Accent = Accents.firstOrNull { it.id == id } ?: Accents[0]

private fun gem(light: Long, mid: Long, dark: Long): Brush = Brush.linearGradient(
    0f to Color(light), 0.45f to Color(mid), 0.85f to Color(dark), 1f to Color(mid)
)

object Gems {
    val Onyx = gem(0xFF5A5960, 0xFF26252A, 0xFF0B0B0D)
    val Sapphire = gem(0xFF6B8CC7, 0xFF1E3A78, 0xFF0B1530)
    val Emerald = gem(0xFF5FAF8A, 0xFF145C42, 0xFF06241A)
    val Amethyst = gem(0xFF9C7BC4, 0xFF4B2A73, 0xFF1C0E2E)

    private val talk = listOf("dialer", "phone", "contacts", "mms", "messag", "whatsapp", "telegram", "sms", "signal")
    private val work = listOf("mail", "gm", "calendar", "docs", "office", "sheets", "slides", "drive", "keep", "notes", "files", "documents", "teams", "zoom", "outlook")
    private val personal = listOf("music", "audiomack", "spotify", "photo", "gallery", "camera", "youtube", "video", "bible", "netflix")

    /** "talk", "work", "personal" or "other" — used for gem colours and Matte folders. */
    fun typeOf(pkg: String): String {
        val p = pkg.lowercase()
        return when {
            talk.any { p.contains(it) } -> "talk"
            work.any { p.contains(it) } -> "work"
            personal.any { p.contains(it) } -> "personal"
            else -> "other"
        }
    }

    /** Gem colour by app type: talk = emerald, work = sapphire, personal = amethyst, rest = onyx. */
    fun forPackage(pkg: String): Brush {
        val p = pkg.lowercase()
        return when {
            talk.any { p.contains(it) } -> Emerald
            work.any { p.contains(it) } -> Sapphire
            personal.any { p.contains(it) } -> Amethyst
            else -> Onyx
        }
    }
}

/** Simple line icons (SVG path strings on a 24x24 grid). */
object G {
    const val PHONE = "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"
    const val SEARCH = "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z M20 20l-4-4"
    const val SETTINGS = "M4 6h10 M18 6h2 M4 12h4 M12 12h8 M4 18h12 M16 4v4 M10 10v4 M18 16v4"
    const val DOWN = "M6 9l6 6 6-6"
    const val UP = "M6 15l6-6 6 6"
    const val BACK = "M15 5l-7 7 7 7"
    const val HEART = "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"
    const val MIC = "M12 3a3 3 0 0 1 3 3v6a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z M5 11a7 7 0 0 0 14 0 M12 18v3"
    const val PLUS = "M12 5v14 M5 12h14"
    const val TIMER = "M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16z M12 9v4l2.5 1.5 M10 2h4"
    const val CHECK = "M5 12l5 5 9-10"
    const val MUSIC = "M9 18V5l11-2v13 M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0z M20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"
    const val CROSS = "M12 3v18 M7 8h10"
    const val PLAY = "M7 4v16l13-8z"
    const val PAUSE = "M7 5h4v14H7z M13 5h4v14h-4z"
    const val PREV = "M6 5h2v14H6z M20 5v14L9 12z"
    const val NEXT = "M16 5h2v14h-2z M4 5v14l11-7z"
}

/** Draws one of the [G] icons. */
@Composable
fun Glyph(path: String, size: Dp, color: Color, fill: Boolean = false, stroke: Float = 2f) {
    val p = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(Modifier.size(size)) {
        val k = this.size.width / 24f
        scale(k, k, pivot = Offset.Zero) {
            if (fill) {
                drawPath(p, color, style = Fill)
            } else {
                drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
