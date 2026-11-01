package com.personal.carnet.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 玻璃做的字：每个字是一块立体的靛蓝玻璃。
 * 三层叠起来：底下一层淡影子（字浮在卡片上），中间是上浅下深、腰上一道亮线的玻璃体，
 * 最上面一圈白色的边，左上亮右下暗，像光打在玻璃棱上
 */
@Composable
fun GlassNumber(text: String, size: TextUnit = 44.sp, modifier: Modifier = Modifier) {
    val base = TextStyle(fontFamily = InterDisplay, fontSize = size, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em, fontFeatureSettings = "tnum")
    Box(modifier) {
        Text(text, style = base.copy(color = Color(0xFF2F2499).copy(alpha = 0.10f)), modifier = Modifier.offset(x = 0.dp, y = 3.dp), maxLines = 1)
        Text(
            text,
            style = base.copy(
                // 上面透一点、往下越来越深，像一块有厚度的靛蓝玻璃；不画硬亮线，免得像糖果
                brush = Brush.verticalGradient(
                    0f to Color(0xFF9C95FF),
                    0.35f to Color(0xFF6A5CF0),
                    1f to Color(0xFF2F2499),
                ),
            ),
            maxLines = 1,
        )
        Text(
            text,
            style = base.copy(
                brush = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.85f),
                    0.45f to Color.White.copy(alpha = 0.0f),
                    1f to Color.White.copy(alpha = 0.35f),
                ),
                drawStyle = Stroke(width = 1.6f),
            ),
            maxLines = 1,
        )
    }
}
