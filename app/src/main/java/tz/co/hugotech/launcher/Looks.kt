package tz.co.hugotech.launcher

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

/* -------------------------------- App icons -------------------------------- */

val OctagonShape = GenericShape { s, _ ->
    val w = s.width
    val h = s.height
    moveTo(0.3f * w, 0f); lineTo(0.7f * w, 0f); lineTo(w, 0.3f * h); lineTo(w, 0.7f * h)
    lineTo(0.7f * w, h); lineTo(0.3f * w, h); lineTo(0f, 0.7f * h); lineTo(0f, 0.3f * h); close()
}

/** Turns a colour icon into one colour, keeping its light and shade. */
fun tintTo(c: Color): ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.299f * c.red, 0.587f * c.red, 0.114f * c.red, 0f, 0f,
            0.299f * c.green, 0.587f * c.green, 0.114f * c.green, 0f, 0f,
            0.299f * c.blue, 0.587f * c.blue, 0.114f * c.blue, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

/** An app icon in the chosen icon theme (gems, matte, mono or original). */
@Composable
fun AppIcon(app: AppInfo?, size: Dp, badge: Int = 0, dimmed: Boolean = false) {
    val s = LocalLauncher.current
    val pal = LocalPal.current
    val a = accent()
    val theme = s.iconTheme.value
    val icons = if (app != null) rememberAppIcons(app) else null
    val glyphColor = if (pal.dark) a.soft else pal.text
    val forceTint = s.uniformIcons.value || theme == "mono"

    Box(
        Modifier
            .size(size)
            .alpha(if (dimmed) 0.5f else 1f),
        contentAlignment = Alignment.Center
    ) {
        when (theme) {
            "ios" -> {
                // Solid tile like iPhone dark mode, in the colour the user picked
                val shape = RoundedCornerShape(size * 0.24f)
                val tt = TileTones.of(s.tileTone.value)
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(Brush.verticalGradient(listOf(tt.top, tt.bottom)))
                        .border(0.7.dp, Color.White.copy(alpha = if (tt.light) 0.5f else 0.16f), shape)
                )
            }
            "glass" -> {
                // See-through glass tile with a light edge and a soft shine at the top
                val shape = RoundedCornerShape(size * 0.24f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.07f))))
                        .border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.12f))), shape)
                )
            }
            "gems" -> {
                Box(Modifier.fillMaxSize().shadow(6.dp, OctagonShape).clip(OctagonShape).background(a.rim))
                Box(Modifier.fillMaxSize().padding(2.dp).clip(OctagonShape).background(Gems.forPackage(app?.pkg ?: "")))
                Canvas(Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val h = this.size.height
                    drawPath(Path().apply {
                        moveTo(0.3f * w, 0f); lineTo(0.7f * w, 0f); lineTo(0.62f * w, 0.24f * h); lineTo(0.38f * w, 0.24f * h); close()
                    }, Color.White.copy(alpha = 0.10f))
                    drawPath(Path().apply {
                        moveTo(0.38f * w, 0.24f * h); lineTo(0.62f * w, 0.24f * h); lineTo(0.76f * w, 0.38f * h)
                        lineTo(0.76f * w, 0.62f * h); lineTo(0.62f * w, 0.76f * h); lineTo(0.38f * w, 0.76f * h)
                        lineTo(0.24f * w, 0.62f * h); lineTo(0.24f * w, 0.38f * h); close()
                    }, Color.White.copy(alpha = 0.09f), style = Stroke(width = 1.dp.toPx()))
                }
            }
            "matte" -> {
                val shape = RoundedCornerShape(size * 0.27f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .shadow(5.dp, shape)
                        .clip(shape)
                        .background(Brush.verticalGradient(listOf(pal.tileTop, pal.tileBottom)))
                        .border(0.5.dp, pal.text.copy(alpha = 0.08f), shape)
                )
            }
            "mono" -> {
                val shape = RoundedCornerShape(size * 0.3f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(if (pal.dark) Color(0xFF2A2A2C) else Color(0xFFFFFFFF))
                        .border(1.dp, pal.border, shape)
                )
            }
        }
        val inner = if (theme == "original") size else size * 0.6f
        val darkTile = theme == "ios" || theme == "glass"
        val lightTile = theme == "ios" && TileTones.of(s.tileTone.value).light
        val tone = (Color(icons?.tone ?: 0).takeIf { icons?.tone != 0 } ?: Color(0xFFE8E8EA))
            .let { if (lightTile && it.red + it.green + it.blue > 2.4f) Color(0xFF1C1C1E) else it }
        when {
            app == null -> Glyph(G.PLUS, size * 0.36f, if (darkTile) Color(0xFFE8E8EA) else glyphColor)
            icons == null -> Box(Modifier.size(inner * 0.8f).clip(CircleShape).background(pal.text.copy(alpha = 0.06f)))
            darkTile && !forceTint && icons.mono != null -> Image(
                bitmap = icons.mono,
                contentDescription = app.label,
                modifier = Modifier.size(size * 0.92f),
                colorFilter = ColorFilter.tint(tone)
            )
            darkTile && !forceTint && icons.fg != null -> Image(
                bitmap = icons.fg,
                contentDescription = app.label,
                modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)),
                colorFilter = if (icons.fgPlain) ColorFilter.tint(tone, BlendMode.Modulate) else null
            )
            darkTile && icons.full != null -> Image(
                bitmap = icons.full,
                contentDescription = app.label,
                modifier = Modifier.size(size * 0.74f).clip(RoundedCornerShape(size * 0.2f)),
                colorFilter = if (forceTint) tintTo(glyphColor) else null
            )
            icons.mono != null && forceTint -> Image(
                bitmap = icons.mono,
                contentDescription = app.label,
                modifier = Modifier.size(inner),
                colorFilter = ColorFilter.tint(glyphColor)
            )
            icons.full != null -> Image(
                bitmap = icons.full,
                contentDescription = app.label,
                modifier = Modifier
                    .size(if (theme == "original") size else inner * 1.12f)
                    .clip(RoundedCornerShape(if (theme == "original") size * 0.24f else size * 0.18f)),
                colorFilter = if (forceTint && theme != "original") tintTo(glyphColor) else null
            )
            // Icon could not be read: show the first letter of the app name
            else -> Box(
                Modifier.size(inner).clip(RoundedCornerShape(size * 0.24f)).background(a.main.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    app.label.take(1).uppercase(), color = pal.text,
                    fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Medium
                )
            }
        }
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp)
                    .defaultMinSize(minWidth = 19.dp, minHeight = 19.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB4555A))
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (badge > 99) "99+" else badge.toString(), color = Color(0xFFF7F6F3), fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/* -------------------------------- Tile colours ------------------------------ */

