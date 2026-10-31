package com.personal.carnet.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.personal.carnet.CarnetApp
import com.personal.carnet.MainActivity
import com.personal.carnet.R

/**
 * 桌面小组件，能横竖拖大小：
 * - 最小一条：还能花多少
 * - 中等：再加这个月花了、谁补谁
 * - 大的：再加接下来的固定收支和「记一笔」
 */
class CarnetWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CarnetApp
        val data = runCatching { WidgetData.from(app.repository.snapshotNow()) }.getOrNull()
        provideContent { Content(context, data) }
    }

    @Composable
    private fun Content(context: Context, data: WidgetData?) {
        val size = LocalSize.current
        val open = Intent(context, MainActivity::class.java)
        val add = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_ADD)
        Box(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).cornerRadius(24.dp)
                .clickable(actionStartActivity(open)).padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (data == null) {
                Text("打开 Carnet 看看", style = TextStyle(color = INK, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                return@Box
            }
            when {
                size.height < MEDIUM.height || size.width < MEDIUM.width -> Small(data)
                size.height < LARGE.height || size.width < LARGE.width -> Column(GlanceModifier.fillMaxSize()) {
                    Headline(data)
                    Spacer(GlanceModifier.defaultWeight())
                    Stats(data)
                }
                else -> Column(GlanceModifier.fillMaxSize()) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(GlanceModifier.defaultWeight()) { Headline(data) }
                        Box(
                            GlanceModifier.background(ImageProvider(R.drawable.widget_cta)).cornerRadius(14.dp)
                                .clickable(actionStartActivity(add)).padding(horizontal = 12.dp, vertical = 7.dp),
                        ) { Text("＋ 记一笔", style = TextStyle(color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold)) }
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    Stats(data)
                    Spacer(GlanceModifier.height(8.dp))
                    if (data.upcoming.isEmpty()) {
                        Text("接下来没有固定收支", style = TextStyle(color = MUTED, fontSize = 12.sp))
                    } else {
                        Text("接下来", style = TextStyle(color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        data.upcoming.forEach { (day, name, amount) ->
                            Row(GlanceModifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(day, style = TextStyle(color = VIOLET, fontSize = 12.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.width(42.dp))
                                Text(name, style = TextStyle(color = INK, fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                                Text(amount, style = TextStyle(color = INK, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Small(data: WidgetData) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text(data.headlineLabel.substringAfter(" "), style = TextStyle(color = MUTED, fontSize = 12.sp), maxLines = 1)
            Spacer(GlanceModifier.width(6.dp))
            Text(data.headline, style = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }

    @Composable
    private fun Headline(data: WidgetData) {
        Text(data.headlineLabel, style = TextStyle(color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        Text(data.headline, style = TextStyle(color = INK, fontSize = 26.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }

    @Composable
    private fun Stats(data: WidgetData) {
        Row(GlanceModifier.fillMaxWidth()) {
            Chip("这个月花了", data.monthSpent, GlanceModifier.defaultWeight())
            if (data.shared != null) {
                Spacer(GlanceModifier.width(6.dp))
                Chip("我们俩", data.shared, GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun Chip(label: String, value: String, modifier: GlanceModifier) {
        Column(modifier.background(ImageProvider(R.drawable.widget_chip)).cornerRadius(14.dp).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label, style = TextStyle(color = MUTED, fontSize = 11.sp), maxLines = 1)
            Text(value, style = TextStyle(color = INK, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 40.dp)
        val MEDIUM = DpSize(180.dp, 100.dp)
        val LARGE = DpSize(240.dp, 200.dp)
        private val INK = ColorProvider(Color(0xFF1F1640))
        private val MUTED = ColorProvider(Color(0xFF3F3560))
        private val VIOLET = ColorProvider(Color(0xFF5438C4))
        private val WHITE = ColorProvider(Color.White)
    }
}

class CarnetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CarnetWidget()
}
