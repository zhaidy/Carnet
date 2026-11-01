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
import androidx.compose.material3.Typography
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.personal.carnet.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** 液态玻璃的配色：近黑的墨蓝字，一种靛蓝强调色；玻璃本身是主角，颜色只从背景透上来 */
object Palette {
    val Ink = Color(0xFF14162B)
    /** 次要文字：比正文浅一档，玻璃上对比度仍在 6:1 左右 */
    val Muted = Color(0xFF50546E)
    val Violet = Color(0xFF4436D6)
    val Rose = Color(0xFFB0285F)
    val Teal = Color(0xFF0B6E66)
    val Amber = Color(0xFF7A3A00)

    val MeAvatar = Brush.verticalGradient(listOf(Color(0xFF6E62F2), Color(0xFF4436D6)))
    val HerAvatar = Brush.verticalGradient(listOf(Color(0xFFF07AAE), Color(0xFFC0306E)))
    val Selected = Brush.verticalGradient(listOf(Color(0xE66A5CF4), Color(0xE64436D6)))
    val Cta = Brush.verticalGradient(listOf(Color(0xF26A5CF4), Color(0xF24436D6)))

    /** 图标格子的几种柔和底色（底色, 图标色），按图标挑一种，同一个分类永远是同一种颜色 */
    private val Tints = listOf(
        Color(0xFFE6E3FF) to Color(0xFF4436D6),
        Color(0xFFFFE0EE) to Color(0xFFB0285F),
        Color(0xFFD6F4F0) to Color(0xFF0B6E66),
        Color(0xFFFFEBD3) to Color(0xFF8A4A08),
        Color(0xFFDCE8FF) to Color(0xFF2F55B8),
        Color(0xFFF1E2FB) to Color(0xFF7B2F9A),
    )

    fun tintFor(key: String): Pair<Color, Color> = Tints[Math.floorMod(key.hashCode(), Tints.size)]
}

/** 数字和英文：Inter（最接近 iOS 的 SF），中文落到系统黑体；大号数字用 Inter Display，字形收得更紧 */
val Inter = FontFamily(
    Font(R.font.carnet_inter_regular, FontWeight.Normal),
    Font(R.font.carnet_inter_medium, FontWeight.Medium),
    Font(R.font.carnet_inter_semibold, FontWeight.SemiBold),
    Font(R.font.carnet_inter_bold, FontWeight.SemiBold),
)
val InterDisplay = FontFamily(Font(R.font.carnet_inter_display_semibold, FontWeight.SemiBold))

/**
 * 全局字体：行高跟着字号走（Material 默认的 24sp 固定行高放到 12、13sp 的说明文字上，行距会空得很开），
 * 数字用等宽（tnum），金额上下行能对齐、变化时不跳
 */
val CarnetTypography: Typography = Typography().let { t ->
    t.copy(bodyLarge = t.bodyLarge.copy(fontFamily = Inter, lineHeight = 1.45.em, letterSpacing = 0.sp, fontFeatureSettings = "tnum"))
}

/** 背景现在是什么颜色（杯子多满），玻璃要按同一个颜色去「折射」 */
val LocalMood = staticCompositionLocalOf { 0.6f }

/**
 * 画背景：几团柔和的颜色。[screen] 是整个窗口的大小，[origin] 是要画的这块在窗口里的位置，
 * 背景自己画的时候 origin 是 0；玻璃里重画一份时传自己的位置，画出来的就是它正后面那一块
 */
fun DrawScope.drawBackdrop(mood: Float, screen: Size, origin: Offset = Offset.Zero, boost: Float = 1f) {
    val m = mood.coerceIn(0f, 1f)
    val w = screen.width
    val h = screen.height
    // 三段：见底（0）是珊瑚橘，一半是紫粉，满（1）是蓝绿；中间不直接混冷暖，免得混成灰
    fun mix(warm: Long, mid: Long, cool: Long): Color =
        if (m < 0.5f) androidx.compose.ui.graphics.lerp(Color(warm), Color(mid), m * 2)
        else androidx.compose.ui.graphics.lerp(Color(mid), Color(cool), (m - 0.5f) * 2)
    translate(-origin.x, -origin.y) {
        drawRect(
            Brush.verticalGradient(listOf(mix(0xFFFDF0EA, 0xFFF3F0FA, 0xFFEEF4F8), mix(0xFFF8E3DD, 0xFFE9E7F4, 0xFFE4ECF4)), startY = 0f, endY = h),
            topLeft = Offset.Zero, size = screen,
        )
        blob(mix(0xFFFF8A6B, 0xFF7C6CFF, 0xFF5C9DFF), Offset(w * 0.05f, h * 0.12f), w * 0.95f, boost)
        blob(mix(0xFFFFB84D, 0xFFFF8DC2, 0xFF4FD6C2), Offset(w * 1.0f, h * 0.28f), w * 0.8f, boost)
        blob(mix(0xFFFF6F9E, 0xFF5CC8F0, 0xFF7C6CFF), Offset(w * 0.12f, h * 0.62f), w * 0.85f, boost)
        blob(mix(0xFFFFA07A, 0xFFA48BFF, 0xFF5CC8F0), Offset(w * 0.92f, h * 0.9f), w * 0.8f, boost)
    }
}

