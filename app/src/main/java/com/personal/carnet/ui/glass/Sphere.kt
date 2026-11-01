package com.personal.carnet.ui.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 一颗玻璃水珠，气泡、日历上的水珠都用它，长得一样：
 * 身体是很淡的颜色（[left] 到 [right] 横着过渡，共用账本就是蓝粉两色），
 * 光从左上来，中间透亮、边上浓一点；左上一小片高光，右下沿一道折射回来的亮边；底下一点影子
 */
fun DrawScope.glassSphere(c: Offset, r: Float, left: Color, right: Color = left, shadow: Boolean = true) {
    if (shadow) {
        drawOval(Color(0xFF1B1E4A).copy(alpha = 0.06f), Offset(c.x - r * 0.7f, c.y + r * 0.82f), Size(r * 1.4f, r * 0.3f))
    }
    drawCircle(Brush.horizontalGradient(listOf(left.copy(alpha = 0.42f), right.copy(alpha = 0.42f)), startX = c.x - r, endX = c.x + r), r, c)
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.7f), 0.55f to Color.White.copy(alpha = 0.12f), 1f to Color.Transparent,
            center = Offset(c.x - r * 0.25f, c.y - r * 0.3f), radius = r * 1.05f,
        ),
        r, c,
    )
    // 边上一圈更浓的颜色，水珠才有厚度
    drawCircle(
        Brush.radialGradient(0.72f to Color.Transparent, 1f to left.copy(alpha = 0.35f), center = c, radius = r),
        r, c,
    )
    drawCircle(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.95f), 0.5f to Color.White.copy(alpha = 0.1f), 1f to Color.White.copy(alpha = 0.6f),
            start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r),
        ),
        r - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()),
    )
    if (r > 6.dp.toPx()) {
        drawOval(Color.White.copy(alpha = 0.85f), Offset(c.x - r * 0.56f, c.y - r * 0.62f), Size(r * 0.38f, r * 0.2f))
        drawArc(
            Color.White.copy(alpha = 0.55f), 20f, 50f, false,
            Offset(c.x - r * 0.8f, c.y - r * 0.8f), Size(r * 1.6f, r * 1.6f),
            style = Stroke(1.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}
