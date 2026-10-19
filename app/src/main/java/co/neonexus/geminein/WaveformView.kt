
package co.neonexus.geminein

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.AnimationUtils
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING
    }

    private enum class Outcome {
        NONE,
        SUCCESS,
        FAILURE
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.MITER
        strokeWidth = 3.5f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.MITER
        strokeWidth = 13f
        color = 0x663D7CFF
        maskFilter = BlurMaskFilter(
            16f,
            BlurMaskFilter.Blur.NORMAL
        )
    }

    private val wavePath = Path()

    private var targetAudioLevel = 0f
    private var smoothedAudioLevel = 0f
    private var state = State.IDLE
    private var phase = 0f
    private var lastFrameTime = 0L
    private var outcome = Outcome.NONE
    private var shapeBlend = 0f

    fun setAudioLevel(level: Float) {
        targetAudioLevel = level.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    fun setState(newState: State) {
        if (state == newState) return

        state = newState

        if (newState != State.LISTENING) {
            targetAudioLevel = 0f
        }

        lastFrameTime = 0L
        postInvalidateOnAnimation()
    }

    fun showCommandSuccess() {
        outcome = Outcome.SUCCESS
        postInvalidateOnAnimation()
    }

    fun showCommandFailure() {
        outcome = Outcome.FAILURE
        postInvalidateOnAnimation()
    }

    fun clearCommandOutcome() {
        outcome = Outcome.NONE
        postInvalidateOnAnimation()
    }

    // Compatibility wrapper for existing MainActivity calls.
    fun setListening(isListening: Boolean) {
        setState(if (isListening) State.LISTENING else State.IDLE)
    }

    fun onSpeechStarted() {
        if (state != State.LISTENING) return

        targetAudioLevel = maxOf(targetAudioLevel, 0.35f)
        smoothedAudioLevel = maxOf(smoothedAudioLevel, 0.12f)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        val centerX = width / 2f
        val centerY = height / 2f
        val now = AnimationUtils.currentAnimationTimeMillis()

        val deltaSeconds = if (lastFrameTime == 0L) {
            0f
        } else {
            ((now - lastFrameTime).coerceAtMost(50L)) / 1000f
        }

        lastFrameTime = now

        val smoothing = if (targetAudioLevel > smoothedAudioLevel) {
            10f
        } else {
            4f
        }

        val blend = 1f - exp(-smoothing * deltaSeconds)
        smoothedAudioLevel +=
            (targetAudioLevel - smoothedAudioLevel) * blend

        val targetShapeBlend =
            if (state == State.SPEAKING) 1f else 0f

        shapeBlend +=
            (targetShapeBlend - shapeBlend) *
                    (1f - exp(-8f * deltaSeconds))

        val phaseSpeed = when (state) {
            State.IDLE -> 0.85f
            State.LISTENING -> 3.2f
            State.PROCESSING -> 1.7f
            State.SPEAKING -> 4.8f
        }

        phase += deltaSeconds * phaseSpeed

        val idleAmplitude = when (state) {
            State.IDLE -> 0.018f
            State.LISTENING -> 0.035f
            State.PROCESSING -> 0.055f
            State.SPEAKING -> 0.075f
        }

        val levelContribution = when (state) {
            State.LISTENING -> smoothedAudioLevel * 0.75f
            State.SPEAKING ->
                0.10f + (sin(phase * 1.8f) + 1f) * 0.05f
            State.PROCESSING -> 0.015f
            State.IDLE -> 0f
        }

        val amplitude = height * (idleAmplitude + levelContribution)
        val halfWidth = width * 0.46f
        val steps = 180

        wavePath.reset()

        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            val x = centerX - halfWidth + progress * halfWidth * 2f

            val envelope = sin(progress * PI).toFloat()
                .coerceAtLeast(0f)
                .let { it * it }

            val sineWave = (
                    sin(progress * 9f * PI + phase) * 0.62f +
                            sin(progress * 17f * PI - phase * 1.25f) * 0.27f +
                            sin(progress * 27f * PI + phase * 0.65f) * 0.11f
                    ).toFloat()

            // Four cycles across the waveform, with flat plateaus
            // and abrupt vertical transitions.
            val squareCycles = 4f
            val cyclePosition =
                ((progress * squareCycles + phase / (2f * PI)) % 1f
                        + 1f) % 1f

            val squareWave = if (cyclePosition < 0.5f) 1f else -1f

            val combinedWave =
                sineWave * (1f - shapeBlend) +
                        squareWave * shapeBlend

            val y = centerY + combinedWave * amplitude * envelope

            if (i == 0) {
                wavePath.moveTo(x, y)
            } else {
                wavePath.lineTo(x, y)
            }
        }

        // Draw exact horizontal and vertical square-wave sections
        // when speaking, instead of relying on sampled sine points.
        if (state == State.SPEAKING && shapeBlend > 0.98f) {
            wavePath.reset()

            val cycles = 4
            val cycleWidth = halfWidth * 2f / cycles

            for (cycle in 0 until cycles) {
                val x0 = centerX - halfWidth + cycle * cycleWidth
                val x1 = x0 + cycleWidth * 0.5f
                val x2 = x0 + cycleWidth

                val p0 = (cycle.toFloat() / cycles)
                val p1 = ((cycle + 0.5f) / cycles)
                val p2 = ((cycle + 1f) / cycles)

                val y0 = centerY +
                        amplitude * sin(p0 * PI).toFloat().let { it * it }

                val y1 = centerY -
                        amplitude * sin(p1 * PI).toFloat().let { it * it }

                val y2 = centerY +
                        amplitude * sin(p2 * PI).toFloat().let { it * it }

                if (cycle == 0) {
                    wavePath.moveTo(x0, y0)
                }

                wavePath.lineTo(x1, y0)
                wavePath.lineTo(x1, y1)
                wavePath.lineTo(x2, y1)
                wavePath.lineTo(x2, y2)
            }
        }

        val color = when (outcome) {
            Outcome.SUCCESS -> 0xFF55E6A5.toInt()
            Outcome.FAILURE -> 0xFFFF5D73.toInt()
            Outcome.NONE -> when (state) {
                State.SPEAKING -> 0xFFB46CFF.toInt()
                State.LISTENING -> 0xFF38D9FF.toInt()
                State.PROCESSING -> 0xFF647CFF.toInt()
                State.IDLE -> 0xFF8C9BB5.toInt()
            }
        }

        wavePaint.shader = null
        wavePaint.color = color

        glowPaint.color = (color and 0x00FFFFFF) or 0x66000000

        canvas.drawPath(wavePath, glowPaint)
        canvas.drawPath(wavePath, wavePaint)

        if (isShown) {
            postInvalidateOnAnimation()
        }
    }
}