class TileTone(val id: String, val sw: String, val en: String, val top: Color, val bottom: Color, val light: Boolean = false)

object TileTones {
    val all = listOf(
        TileTone("black", "Nyeusi", "Black", Color(0xFF3A3A3D), Color(0xFF151517)),
        TileTone("graphite", "Kijivu", "Graphite", Color(0xFF5A5B60), Color(0xFF2A2B2F)),
        TileTone("navy", "Bluu", "Navy", Color(0xFF26416E), Color(0xFF0E1B33)),
        TileTone("wine", "Mvinyo", "Wine", Color(0xFF5E2131), Color(0xFF2A0E15)),
        TileTone("forest", "Kijani", "Forest", Color(0xFF24503B), Color(0xFF0E2419)),
        TileTone("gold", "Gold", "Gold", Color(0xFF6E5E3C), Color(0xFF2E2616)),
        TileTone("purple", "Zambarau", "Purple", Color(0xFF4B2A6E), Color(0xFF1C0F2B)),
        TileTone("white", "Nyeupe", "White", Color(0xFFF7F7F9), Color(0xFFDCDCE1), light = true)
    )
    fun of(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}

/* -------------------------------- Wallpapers -------------------------------- */

val WALLPAPERS = listOf(
    "spiral" to ("Golden spiral" to "Spiral ya dhahabu"),
    "galaxy" to ("Heavens" to "Mbingu"),
    "atom" to ("Atom" to "Atomu"),
    "dawn" to ("Mountains" to "Milima"),
    "dna" to ("DNA" to "DNA"),
    "cross" to ("Cross of light" to "Msalaba wa nuru"),
    "plain" to ("Plain" to "Rangi moja")
)

private fun customFile(s: LauncherState) = File(s.context.filesDir, "wallpaper.jpg")

/** Saves a photo from the gallery as the wallpaper (scaled down to stay light). */
fun saveCustomWallpaper(s: LauncherState, uri: Uri): Boolean = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    s.context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 2560) sample *= 2
    val bmp = s.context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    if (bmp == null) {
        false
    } else {
        FileOutputStream(customFile(s)).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
        s.wallpaper.value = "custom"
        s.sp.edit().putLong("wallpaper_at", System.currentTimeMillis()).apply()
        true
    }
} catch (e: Exception) {
    false
}

