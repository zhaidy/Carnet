package com.personal.carnet.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** 液态玻璃的配色：深紫墨色的字，紫到玫红的强调色 */
object Palette {
    val Ink = Color(0xFF1F1640)
    val Muted = Color(0xFF3F3560)
    val Violet = Color(0xFF5438C4)
    val Rose = Color(0xFF8E2457)
    val Teal = Color(0xFF0A6B64)
    val Amber = Color(0xFF7A3A00)

    val MeAvatar = Brush.verticalGradient(listOf(Color(0xFF8A6DF2), Color(0xFF5438C4)))
    val HerAvatar = Brush.verticalGradient(listOf(Color(0xFFE86AA6), Color(0xFFB02F6E)))
    val Selected = Brush.verticalGradient(listOf(Color(0xF27C60F0), Color(0xF25438C4)))
    val Cta = Brush.linearGradient(listOf(Color(0xF57656EC), Color(0xF5A046BE), Color(0xF5BE3C82)))

    val Holo = listOf(
        Color(0xBFFFC4E6), Color(0x99DECEFF), Color(0x99BCE0FF), Color(0x8CD6FCEE), Color(0xB3FFEEC4),
        Color(0x99DECEFF), Color(0xBFFFC4E6),
    )
}

private val GlassFill = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.32f)),
)
private val GlassRim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.35f)))

/** 磨砂玻璃：半透明白、斜射高光、上亮下暗的边 */
fun Modifier.glass(shape: Shape = RoundedCornerShape(26.dp)): Modifier = this
    .clip(shape)
    .background(GlassFill)
    .border(1.dp, GlassRim, shape)

/** 小一号的玻璃，按钮、未选中的标签用 */
fun Modifier.glassControl(shape: Shape = RoundedCornerShape(50)): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.34f))))
    .border(1.dp, GlassRim, shape)

/** 染色玻璃：选中的标签 */
fun Modifier.tinted(shape: Shape = RoundedCornerShape(50), brush: Brush = Palette.Selected): Modifier = this
    .clip(shape)
    .background(brush)
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.15f))), shape)
    .drawWithContent {
        drawContent()
        // 顶上一道高光，底下一点暗，看起来是凸起的
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.10f)))
    }

/** 镭射卡片：彩虹慢慢流动，再有一道光扫过 */
@Composable
fun Modifier.holo(shape: Shape = RoundedCornerShape(30.dp)): Modifier {
    val t = rememberInfiniteTransition(label = "holo")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
    val sheen by t.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(7500, easing = LinearEasing)), label = "sheen")
    return this
        .clip(shape)
        .drawBehind {
            val w = size.width
            drawRect(Brush.linearGradient(Palette.Holo, start = Offset(-w * shift, 0f), end = Offset(w * (2f - shift), size.height)))
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent), start = Offset.Zero, end = Offset(w * 0.55f, size.height * 0.7f)))
            val x = w * sheen
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                    start = Offset(x - w * 0.25f, 0f), end = Offset(x + w * 0.05f, size.height),
                ),
            )
        }
        .border(1.dp, GlassRim, shape)
}

/** 按下去像果冻一样压扁，松手弹回 */
@Composable
fun Modifier.jelly(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sx by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.38f, stiffness = 600f), label = "sx")
    val sy by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.38f, stiffness = 600f), label = "sy")
    return this
        .graphicsLayer { scaleX = sx; scaleY = sy }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** 背景：几团淡彩光慢慢漂，还有一层白色水波纹在流 */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(17000), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(23000), RepeatMode.Reverse), label = "b")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "wave")
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFFE4D6FF), Color(0xFFF6D2EA), Color(0xFFD7E0FF), Color(0xFFCBEEF4)),
                start = Offset.Zero, end = Offset(w * 0.6f, h),
            ),
        )
        blob(Color(0xFFFF7ABE), Offset(w * (0.05f + 0.1f * a), h * (0.1f - 0.03f * b)), w * 0.8f)
        blob(Color(0xFF6EA0FF), Offset(w * (0.95f - 0.08f * b), h * (0.32f + 0.04f * a)), w * 0.85f)
        blob(Color(0xFFA06EFF), Offset(w * (0.1f + 0.08f * b), h * (0.62f - 0.03f * a)), w * 0.75f)
        blob(Color(0xFF64E1CD), Offset(w * (0.6f - 0.1f * a), h * (1.02f - 0.03f * b)), w * 0.7f)

        val unit = 100.dp.toPx()
        val amp = 14.dp.toPx()
        val stroke = Stroke(width = 1.3.dp.toPx())
        val shift = wave * unit * 2
        var y = 40.dp.toPx()
        while (y < h) {
            val path = Path()
            var x = -unit * 2 + shift
            path.moveTo(x, y)
            while (x < w + unit) {
                path.quadraticTo(x + unit / 2, y - amp, x + unit, y)
                path.quadraticTo(x + unit * 1.5f, y + amp, x + unit * 2, y)
                x += unit * 2
            }
            drawPath(path, Color.White.copy(alpha = 0.32f), style = stroke)
            y += 70.dp.toPx()
        }
    }
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float) {
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = 0.42f), color.copy(alpha = 0f)), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}
