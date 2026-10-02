package io.pocketshare

import android.content.Context
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.shape.CornerSize
import com.google.android.material.shape.CornerTreatment
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.shape.ShapePath
import com.google.android.material.textfield.TextInputLayout

/** Applies the reference radius and smoothing without shrinking short controls' radii. */
internal class ContinuousCornerTreatment : CornerTreatment() {
    private var cachedSmoothing = GraphicsSmoothCorner.SMOOTHING
    private var cachedCubics = GraphicsSmoothCorner.cubics

    override fun getCornerPath(path: ShapePath, angle: Float, interpolation: Float, bounds: RectF, size: CornerSize) {
        val available = minOf(bounds.width(), bounds.height()).coerceAtLeast(0f) / 2f
        val radius = size.getCornerSize(bounds).coerceIn(0f, available)
        // Reduce the extra smoothing span first, preserving the requested radius.
        appendCorner(path, angle, interpolation, radius, GraphicsSmoothCorner.smoothingFor(radius, available))
    }

    override fun getCornerPath(path: ShapePath, angle: Float, interpolation: Float, radius: Float) {
        appendCorner(path, angle, interpolation, radius.coerceAtLeast(0f), GraphicsSmoothCorner.SMOOTHING)
    }

    private fun appendCorner(path: ShapePath, angle: Float, interpolation: Float, radius: Float, smoothing: Float) {
        if (cachedSmoothing != smoothing) {
            cachedSmoothing = smoothing
            cachedCubics = GraphicsSmoothCorner.create(smoothing)
        }
        val r = radius * interpolation
        path.reset(0f, r * (1f + smoothing), 180f, 180f - angle)
        cachedCubics.forEach { cubic ->
            path.cubicToPoint(
                cubic.control0X * r, cubic.control0Y * r,
                cubic.control1X * r, cubic.control1Y * r,
                cubic.anchor1X * r, cubic.anchor1Y * r,
            )
        }
    }
}

internal object SmoothCorners {
    private fun model(original: ShapeAppearanceModel) = original.toBuilder()
        .setAllCorners(ContinuousCornerTreatment()).build()

    fun apply(view: View) {
        when (view) {
            is CloverButton -> return // Exact official Clover4Leaf is intentionally distinct.
            is MaterialButton -> view.shapeAppearanceModel = model(view.shapeAppearanceModel)
            is MaterialCardView -> view.shapeAppearanceModel = model(view.shapeAppearanceModel)
            is TextInputLayout -> view.shapeAppearanceModel = model(view.shapeAppearanceModel)
            is NavigationBarView -> view.itemActiveIndicatorShapeAppearance?.let {
                view.itemActiveIndicatorShapeAppearance = model(it)
            }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) apply(view.getChildAt(i))
    }

    fun apply(drawable: MaterialShapeDrawable) {
        drawable.shapeAppearanceModel = model(drawable.shapeAppearanceModel)
    }
}

internal class SmoothAlertDialogBuilder(context: Context) : MaterialAlertDialogBuilder(context) {
    init {
        (background as? MaterialShapeDrawable)?.let(SmoothCorners::apply)
    }
}
