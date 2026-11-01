package com.personal.carnet.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 两颗水珠的颜色：我是靛蓝，她是玫粉，和头像一样 */
private val MeDrop = Color(0xFF6E62F2)
private val HerDrop = Color(0xFFF07AAE)

/**
 * 共用账本的两滴水珠：左边是我，右边是她。
 * - 谁多垫了钱，谁那颗就更饱满（[lean] 从 -1 到 1，正数是我多垫）
 * - 没两清时，中间一道细细的液体桥，几颗小光点顺着桥往多垫的那边流：那是要补回来的钱
 * - 两清了，两颗一样大、贴在一起融成一颗
 * 状态变了（比如刚点了结算），水珠会慢慢流过去，不是一下跳变
 */
@Composable
fun CoupleDroplets(
    lean: Float,
    settled: Boolean,
    me: String,
    her: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val shownLean by animateFloatAsState(if (settled) 0f else lean.coerceIn(-1f, 1f), tween(900), label = "lean")
    val merge by animateFloatAsState(if (settled) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 60f), label = "merge")
    val t = rememberInfiniteTransition(label = "flow")
    val flow by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "flow")
    val wobble by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "wobble")
    val measurer = rememberTextMeasurer()

    Box(modifier.fillMaxWidth().height(116.dp).semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(116.dp)) {
            val w = size.width
            val cy = size.height * 0.5f
            val base = size.height * 0.30f
            // 多垫的那颗大 35%，另一颗小 35%；水珠自己也在轻轻呼吸
            val breathe = 1f + 0.015f * sin(wobble)
            val rMe = base * (1f + 0.35f * shownLean) * breathe
            val rHer = base * (1f - 0.35f * shownLean) * (2f - breathe)
            // 没两清时两颗隔开；两清时往中间靠，叠在一起
            val gap = w * 0.30f * (1f - merge) + base * 0.9f * merge
            val xMe = w / 2 - gap / 2 - rMe * (1f - merge) * 0.2f
            val xHer = w / 2 + gap / 2 + rHer * (1f - merge) * 0.2f

            // 影子：水珠落在玻璃上的一点暗
            drawOval(Color(0xFF1B1E4A).copy(alpha = 0.07f), Offset(xMe - rMe * 0.8f, cy + rMe * 0.86f), Size(rMe * 1.6f, rMe * 0.28f))
            drawOval(Color(0xFF1B1E4A).copy(alpha = 0.07f), Offset(xHer - rHer * 0.8f, cy + rHer * 0.86f), Size(rHer * 1.6f, rHer * 0.28f))

            // 两颗水珠和中间的桥合成一整块液体：一起填色、一起描边，交界处没有缝
            val neck = base * (0.10f + 0.75f * merge)
            val body = Path().apply {
                op(
                    Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(xMe, cy), rMe)) },
                    Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(xHer, cy), rHer)) },
                    PathOperation.Union,
                )
            }
            body.op(body, bridgePath(xMe, rMe, xHer, rHer, cy, neck), PathOperation.Union)
            drawPath(body, Brush.horizontalGradient(listOf(MeDrop.copy(alpha = 0.55f), HerDrop.copy(alpha = 0.55f)), startX = xMe, endX = xHer))
            clipPath(body) {
                glow(Offset(xMe, cy), rMe)
                glow(Offset(xHer, cy), rHer)
            }
            drawPath(
                body,
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.95f), 0.45f to Color.White.copy(alpha = 0.15f), 1f to Color.White.copy(alpha = 0.75f),
                    start = Offset(xMe - rMe, cy - rMe), end = Offset(xHer + rHer, cy + rHer),
                ),
                style = Stroke(1.3.dp.toPx()),
            )
            sparkle(Offset(xMe, cy), rMe)
            sparkle(Offset(xHer, cy), rHer)

            // 要补的钱：小光点从少垫的那颗流向多垫的那颗
            if (!settled && abs(shownLean) > 0.02f) {
                val from = if (shownLean > 0) xHer - rHer * 0.6f else xMe + rMe * 0.6f
                val to = if (shownLean > 0) xMe + rMe * 0.6f else xHer - rHer * 0.6f
                for (i in 0 until 3) {
                    val p = (flow + i / 3f) % 1f
                    val x = from + (to - from) * p
                    val a = sin(p * Math.PI).toFloat()
                    drawCircle(Color.White.copy(alpha = 0.9f * a), 2.4.dp.toPx(), Offset(x, cy))
                }
            }

            label(measurer, me, Offset(xMe, cy), rMe)
            label(measurer, her, Offset(xHer, cy), rHer)
        }
    }
}

/** 两颗圆中间的「颈」：上下两条往里收的弧 */
private fun bridgePath(x1: Float, r1: Float, x2: Float, r2: Float, cy: Float, neck: Float): Path {
    val a = 0.9f // 从圆上大约 50° 的地方接出去
    val mid = (x1 + r1 + x2 - r2) / 2
    return Path().apply {
        moveTo(x1 + r1 * cos(a), cy - r1 * sin(a))
        quadraticTo(mid, cy - neck, x2 - r2 * cos(a), cy - r2 * sin(a))
        lineTo(x2 - r2 * cos(a), cy + r2 * sin(a))
        quadraticTo(mid, cy + neck, x1 + r1 * cos(a), cy + r1 * sin(a))
        close()
    }
}

/** 水珠里面：中心透亮、往边上越来越浓，像光穿过一颗水 */
private fun DrawScope.glow(c: Offset, r: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.6f),
            0.55f to Color.White.copy(alpha = 0.12f),
            1f to Color.Transparent,
            center = Offset(c.x - r * 0.2f, c.y - r * 0.25f), radius = r * 1.1f,
        ),
        r * 1.1f, Offset(c.x - r * 0.2f, c.y - r * 0.25f),
    )
}

/** 高光：左上一小片斜椭圆；右下沿一小段折射回来的亮边 */
private fun DrawScope.sparkle(c: Offset, r: Float) {
    drawOval(Color.White.copy(alpha = 0.85f), Offset(c.x - r * 0.6f, c.y - r * 0.66f), Size(r * 0.42f, r * 0.22f))
    drawArc(
        Color.White.copy(alpha = 0.7f), 15f, 45f, false,
        Offset(c.x - r * 0.86f, c.y - r * 0.86f), Size(r * 1.72f, r * 1.72f), style = Stroke(1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )
}

private fun DrawScope.label(measurer: androidx.compose.ui.text.TextMeasurer, text: String, c: Offset, r: Float) {
    val style = TextStyle(color = Color(0xFF14162B), fontWeight = FontWeight.SemiBold, fontSize = (r / density * 0.55f).coerceIn(12f, 22f).sp)
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
}
