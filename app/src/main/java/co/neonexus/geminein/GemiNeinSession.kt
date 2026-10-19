
package co.neonexus.geminein

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class GemiNeinSession(context: android.content.Context) :
    VoiceInteractionSession(context) {

    companion object {
        private const val TAG = "GemiNeinVoice"
        private const val BLUR_RADIUS = 48
        private const val BACKDROP_ALPHA = 70
    }

    private var waveform: WaveformView? = null
    private var statusText: TextView? = null
    private var speechInput: SpeechInput? = null
    private var speechOutput: SpeechOutput? = null
    private var recognizedSpeechHandler: RecognizedSpeechHandler? = null

    private var sessionUnavailable = true
    private var pendingContinueListening = false

    override fun onCreateContentView(): View {
        configureBackdrop()

        val root = FrameLayout(context).apply {
            setBackgroundColor(
                Color.argb(BACKDROP_ALPHA, 12, 16, 24)
            )
        }

//        val spotlightView = AssistantSpotlightView(context)
//
//        root.addView(
//            spotlightView,
//            FrameLayout.LayoutParams(
//                FrameLayout.LayoutParams.MATCH_PARENT,
//                FrameLayout.LayoutParams.MATCH_PARENT
//            )
//        )

        val sessionWaveform = WaveformView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setState(WaveformView.State.IDLE)
        }
        waveform = sessionWaveform

        root.addView(
            sessionWaveform,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val sessionStatus = TextView(context).apply {
            text = "Gemi-nein"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }
        statusText = sessionStatus

        root.addView(
            sessionStatus,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        )

        return root
    }

    private fun configureBackdrop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.i(TAG, "Window blur unavailable below Android 12")
            return
        }

        val window = getWindow().window ?: return

        window.addFlags(
            WindowManager.LayoutParams.FLAG_BLUR_BEHIND or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND
        )

        val params = window.attributes
        params.blurBehindRadius = BLUR_RADIUS
        window.attributes = params
        window.setDimAmount(0.12f)

        Log.i(TAG, "Configured assistant backdrop blur")
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        sessionUnavailable = false
        Log.i(TAG, "Assistant overlay shown")
        startSpeechInput()
    }

    override fun onHide() {
        sessionUnavailable = true
        Log.i(TAG, "Assistant overlay hidden")
        stopSpeechInput()
        super.onHide()
    }

    override fun onDestroy() {
        sessionUnavailable = true
        Log.i(TAG, "Assistant session destroyed")
        stopSpeechInput()
        super.onDestroy()
    }

    private fun startSpeechInput() {
        if (sessionUnavailable || speechInput != null) {
            Log.i(TAG, "Skipping speech input start")
            return
        }

        val commandHandler = CommandHandler(
            context = context,
            updateStatus = ::updateOverlayStatus,
            speak = ::speakResponse,
            cancelAssistant = {
                Log.i(TAG, "Assistant cancellation requested")
                finishSession()
            },
            onCommandOutcome = { succeeded ->
                waveform?.post {
                    if (succeeded) {
                        waveform?.showCommandSuccess()
                    } else {
                        waveform?.showCommandFailure()
                    }
                }
            }
        )

        recognizedSpeechHandler = RecognizedSpeechHandler(
            isActivityUnavailable = { sessionUnavailable },
            updateStatus = ::updateOverlayStatus,
            setListenButtonEnabled = { enabled ->
                Log.d(TAG, "Speech input UI enabled: $enabled")
            },
            commandHandler = commandHandler
        )

        speechOutput = SpeechOutput(
            context = context,
            onSpeakingChanged = { isSpeaking ->
                statusText?.post {
                    if (sessionUnavailable) return@post

                    waveform?.setState(
                        if (isSpeaking) {
                            WaveformView.State.SPEAKING
                        } else {
                            WaveformView.State.IDLE
                        }
                    )
                }
            },
            onUtteranceFinished = { utteranceId ->
                statusText?.post {
                    handleSpeechFinished(utteranceId)
                }
            }
        )

        speechInput = SpeechInput(
            context = context,
            updateStatus = ::updateOverlayStatus,
            setListeningUiEnabled = { enabled ->
                Log.d(TAG, "Speech input UI enabled: $enabled")
            },
            onResult = { spokenText ->
                Log.i(TAG, "Overlay recognized speech")
                recognizedSpeechHandler?.handle(spokenText)
            },
            onAudioLevelChanged = { level ->
                waveform?.setAudioLevel(level)
            },
            onSpeechStarted = {
                waveform?.onSpeechStarted()
            },
            onSpeechTimeout = {
                Log.i(TAG, "Speech timed out; cancelling assistant interaction")
                finishSession()
            }
        )

        waveform?.clearCommandOutcome()
        waveform?.setState(WaveformView.State.LISTENING)
        statusText?.text = "Starting microphone..."
        speechInput?.startListening()
    }

    private fun updateOverlayStatus(message: String) {
        statusText?.post {
            if (sessionUnavailable) return@post

            statusText?.text = message

            waveform?.setState(
                when {
                    message.startsWith("Processing speech") ->
                        WaveformView.State.PROCESSING

                    message.startsWith("Listening") ||
                            message.startsWith("Ready") ||
                            message.startsWith("Starting microphone") ->
                        WaveformView.State.LISTENING

                    else -> WaveformView.State.IDLE
                }
            )
        }
    }

    private fun speakResponse(
        message: String,
        continueListening: Boolean
    ) {
        if (sessionUnavailable) return

        val output = speechOutput

        if (output?.isReady != true) {
            Log.w(TAG, "TTS not ready; showing response as text")
            updateOverlayStatus(message)

            if (continueListening) {
                restartListening()
            } else {
                finishSession()
            }
            return
        }

        pendingContinueListening = continueListening
        updateOverlayStatus(message)

        val utteranceId = if (continueListening) {
            SpeechOutput.UTTERANCE_CONTINUE
        } else {
            SpeechOutput.UTTERANCE_FINISH
        }

        val result = output.speak(message, utteranceId)
        Log.i(TAG, "TTS speak requested: result=$result")

        if (result == android.speech.tts.TextToSpeech.ERROR) {
            Log.e(TAG, "TTS rejected the utterance")
            handleSpeechFinished(utteranceId)
        }
    }

    private fun handleSpeechFinished(utteranceId: String?) {
        if (sessionUnavailable) return

        Log.i(TAG, "TTS finished: utteranceId=$utteranceId")

        if (utteranceId == SpeechOutput.UTTERANCE_CONTINUE &&
            pendingContinueListening
        ) {
            pendingContinueListening = false
            restartListening()
        } else {
            finishSession()
        }
    }

    private fun restartListening() {
        if (sessionUnavailable) return

        Log.i(TAG, "Restarting speech recognition")

        waveform?.clearCommandOutcome()
        waveform?.setState(WaveformView.State.LISTENING)

        statusText?.text = "Listening..."
        speechInput?.startListening()
    }

    private fun finishSession() {
        if (sessionUnavailable) return

        Log.i(TAG, "Finishing assistant interaction")
        sessionUnavailable = true
        stopSpeechInput()
        finish()
    }

    private fun stopSpeechInput() {
        speechInput?.let { input ->
            input.stopListening()
            input.destroy()
        }
        speechInput = null

        speechOutput?.shutdown()
        speechOutput = null

        recognizedSpeechHandler = null
        pendingContinueListening = false

        waveform?.setAudioLevel(0f)
        waveform?.setState(WaveformView.State.IDLE)
    }
}
