package tz.co.hugotech.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

val OctagonShape = GenericShape { s, _ ->
    val w = s.width
    val h = s.height
    moveTo(0.3f * w, 0f)
    lineTo(0.7f * w, 0f)
    lineTo(w, 0.3f * h)
    lineTo(w, 0.7f * h)
    lineTo(0.7f * w, h)
    lineTo(0.3f * w, h)
    lineTo(0f, 0.7f * h)
    lineTo(0f, 0.3f * h)
    close()
}

/**
 * One app icon in the chosen theme.
 * theme = "gems"  -> emerald-cut gem in a gold (accent) frame
 * theme = "matte" -> charcoal rounded tile
 */
@Composable
fun AppIcon(
    app: AppInfo?,
    iconSize: Dp,
    theme: String,
    accent: Accent,
    badge: Int = 0,
    dimmed: Boolean = false,
    uniform: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(iconSize)
            .alpha(if (dimmed) 0.55f else 1f),
        contentAlignment = Alignment.Center
    ) {
        if (theme == "gems") {
            Box(
                Modifier
                    .fillMaxSize()
                    .shadow(8.dp, OctagonShape)
                    .clip(OctagonShape)
                    .background(accent.rim)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(OctagonShape)
                    .background(Gems.forPackage(app?.pkg ?: ""))
            )
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val top = Path().apply {
                    moveTo(0.3f * w, 0f); lineTo(0.7f * w, 0f); lineTo(0.62f * w, 0.24f * h); lineTo(0.38f * w, 0.24f * h); close()
                }
                drawPath(top, Color.White.copy(alpha = 0.14f))
                val table = Path().apply {
                    moveTo(0.38f * w, 0.24f * h); lineTo(0.62f * w, 0.24f * h); lineTo(0.76f * w, 0.38f * h)
                    lineTo(0.76f * w, 0.62f * h); lineTo(0.62f * w, 0.76f * h); lineTo(0.38f * w, 0.76f * h)
                    lineTo(0.24f * w, 0.62f * h); lineTo(0.24f * w, 0.38f * h); close()
                }
                drawPath(table, Color.White.copy(alpha = 0.12f), style = Stroke(width = 1.dp.toPx()))
            }
        } else {
            val shape = RoundedCornerShape(iconSize * 0.27f)
            Box(
                Modifier
                    .fillMaxSize()
                    .shadow(8.dp, shape)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(HC.TileTop, HC.TileBottom)))
                    .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
            )
        }
        IconContent(app, iconSize, accent, uniform)
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .clip(CircleShape)
                    .background(HC.Badge)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badge > 99) "99+" else badge.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun IconContent(app: AppInfo?, iconSize: Dp, accent: Accent, uniform: Boolean) {
    when {
        app == null -> Glyph(G.PLUS, iconSize * 0.4f, accent.soft)
        app.mono != null -> Image(
            bitmap = app.mono,
            contentDescription = app.label,
            modifier = Modifier.size(iconSize * 0.62f),
            colorFilter = ColorFilter.tint(accent.soft)
        )
        uniform -> Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(iconSize * 0.56f)
                .clip(RoundedCornerShape(iconSize * 0.14f)),
            colorFilter = tintToAccent(accent.soft)
        )
        else -> Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(iconSize * 0.56f)
                .clip(RoundedCornerShape(iconSize * 0.14f))
        )
    }
}

/** Turns a full-colour icon into one colour (keeps light and dark). */
fun tintToAccent(c: Color): ColorFilter {
    val r = c.red
    val g = c.green
    val b = c.blue
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.299f * r, 0.587f * r, 0.114f * r, 0f, 0f,
                0.299f * g, 0.587f * g, 0.114f * g, 0f, 0f,
                0.299f * b, 0.587f * b, 0.114f * b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

/** Background: charcoal with a faint golden spiral and stars. */
@Composable
fun GoldenWallpaper(accent: Accent) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(HC.Bg)
        val glowCenter = Offset(size.width * 0.8f, size.height * 0.35f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF2A2620), Color.Transparent), glowCenter, size.width * 0.9f),
            radius = size.width * 0.9f,
            center = glowCenter
        )
        val k = size.width / 390f
        val b = ln(1.618) / (PI / 2)
        val cx = size.width * 0.67f
        val cy = size.height * 0.39f
        val spiral = Path()
        for (i in 0..240) {
            val th = i * (5.5 * PI) / 240
            val r = 2 * exp(b * th) * k
            val x = (cx + r * cos(th)).toFloat()
            val y = (cy - r * sin(th)).toFloat()
            if (i == 0) spiral.moveTo(x, y) else spiral.lineTo(x, y)
        }
        drawPath(spiral, accent.main.copy(alpha = 0.16f), style = Stroke(width = 1.2f * k))
        val rnd = Random(7)
        repeat(40) {
            drawCircle(
                color = HC.VerseGold.copy(alpha = 0.2f + rnd.nextFloat() * 0.5f),
                radius = (0.4f + rnd.nextFloat() * 1.3f) * k,
                center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
            )
        }
    }
}
