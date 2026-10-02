package io.pocketshare

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

/** Official MaterialShapes.Clover4Leaf outline; the full 48dp area stays tappable. */
class CloverButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle,
) : MaterialButton(context, attrs, defStyleAttr) {
    private val clover = Path()

    init {
        // Material still draws the themed fill, icon, focus and pressed ripple.
        // Clip them together, without the old round background cutting off leaves.
        cornerRadius = 0
        stateListAnimator = null
        elevation = 0f
        outlineProvider = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clover.reset()
        if (w <= 0 || h <= 0) return
        val cubics = OfficialCloverShape.polygon.cubics
        clover.moveTo(cubics.first().anchor0X, cubics.first().anchor0Y)
        cubics.forEach { cubic ->
            clover.cubicTo(
                cubic.control0X, cubic.control0Y,
                cubic.control1X, cubic.control1Y,
                cubic.anchor1X, cubic.anchor1Y,
            )
        }
        clover.close()
        val side = minOf(w, h).toFloat()
        val left = (w - side) / 2f
        val top = (h - side) / 2f
        clover.transform(Matrix().apply {
            setScale(side, side)
            postTranslate(left, top)
        })
    }

    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        try {
            canvas.clipPath(clover)
            super.draw(canvas)
        } finally {
            canvas.restoreToCount(saved)
        }
    }
}