@Composable
fun Wallpaper(id: String, modifier: Modifier = Modifier.fillMaxSize()) {
    val s = LocalLauncher.current
    val a = accent()
    if (id == "custom") {
        val stamp = s.sp.getLong("wallpaper_at", 0L)
        val img by produceState<ImageBitmap?>(null, stamp) {
            value = withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(customFile(s).absolutePath)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
        }
        Box(modifier.background(DarkPal.bg)) {
            img?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
        }
        return
    }
    val line = a.main
    Spacer(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val k = w / 390f
            val bg = DarkPal.bg
            val rnd = Random(7)
            val stars = List(if (id == "galaxy") 90 else 36) {
                Triple(Offset(rnd.nextFloat() * w, rnd.nextFloat() * h), (0.4f + rnd.nextFloat() * 1.2f) * k, 0.12f + rnd.nextFloat() * 0.35f)
            }
            val spiral = Path()
            if (id == "spiral") {
                val b = ln(1.618) / (PI / 2)
                for (i in 0..240) {
                    val th = i * (5.5 * PI) / 240
                    val r = 2 * exp(b * th) * k
                    val x = (w * 0.67f + r * cos(th)).toFloat()
                    val y = (h * 0.39f - r * sin(th)).toFloat()
                    if (i == 0) spiral.moveTo(x, y) else spiral.lineTo(x, y)
                }
            }
            val hills = listOf(
                Path().apply { moveTo(0f, h * 0.60f); lineTo(w * 0.2f, h * 0.49f); lineTo(w * 0.34f, h * 0.55f); lineTo(w * 0.53f, h * 0.43f); lineTo(w * 0.72f, h * 0.54f); lineTo(w * 0.86f, h * 0.48f); lineTo(w, h * 0.56f); lineTo(w, h); lineTo(0f, h); close() },
                Path().apply { moveTo(0f, h * 0.69f); lineTo(w * 0.17f, h * 0.62f); lineTo(w * 0.38f, h * 0.68f); lineTo(w * 0.6f, h * 0.59f); lineTo(w * 0.81f, h * 0.67f); lineTo(w, h * 0.63f); lineTo(w, h); lineTo(0f, h); close() },
                Path().apply { moveTo(0f, h * 0.79f); lineTo(w * 0.24f, h * 0.72f); lineTo(w * 0.5f, h * 0.78f); lineTo(w * 0.76f, h * 0.71f); lineTo(w, h * 0.76f); lineTo(w, h); lineTo(0f, h); close() }
            )
            onDrawBehind {
                drawRect(bg)
                when (id) {
                    "spiral" -> {
                        drawCircle(Brush.radialGradient(listOf(Color(0xFF2A2825), Color.Transparent), Offset(w * 0.8f, h * 0.35f), w * 0.9f), w * 0.9f, Offset(w * 0.8f, h * 0.35f))
                        drawPath(spiral, line.copy(alpha = 0.16f), style = Stroke(1.2f * k))
                        stars.forEach { (c, r, al) -> drawCircle(a.soft.copy(alpha = al), r, c) }
                    }
                    "galaxy" -> {
                        drawCircle(Brush.radialGradient(listOf(Color(0xFF34304A), Color.Transparent), Offset(w * 0.3f, h * 0.3f), w * 0.8f), w * 0.8f, Offset(w * 0.3f, h * 0.3f))
                        drawCircle(Brush.radialGradient(listOf(Color(0xFF3A342A), Color.Transparent), Offset(w * 0.75f, h * 0.65f), w * 0.7f), w * 0.7f, Offset(w * 0.75f, h * 0.65f))
                        stars.forEach { (c, r, al) -> drawCircle(Color(0xFFEDE9E0).copy(alpha = al + 0.15f), r, c) }
                    }
                    "atom" -> {
                        val c = Offset(w / 2f, h * 0.42f)
                        val rx = w * 0.42f
                        val ry = w * 0.13f
                        for (deg in listOf(0f, 60f, 120f)) {
                            val p = Path()
                            for (i in 0..120) {
                                val t = i / 120.0 * 2 * PI
                                val x0 = rx * cos(t)
                                val y0 = ry * sin(t)
                                val rad = deg / 180.0 * PI
                                val x = (c.x + x0 * cos(rad) - y0 * sin(rad)).toFloat()
                                val y = (c.y + x0 * sin(rad) + y0 * cos(rad)).toFloat()
                                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                            }
                            drawPath(p, line.copy(alpha = 0.35f), style = Stroke(1.1f * k))
                        }
                        drawCircle(line.copy(alpha = 0.12f), 26f * k, c)
                        drawCircle(a.soft.copy(alpha = 0.8f), 9f * k, c)
                    }
                    "dawn" -> {
                        drawRect(Brush.verticalGradient(listOf(Color(0xFF1C1B1A), Color(0xFF3B3326), Color(0xFF222120)), 0f, h * 0.7f))
                        drawCircle(a.soft.copy(alpha = 0.12f), 70f * k, Offset(w / 2f, h * 0.5f))
                        drawCircle(a.soft.copy(alpha = 0.75f), 34f * k, Offset(w / 2f, h * 0.5f))
                        drawPath(hills[0], Color(0xFF34322F))
                        drawPath(hills[1], Color(0xFF262523))
                        drawPath(hills[2], Color(0xFF191918))
                    }
                    "dna" -> {
                        for (i in 0 until 34) {
                            val y = h * 0.08f + i * (h * 0.84f / 34)
                            val t = i * 0.42
                            val x1 = (w / 2 + w * 0.26f * sin(t)).toFloat()
                            val x2 = (w / 2 - w * 0.26f * sin(t)).toFloat()
                            drawLine(line.copy(alpha = 0.14f), Offset(x1, y), Offset(x2, y), 1f * k)
                            drawCircle(a.soft.copy(alpha = 0.55f), ((3 + 1.5 * cos(t)) * k).toFloat(), Offset(x1, y))
                            drawCircle(Color(0xFF8C8B88).copy(alpha = 0.5f), ((3 - 1.5 * cos(t)) * k).toFloat(), Offset(x2, y))
                        }
                    }
                    "cross" -> {
                        val c = Offset(w / 2f, h * 0.36f)
                        drawCircle(Brush.radialGradient(listOf(a.soft.copy(alpha = 0.22f), Color.Transparent), c, w * 0.7f), w * 0.7f, c)
                        listOf(0f, 0.3f, 0.7f, 1f).forEach { fx -> drawLine(a.soft.copy(alpha = 0.07f), c, Offset(w * fx, 0f), 1f * k) }
                        drawLine(a.soft.copy(alpha = 0.07f), c, Offset(0f, h * 0.8f), 1f * k)
                        drawLine(a.soft.copy(alpha = 0.07f), c, Offset(w, h * 0.8f), 1f * k)
                        drawRoundRect(a.soft.copy(alpha = 0.85f), Offset(c.x - 3f * k, c.y - 70f * k), Size(6f * k, 150f * k), androidx.compose.ui.geometry.CornerRadius(3f * k))
                        drawRoundRect(a.soft.copy(alpha = 0.85f), Offset(c.x - 34f * k, c.y - 30f * k), Size(68f * k, 6f * k), androidx.compose.ui.geometry.CornerRadius(3f * k))
                    }
                    else -> {}
                }
            }
        }
    )
}
