
package co.neonexus.geminein

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.AnimationUtils
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
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
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 3.5f
    }

    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.4f
    }

    private val tertiaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.8f
    }

    private val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 13f
        maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
    }

    private val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 25f
        maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
    }

    private val wavePath = Path()
    private val secondaryPath = Path()
    private val tertiaryPath = Path()
    private val secondaryRibbonPath = Path()
    private val tertiaryRibbonPath = Path()

    private var targetAudioLevel = 0f
    private var smoothedAudioLevel = 0f
    private var state = State.IDLE
    private var phase = 0f
    private var lastFrameTime = 0L
    private var outcome = Outcome.NONE
    private var shapeBlend = 0f

    fun setAudioLevel(level: Float) {
        targetAudioLevel = level.coerceIn(0f, 1f)

        if (
            state == State.LISTENING &&
            targetAudioLevel > 0.15f &&
            smoothedAudioLevel < 0.12f
        ) {
            smoothedAudioLevel = 0.08f
        }

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

    // Repeatable pseudo-random values between 0 and 1.
    private fun pseudoRandom(seed: Int): Float {
        val value = sin(seed.toDouble() * 127.1 + 311.7) * 43758.5453
        return (value - floor(value)).toFloat()
    }

    // Square-mode inversion behavior: intentionally preserved.
    private fun inversion(
        progress: Float,
        time: Float,
        seed: Int
    ): Float {
        val regionCount = 11
        val regionPosition = progress * regionCount
        val region = floor(regionPosition).toInt()
        val localPosition = regionPosition - region

        val selected = pseudoRandom(region + seed) > 0.34f
        if (!selected) return 1f

        val direction = if (pseudoRandom(region + seed + 97) > 0.5f) {
            -1f
        } else {
            1f
        }

        val motion = sin(time * 1.6f + region * 1.91f + seed)
        val strength = (0.35f + motion * 0.65f).coerceIn(0f, 1f)

        val edgeDistance = minOf(localPosition, 1f - localPosition)
        val edgeBlend = (edgeDistance / 0.12f).coerceIn(0f, 1f)
        val smoothEdge = edgeBlend * edgeBlend * (3f - 2f * edgeBlend)

        return 1f + (direction * strength - 1f) * smoothEdge
    }

    // Sine-mode inversion: stable polarity inside each region, with
    // transitions confined to narrow edges instead of broad flat sections.
    private fun smoothSineInversion(
        progress: Float,
        seed: Int
    ): Float {
        val regionCount = 9
        val regionPosition = progress * regionCount
        val region = floor(regionPosition).toInt()
        val localPosition = regionPosition - region

        val selected = pseudoRandom(region + seed) > 0.32f
        val direction = if (
            selected && pseudoRandom(region + seed + 97) > 0.5f
        ) {
            -1f
        } else {
            1f
        }

        val edgeDistance = minOf(localPosition, 1f - localPosition)
        val edgeBlend = (edgeDistance / 0.10f).coerceIn(0f, 1f)
        val smoothEdge = edgeBlend * edgeBlend * (3f - 2f * edgeBlend)

        return 1f + (direction - 1f) * smoothEdge
    }

    // Independent smooth sine harmonics for the non-speaking states.
    private fun independentSineWave(
        progress: Float,
        time: Float,
        seed: Int
    ): Float {
        val frequency = 0.75f + pseudoRandom(seed + 3) * 0.65f
        val phaseOffset = pseudoRandom(seed + 9) * 2f * PI.toFloat()
        val motionOffset = pseudoRandom(seed + 17) * 1.8f

        val fundamental = sin(
            progress * (7f + frequency * 2f) * PI.toFloat() +
                    time * 0.85f + phaseOffset
        ) * 0.58f

        val harmonic = sin(
            progress * (13f + frequency * 3f) * PI.toFloat() -
                    time * 1.1f + phaseOffset * 1.4f
        ) * 0.27f

        val detail = sin(
            progress * (21f + frequency * 4f) * PI.toFloat() +
                    time * 0.65f + motionOffset
        ) * 0.15f

        return fundamental + harmonic + detail
    }

    // Independent square-wave generator: intentionally preserved.
    private fun independentWave(
        progress: Float,
        time: Float,
        seed: Int
    ): Float {
        val cycleCount = 3.2f + pseudoRandom(seed + 3) * 2.8f
        val cyclePosition = progress * cycleCount
        val cycleIndex = floor(cyclePosition).toInt()
        val localPosition = cyclePosition - cycleIndex

        val cycleVariation = 0.45f +
                pseudoRandom(cycleIndex + seed + 17) * 0.9f

        val polarity = if (
            sin(
                time * (0.65f + pseudoRandom(seed + 9)) +
                        cycleIndex * (1.1f + pseudoRandom(seed + 11) * 2f) +
                        pseudoRandom(cycleIndex + seed + 71) * 2f * PI
            ) >= 0f
        ) 1f else -1f

        val dutyCycle = 0.2f +
                pseudoRandom(cycleIndex + seed + 137) * 0.6f

        return if (localPosition < dutyCycle) {
            cycleVariation * polarity
        } else {
            -cycleVariation * polarity
        }
    }

    private fun shiftHue(color: Int, degrees: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)

        hsv[0] = (hsv[0] + degrees + 360f) % 360f

        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    private fun buildRibbon(
        path: Path,
        firstPoints: FloatArray,
        secondPoints: FloatArray,
        steps: Int
    ) {
        path.reset()

        for (i in 0..steps) {
            val x = firstPoints[i * 2]
            val y = firstPoints[i * 2 + 1]

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        for (i in steps downTo 0) {
            path.lineTo(
                secondPoints[i * 2],
                secondPoints[i * 2 + 1]
            )
        }

        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        // Use a centered 16:9 drawing region inside the full-screen view.
        // In portrait, the region becomes narrower and shorter.
        // In landscape, it can occupy the full view.
        val drawingWidth = width.toFloat()
        val drawingHeight = drawingWidth * (9f / 16f)
        val centerX = width / 2f
        val centerY = height / 2f

        val now = AnimationUtils.currentAnimationTimeMillis()

        val deltaSeconds = if (lastFrameTime == 0L) {
            0f
        } else {
            (now - lastFrameTime).coerceAtMost(50L) / 1000f
        }

        lastFrameTime = now

        val smoothing = if (targetAudioLevel > smoothedAudioLevel) 18f else 8f
        val audioBlend = 1f - exp(-smoothing * deltaSeconds)

        smoothedAudioLevel +=
            (targetAudioLevel - smoothedAudioLevel) * audioBlend

        val targetShapeBlend = if (state == State.SPEAKING) 1f else 0f
        val shapeTransition = 1f - exp(-8f * deltaSeconds)

        shapeBlend += (targetShapeBlend - shapeBlend) * shapeTransition

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
            State.SPEAKING -> 0.10f + (sin(phase * 1.8f) + 1f) * 0.05f
            State.PROCESSING -> 0.015f
            State.IDLE -> 0f
        }

        // Scale amplitude from the 16:9 region, not the full portrait height.
        val amplitude = drawingHeight * (idleAmplitude + levelContribution)
        val halfWidth = drawingWidth / 2f
        val steps = 240
        val squareCycles = 4f

        wavePath.reset()
        secondaryPath.reset()
        tertiaryPath.reset()

        val primaryPoints = FloatArray((steps + 1) * 2)
        val secondaryPoints = FloatArray((steps + 1) * 2)
        val tertiaryPoints = FloatArray((steps + 1) * 2)

        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            val x = centerX - halfWidth + progress * halfWidth * 2f

            val envelopeBase = sin(progress * PI).toFloat().coerceAtLeast(0f)
            val taperedEnvelope = envelopeBase * envelopeBase
            val envelope = taperedEnvelope * (1f - shapeBlend) + shapeBlend

            // Primary waveform remains unchanged.
            val cyclePosition = progress * squareCycles
            val cycleIndex = floor(cyclePosition).toInt()
            val localCyclePosition = cyclePosition - cycleIndex

            val cycleMotion = phase * 0.7f + cycleIndex * 1.37f
            val cycleVariation = 0.55f +
                    pseudoRandom(cycleIndex + 17) * 0.55f

            val polarity = if (
                sin(
                    cycleMotion +
                            pseudoRandom(cycleIndex + 71) * 2f * PI
                ) >= 0f
            ) 1f else -1f

            val dutyCycle = 0.35f +
                    pseudoRandom(cycleIndex + 137) * 0.3f

            val primarySine = (
                    sin(progress * 9f * PI + phase) * 0.62f +
                            sin(progress * 17f * PI - phase * 1.25f) * 0.27f +
                            sin(progress * 27f * PI + phase * 0.65f) * 0.11f
                    ).toFloat()

            val primarySquare = if (localCyclePosition < dutyCycle) {
                cycleVariation * polarity
            } else {
                -cycleVariation * polarity
            }

            val primaryWave =
                primarySine * (1f - shapeBlend) +
                        primarySquare * shapeBlend

            val primaryDisplacement = primaryWave * amplitude * envelope
            val primaryY = centerY + primaryDisplacement

            // Sine mode: independent smooth curves with stronger divergence.
            val secondarySine = independentSineWave(
                progress = progress,
                time = phase,
                seed = 137
            )

            val tertiarySine = independentSineWave(
                progress = progress,
                time = phase + 2.3f,
                seed = 419
            )

            val secondarySineY = centerY +
                    secondarySine * amplitude * 0.8f * envelope *
                    smoothSineInversion(progress, 13) -
                    drawingHeight * 0.025f

            val tertiarySineY = centerY +
                    tertiarySine * amplitude * 0.6f * envelope *
                    smoothSineInversion(progress, 41) +
                    drawingHeight * 0.025f

            // Square mode: retain the established independent shapes and inversions.
            val secondarySquare = independentWave(
                progress = progress,
                time = phase,
                seed = 137
            )

            val tertiarySquare = independentWave(
                progress = progress,
                time = phase + 2.3f,
                seed = 419
            )

            val secondarySquareAmplitude = amplitude *
                    (0.95f + pseudoRandom(137) * 0.45f)

            val tertiarySquareAmplitude = amplitude *
                    (0.95f + pseudoRandom(419) * 0.45f)

            val secondarySquareY = centerY +
                    secondarySquare * secondarySquareAmplitude * envelope *
                    inversion(progress, phase, 13) -
                    drawingHeight * 0.025f

            val tertiarySquareY = centerY +
                    tertiarySquare * tertiarySquareAmplitude * envelope *
                    inversion(progress, phase + 1.7f, 41) +
                    drawingHeight * 0.025f

            // Smoothly transition between sine and square personalities.
            val secondaryY = secondarySineY * (1f - shapeBlend) +
                    secondarySquareY * shapeBlend

            val tertiaryY = tertiarySineY * (1f - shapeBlend) +
                    tertiarySquareY * shapeBlend

            primaryPoints[i * 2] = x
            primaryPoints[i * 2 + 1] = primaryY

            secondaryPoints[i * 2] = x
            secondaryPoints[i * 2 + 1] = secondaryY

            tertiaryPoints[i * 2] = x
            tertiaryPoints[i * 2 + 1] = tertiaryY

            if (i == 0) {
                wavePath.moveTo(x, primaryY)
                secondaryPath.moveTo(x, secondaryY)
                tertiaryPath.moveTo(x, tertiaryY)
            } else {
                wavePath.lineTo(x, primaryY)
                secondaryPath.lineTo(x, secondaryY)
                tertiaryPath.lineTo(x, tertiaryY)
            }
        }

        buildRibbon(
            secondaryRibbonPath,
            primaryPoints,
            secondaryPoints,
            steps
        )

        buildRibbon(
            tertiaryRibbonPath,
            primaryPoints,
            tertiaryPoints,
            steps
        )

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

        val secondaryBaseColor = shiftHue(color, 18f)
        val tertiaryBaseColor = shiftHue(color, -22f)

        val secondaryColor =
            (secondaryBaseColor and 0x00FFFFFF) or 0xAA000000.toInt()

        val tertiaryColor =
            (tertiaryBaseColor and 0x00FFFFFF) or 0x66000000

        // First ribbon: 40% opacity.
        ribbonPaint.color =
            (secondaryBaseColor and 0x00FFFFFF) or 0x66000000
        canvas.drawPath(secondaryRibbonPath, ribbonPaint)

        // Second ribbon: 15% opacity.
        ribbonPaint.color =
            (tertiaryBaseColor and 0x00FFFFFF) or 0x26000000
        canvas.drawPath(tertiaryRibbonPath, ribbonPaint)

        bloomPaint.color = (color and 0x00FFFFFF) or 0x26000000
        canvas.drawPath(wavePath, bloomPaint)

        bloomPaint.color = (secondaryBaseColor and 0x00FFFFFF) or 0x22000000
        canvas.drawPath(secondaryPath, bloomPaint)

        bloomPaint.color = (tertiaryBaseColor and 0x00FFFFFF) or 0x1C000000
        canvas.drawPath(tertiaryPath, bloomPaint)

        glowPaint.color = (color and 0x00FFFFFF) or 0x66000000
        canvas.drawPath(wavePath, glowPaint)

        tertiaryPaint.color = tertiaryColor
        secondaryPaint.color = secondaryColor
        wavePaint.color = color

        canvas.drawPath(tertiaryPath, tertiaryPaint)
        canvas.drawPath(secondaryPath, secondaryPaint)
        canvas.drawPath(wavePath, wavePaint)

        if (isShown) {
            postInvalidateOnAnimation()
        }
    }
}
