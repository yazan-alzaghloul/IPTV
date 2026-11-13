package com.iptv.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small native vector set keeps navigation distinct without adding an icon-font dependency. */
object CinemaIcons {
    val LiveTv = ImageVector.Builder("Live TV", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
            moveTo(8f, 3f); lineTo(12f, 7f); lineTo(16f, 3f)
            moveTo(5f, 7f); lineTo(19f, 7f); quadTo(21f, 7f, 21f, 9f); lineTo(21f, 18f); quadTo(21f, 20f, 19f, 20f)
            lineTo(5f, 20f); quadTo(3f, 20f, 3f, 18f); lineTo(3f, 9f); quadTo(3f, 7f, 5f, 7f); close()
        }
        path(fill = SolidColor(Color.Black)) { moveTo(10f, 10f); lineTo(16f, 13.5f); lineTo(10f, 17f); close() }
    }.build()
    val Movies = ImageVector.Builder("Movies", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f) {
            moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
            moveTo(8f, 4f); lineTo(8f, 20f); moveTo(16f, 4f); lineTo(16f, 20f)
            moveTo(4f, 9f); lineTo(8f, 9f); moveTo(4f, 15f); lineTo(8f, 15f)
            moveTo(16f, 9f); lineTo(20f, 9f); moveTo(16f, 15f); lineTo(20f, 15f)
        }
    }.build()
}
