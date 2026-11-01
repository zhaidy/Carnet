package com.personal.carnet.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.personal.carnet.data.AccountType
import com.personal.carnet.domain.AccountView
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.MainCurrency
import kotlin.math.sqrt

/** 欠信用卡的钱：暗红色，从试管顶上往下灌 */
private val Debt = listOf(Color(0xFFD9467F), Color(0xFF8E1F52))

/**
 * 一个账户的试管该灌多满（0 到 1）。所有账户用同一把尺子（钱最多的那个算满），
 * 欠款也一样，欠 S$900 在一排几万的账户里就只是一小截；按面积开方，钱少的账户也看得见一点
 */
fun tubeLevels(accounts: List<AccountView>, rate: FxRate): Map<Long, Float> {
    fun amount(v: AccountView): Long {
        val credit = v.account.type == AccountType.CREDIT
        val owedOrHave = if (credit) (-v.balanceCents).coerceAtLeast(0) else v.balanceCents.coerceAtLeast(0)
        return rate.convert(owedOrHave, v.account.currency, MainCurrency)
    }
    val max = accounts.maxOfOrNull(::amount)?.coerceAtLeast(1) ?: 1
    return accounts.associate { v ->
        val m = amount(v)
        v.account.id to (if (m <= 0) 0f else sqrt(m.toFloat() / max).coerceIn(0.08f, 1f))
    }
}

fun tubeColors(view: AccountView): List<Color> = when {
    view.account.type == AccountType.CREDIT -> Debt
    view.account.currency == Currency.CNY -> Liquid.Cny
    else -> Liquid.Sgd
}

/**
 * 一支玻璃试管：圆底，左边一道竖高光，右边几道刻度。
 * 存钱的账户液体从底往上；信用卡 [fromTop] 是欠款从顶往下灌，欠得越多越满
 */
@Composable
fun GlassTube(level: Float, colors: List<Color>, fromTop: Boolean, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(level.coerceIn(0f, 1f), tween(1100), label = "tube")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = w / 2
        val stroke = 1.2.dp.toPx()
        val body = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, CornerRadius(r, r)))
        }
        // 玻璃本体
        drawPath(body, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.4f))))
        clipPath(body) {
            val pad = 2.5.dp.toPx()
            val inner = h - pad * 2
            val liquidH = inner * shown
            if (liquidH > 0f) {
                val dip = (w * 0.18f).coerceAtMost(liquidH / 2)
                val brush = Brush.verticalGradient(colors.map { it.copy(alpha = 0.78f) })
                if (!fromTop) {
                    val top = h - pad - liquidH
                    // 液面是凹的弯月：贴着管壁的两边高一点
                    val p = Path().apply {
                        moveTo(0f, top - dip * 0.3f)
                        quadraticTo(w / 2, top + dip, w, top - dip * 0.3f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(p, brush)
                    drawPath(
                        Path().apply { moveTo(0f, top - dip * 0.3f); quadraticTo(w / 2, top + dip, w, top - dip * 0.3f) },
                        Color.White.copy(alpha = 0.8f), style = Stroke(1.2.dp.toPx()),
                    )
                } else {
                    // 欠款倒过来：从顶上往下，下沿是鼓出来的一滴
                    val bottom = pad + liquidH
                    val p = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(w, 0f)
                        lineTo(w, bottom - dip * 0.3f)
                        quadraticTo(w / 2, bottom + dip, 0f, bottom - dip * 0.3f)
                        close()
                    }
                    drawPath(p, Brush.verticalGradient(colors.reversed().map { it.copy(alpha = 0.78f) }))
                }
            }
            // 左边一道竖着的高光
            drawRoundRect(
                Color.White.copy(alpha = 0.7f),
                topLeft = Offset(w * 0.2f, h * 0.08f), size = Size(w * 0.14f, h * 0.7f),
                cornerRadius = CornerRadius(w, w),
            )
        }
        // 刻度：右边三短一长
        for (i in 1..4) {
            val y = h * (0.2f + 0.15f * i)
            val len = if (i == 2) w * 0.32f else w * 0.2f
            drawLine(Color.White.copy(alpha = 0.75f), Offset(w - len - w * 0.12f, y), Offset(w - w * 0.12f, y), 1.dp.toPx(), cap = StrokeCap.Round)
        }
        drawPath(
            body,
            Brush.linearGradient(
                0f to Color.White, 0.5f to Color.White.copy(alpha = 0.2f), 1f to Color.White.copy(alpha = 0.8f),
                start = Offset.Zero, end = Offset(w, h),
            ),
            style = Stroke(stroke),
        )
    }
}
