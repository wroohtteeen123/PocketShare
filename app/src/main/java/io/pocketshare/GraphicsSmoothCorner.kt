package io.pocketshare

import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle

/** Radius-one AndroidX corner, using the reference design's 60% smoothing. */
internal object GraphicsSmoothCorner {
    const val SMOOTHING = 0.6f
    val cubics by lazy { create(SMOOTHING) }

    fun smoothingFor(radius: Float, available: Float): Float =
        if (radius <= 0f) 0f else (available / radius - 1f).coerceIn(0f, SMOOTHING)

    fun create(smoothing: Float) = RoundedPolygon.rectangle(
        width = 8f, height = 8f, centerX = 4f, centerY = 4f,
        rounding = CornerRounding(radius = 1f, smoothing = smoothing),
    ).cubics.filter { cubic ->
        val extent = 1f + smoothing + 0.00001f
        cubic.anchor0X <= extent && cubic.anchor0Y <= extent &&
            cubic.anchor1X <= extent && cubic.anchor1Y <= extent
    }
}
