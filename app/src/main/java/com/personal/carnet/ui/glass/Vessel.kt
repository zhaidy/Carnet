package com.personal.carnet.ui.glass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** 一个月工资，换成主货币；没设工资是 0 */
fun salaryMain(snapshot: com.personal.carnet.domain.Snapshot): Long =
    snapshot.rules.filter { it.active && com.personal.carnet.domain.Cashflow.isSalary(it) }
        .sumOf { snapshot.rate.convert(it.amountCents, it.currency, com.personal.carnet.domain.MainCurrency) }

/** 杯子有多满：发薪前还能花 ÷ 一个月工资（0 到 1）；没设工资时按六成算。背景的冷暖也跟着它 */
fun cupLevel(snapshot: com.personal.carnet.domain.Snapshot): Float {
    val spendable = snapshot.plan.spendableCents
    val salary = salaryMain(snapshot)
    return (if (spendable != null && salary > 0) spendable.toFloat() / salary else 0.6f).coerceIn(0f, 1f)
}

/** 两层液体的颜色：下层新币是靛蓝，上层人民币是玫粉；都很淡，压在上面的字照样看得清 */
object Liquid {
    val Sgd = listOf(Color(0xFFA9B6FF), Color(0xFF5D6BF2))
    val Cny = listOf(Color(0xFFFFD0DF), Color(0xFFFF93B8))
}

/**
 * 杯子里的钱：画在一块玻璃的下半截。
 * - [level]：液面高度占整块的比例（发薪前还能花 ÷ 工资），越接近发薪越低
 * - [split]：下层新币占多少，剩下的是人民币
 * 液面跟着手机倾斜，平时自己轻轻晃；液面上一道弯月高光，几颗气泡慢慢往上冒
 */
@Composable
fun MoneyVessel(level: Float, split: Float, modifier: Modifier = Modifier) {
    val tilt by rememberTilt()
    // 数字变了液面慢慢涨落，倾斜用弹簧跟上，有一点惯性
    val shownLevel by animateFloatAsState(level.coerceIn(0.06f, 0.94f), tween(1200), label = "level")
    val shownSplit by animateFloatAsState(split.coerceIn(0f, 1f), tween(1200), label = "split")
    val slope by animateFloatAsState(tilt, spring(dampingRatio = 0.35f, stiffness = 40f), label = "slope")
    val t = rememberInfiniteTransition(label = "liquid")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "phase")
    val rise by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "rise")

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val total = h * shownLevel
        val sgdTop = h - total * shownSplit
        val cnyTop = h - total
        val amp = 3.dp.toPx()
        // 倾斜：手机右边朝下时，液面要跟地面保持水平，在屏幕上就是右边高、左边低（最多差 18% 的杯宽）
        val tiltPx = -slope * w * 0.18f

        fun surface(top: Float, shift: Float): Path = Path().apply {
            val step = 6.dp.toPx()
            moveTo(0f, top + y(0f, w, tiltPx, amp, phase + shift))
            var x = step
            while (x <= w + step) {
                lineTo(x, top + y(x, w, tiltPx, amp, phase + shift))
                x += step
            }
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        // 两层各画各的，不叠在一起（叠了会混成灰紫）：新币在下面，人民币只占新币液面以上那一截
        val sgdPath = surface(sgdTop, 1.3f)
        if (shownSplit < 1f) {
            clipPath(sgdPath, androidx.compose.ui.graphics.ClipOp.Difference) {
                drawPath(surface(cnyTop, 0f), Brush.verticalGradient(Liquid.Cny.map { it.copy(alpha = 0.40f) }, startY = cnyTop, endY = sgdTop + amp * 3))
            }
            meniscus(cnyTop, w, tiltPx, amp, phase, 0.9f)
        }
        if (shownSplit > 0f) {
            drawPath(sgdPath, Brush.verticalGradient(Liquid.Sgd.map { it.copy(alpha = 0.42f) }, startY = sgdTop, endY = h))
            meniscus(sgdTop, w, tiltPx, amp, phase + 1.3f, 0.75f)
        }
        // 杯底一层更深的颜色，像光沉在水底
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Liquid.Sgd.last().copy(alpha = 0.18f)), startY = h - total * 0.35f, endY = h),
            topLeft = Offset(0f, h - total * 0.35f), size = androidx.compose.ui.geometry.Size(w, total * 0.35f),
        )

        // 气泡：从杯底往上冒，冒到液面就没了
        val bubbles = listOf(0.18f to 0f, 0.41f to 0.37f, 0.67f to 0.71f, 0.83f to 0.15f, 0.29f to 0.58f)
        bubbles.forEachIndexed { i, (fx, offset) ->
            val p = (rise + offset) % 1f
            val x = w * fx + 4.dp.toPx() * sin(p * 6f + i)
            val y = h - p * total
            val r = (1.6f + (i % 3) * 0.8f).dp.toPx()
            if (y > cnyTop + 6.dp.toPx()) {
                drawCircle(Color.White.copy(alpha = 0.55f * (1f - p)), r, Offset(x, y), style = Stroke(1.dp.toPx()))
            }
        }
    }
}

private fun y(x: Float, w: Float, tiltPx: Float, amp: Float, phase: Float): Float =
    tiltPx * (x / w - 0.5f) +
        amp * sin(x / w * 2f * PI.toFloat() * 1.3f + phase) +
        amp * 0.5f * sin(x / w * 2f * PI.toFloat() * 2.7f - phase * 1.4f)

/** 液面那道亮线：玻璃杯里液体贴着杯壁翘起来的那一圈光 */
private fun DrawScope.meniscus(top: Float, w: Float, tiltPx: Float, amp: Float, phase: Float, alpha: Float) {
    val path = Path()
    val step = 6.dp.toPx()
    path.moveTo(0f, top + y(0f, w, tiltPx, amp, phase))
    var x = step
    while (x <= w + step) {
        path.lineTo(x, top + y(x, w, tiltPx, amp, phase))
        x += step
    }
    drawPath(path, Color.White.copy(alpha = alpha), style = Stroke(1.6.dp.toPx()))
    // 液面下一道很浅的光带，像光穿过水面
    drawRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f * alpha), Color.Transparent), startY = top, endY = top + 14.dp.toPx()),
        topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(w, 14.dp.toPx()),
    )
}

/** 手机左右倾斜多少：-1（左边朝下）到 1（右边朝下）；没有重力传感器时一直是 0 */
@Composable
private fun rememberTilt(): State<Float> {
    val context = LocalContext.current
    val tilt = remember { mutableFloatStateOf(0f) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // 竖着拿时读数是「朝上」的方向；右边朝下时 x 读数是负的
                tilt.floatValue = (-event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (manager != null && sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager?.unregisterListener(listener) }
    }
    return tilt
}
