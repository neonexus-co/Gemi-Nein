
package co.neonexus.geminein

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class AssistantSpotlightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val CENTER_COLOR = 0x2838D9FF
        private const val MID_COLOR = 0xE6000000.toInt()
        private const val EDGE_COLOR = 0xFF000000.toInt()
    }

    private val spotlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var spotlightShader: RadialGradient? = null

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        if (spotlightShader == null) {
            updateSpotlight()
        }

        spotlightPaint.shader = spotlightShader

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            spotlightPaint
        )
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateSpotlight()
    }

    private fun updateSpotlight() {
        if (width == 0 || height == 0) return

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = maxOf(width, height) * 0.68f

        spotlightShader = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(
                CENTER_COLOR,
                MID_COLOR,
                EDGE_COLOR
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )

        invalidate()
    }
}