/**
 * 真一点的玻璃：
 * - 透镜：玻璃后面那一块背景被放大一点、颜色更饱和（[lensScale]、[boost]），像隔着一块厚玻璃看
 * - 磨砂：上亮下淡的一层白（[frost]）
 * - 厚度：边上一圈内发光，左上亮、右下带一点背景的颜色，像光在玻璃边里折了一下
 * - 边：一道左上亮的白边，外面再一道很细的暗线，把玻璃和背景分开
 * - 投影：下面一层很淡、散开的影子，玻璃浮在背景上
 */
private fun Modifier.realGlass(shape: Shape, lensScale: Float, boost: Float, frost: Float, rimWidth: Dp, shadow: Boolean): Modifier = composed {
    val mood = LocalMood.current
    val window = LocalWindowInfo.current.containerSize
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            val screen = Size(window.width.toFloat().coerceAtLeast(size.width), window.height.toFloat().coerceAtLeast(size.height))
            val glow = 12.dp.toPx()
            onDrawBehind {
                if (shadow) {
                    translate(0f, 8.dp.toPx()) {
                        for (k in 1..4) drawPath(path, Color(0xFF1B1E4A).copy(alpha = 0.022f), style = Stroke(width = k * 6.dp.toPx()))
                    }
                }
                clipPath(path) {
                    // 透镜：以玻璃中心放大背景
                    val c = Offset(size.width / 2, size.height / 2)
                    scale(lensScale, lensScale, pivot = c) { drawBackdrop(mood, screen, origin, boost) }
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = frost + 0.12f), Color.White.copy(alpha = frost - 0.06f))))
                    // 厚度：边上一圈内发光。一层层叠细线，越往里越淡，不留硬边
                    val light = Brush.linearGradient(
                        0f to Color.White, 0.4f to Color.White.copy(alpha = 0.25f), 0.75f to Color.Transparent,
                        1f to Color(0xFF6A5CF4).copy(alpha = 0.5f),
                        start = Offset.Zero, end = Offset(size.width, size.height),
                    )
                    val layers = 10
                    for (i in 1..layers) {
                        drawPath(path, light, alpha = 0.045f, style = Stroke(width = glow * 2 * i / layers))
                    }
                }
                drawPath(path, Color(0xFF1B1E4A).copy(alpha = 0.09f), style = Stroke(width = 0.6.dp.toPx()))
                drawPath(
                    path,
                    Brush.linearGradient(
                        0f to Color.White, 0.3f to Color.White.copy(alpha = 0.5f), 0.55f to Color.White.copy(alpha = 0.05f),
                        0.85f to Color.White.copy(alpha = 0.25f), 1f to Color.White.copy(alpha = 0.8f),
                        start = Offset.Zero, end = Offset(size.width, size.height),
                    ),
                    style = Stroke(width = rimWidth.toPx()),
                )
            }
        }
        .clip(shape)
}

/** 液态玻璃：后面的背景透过来、被放大一点，边上有厚度和高光 */
fun Modifier.glass(shape: Shape = RoundedCornerShape(26.dp)): Modifier =
    realGlass(shape, lensScale = 1.12f, boost = 1.35f, frost = 0.30f, rimWidth = 1.3.dp, shadow = true)

/** 小一号的玻璃，按钮、未选中的标签用：磨砂多一点，不加投影 */
fun Modifier.glassControl(shape: Shape = RoundedCornerShape(50)): Modifier =
    realGlass(shape, lensScale = 1.06f, boost = 1.2f, frost = 0.42f, rimWidth = 1.dp, shadow = false)

/** 染色玻璃：选中的标签、主按钮 */
fun Modifier.tinted(shape: Shape = RoundedCornerShape(50), brush: Brush = Palette.Selected): Modifier = this
    .clip(shape)
    .background(brush)
    .drawWithContent {
        drawContent()
        // 顶上一道高光，像水珠鼓起来
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.32f), 0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.08f)))
    }
    .border(1.dp, Brush.linearGradient(0f to Color.White.copy(alpha = 0.75f), 0.5f to Color.White.copy(alpha = 0.1f), 1f to Color.White.copy(alpha = 0.45f)), shape)

/** 厚玻璃：每页最上面那块总数用，放大得更多、更通透，边更亮 */
@Composable
fun Modifier.holo(shape: Shape = RoundedCornerShape(30.dp)): Modifier =
    realGlass(shape, lensScale = 1.22f, boost = 1.55f, frost = 0.26f, rimWidth = 1.6.dp, shadow = true)

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

/**
 * 背景跟着钱变色：[mood] 是杯子有多满（0 到 1）。
 * 刚发薪、钱多的时候是清爽的蓝绿；越接近发薪、杯子越浅，慢慢变暖成珊瑚橘。
 * 不用看数字，打开 App 第一眼就知道这个月宽不宽裕
 */
@Composable
fun LiquidBackground(mood: Float = LocalMood.current, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) { drawBackdrop(mood, size) }
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float, boost: Float = 1f) {
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = (0.42f * boost).coerceAtMost(1f)), color.copy(alpha = 0.12f * boost), color.copy(alpha = 0f)), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}
