package com.personal.carnet.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 和设计稿同一套 24×24 线条图标 */
object IconPaths {
    const val CLOSE = "M6 6l12 12M18 6L6 18"
    const val BACK = "M15 5l-7 7 7 7"
    const val PLUS = "M12 5v14M5 12h14"
    const val HOME = "M4 11l8-7 8 7v9H4zM10 20v-5h4v5"
    const val BOOK = "M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3zM5 17a3 3 0 0 1 3-3h11"
    const val CARD = "M5 6h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2zM3 10h18M7 15h3"
    const val SWAP = "M7 4l-3 3 3 3M4 7h13M17 20l3-3-3-3M20 17H7"
    const val DELETE = "M9 5h11v14H9l-6-7zM12 9l5 6M17 9l-5 6"
    const val CHECK = "M5 12l5 5 9-10"
    const val BANK = "M3 10l9-6 9 6M5 10v8M10 10v8M14 10v8M19 10v8M3 20h18"
    const val CASH = "M3 7h18v10H3zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM6 10v4M18 10v4"
    const val WALLET = "M4 7h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2zM4 7l11-3v3M16 13.5h1"
    const val SPARKLE = "M12 2c.7 6 3 8.3 9 9-6 .7-8.3 3-9 9-.7-6-3-8.3-9-9 6-.7 8.3-3 9-9z"
}

private val cache = HashMap<String, ImageVector>()

fun lineIcon(path: String, width: Float = 1.8f): ImageVector = cache.getOrPut("s$width:$path") {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(path),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}

fun fillIcon(path: String): ImageVector = cache.getOrPut("f:$path") {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
}

@Composable
fun LineIcon(path: String, tint: Color, size: Dp = 20.dp, width: Float = 1.8f, modifier: Modifier = Modifier) {
    Icon(lineIcon(path, width), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

@Composable
fun Sparkle(tint: Color, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    Icon(fillIcon(IconPaths.SPARKLE), contentDescription = null, tint = tint, modifier = modifier.size(size))
}
