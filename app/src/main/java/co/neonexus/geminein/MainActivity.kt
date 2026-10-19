package co.neonexus.geminein

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var listenButton: Button
    private lateinit var waveformView: WaveformView

    private var speechInput: SpeechInput? = null
    private var speechOutput: SpeechOutput? = null
    private lateinit var speechController: SpeechController
    private lateinit var commandHandler: CommandHandler
    private lateinit var recognizedSpeechHandler: RecognizedSpeechHandler
    private lateinit var microphonePermissionHandler: MicrophonePermissionHandler
    private lateinit var assistantSessionManager: AssistantSessionManager

    companion object {
        private const val TAG = "GemiNeinVoice"

        const val EXTRA_START_LISTENING =
            "co.neonexus.geminein.START_LISTENING"
    }

    private fun updateAssistantInvocation(isAssistantInvocation: Boolean) {
        if (isAssistantInvocation) {
            assistantSessionManager.startInvocation()
        } else {
            assistantSessionManager.updateInvocation(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "onCreate: activity created")

        assistantSessionManager = AssistantSessionManager(this)

        val isAssistantInvocation =
            intent.getBooleanExtra(EXTRA_START_LISTENING, false)

        updateAssistantInvocation(isAssistantInvocation)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        listenButton = findViewById(R.id.listenButton)
        waveformView = findViewById(R.id.waveformView)

        waveformView.setListening(false)

        microphonePermissionHandler = MicrophonePermissionHandler(
            activity = this,
            startListening = { startListening() },
        )

        speechOutput = SpeechOutput(
            context = this,
            onSpeakingChanged = { isSpeaking ->
                runOnUiThread {
                    if (isSpeaking) {
                        waveformView.setState(WaveformView.State.SPEAKING)
                    } else {
                        waveformView.clearCommandOutcome()
                        waveformView.setState(WaveformView.State.IDLE)
                    }
                }
            }
        ) { utteranceId ->
            runOnUiThread {
                assistantSessionManager.handleSpeechOutputFinished(
                    utteranceId = utteranceId,
                    startListening = { startListening() }
                )
            }
        }

        speechController = SpeechController(
            speechOutput = { speechOutput },
            assistantSessionManager = assistantSessionManager,
            startListening = { startListening() }
        )

        speechInput = SpeechInput(
            context = this,
            updateStatus = { message ->
                runOnUiThread {
                    statusText.text = message
                }
            },
            setListeningUiEnabled = { enabled ->
                runOnUiThread {
                    listenButton.isEnabled = enabled
                }
            },
            onResult = { spokenText ->
                runOnUiThread {
                    recognizedSpeechHandler.handle(spokenText)
                }
            },
            onAudioLevelChanged = { level ->
                runOnUiThread {
                    waveformView.setAudioLevel(level)
                }
            },
            onSpeechStarted = {
                runOnUiThread {
                    waveformView.onSpeechStarted()
                }
            }
        )

        commandHandler = CommandHandler(
            context = this,
            updateStatus = { message -> statusText.text = message },
            speak = { message, continueListening ->
                speechController.speak(message, continueListening)
            },
            cancelAssistant = {
                cancelAssistantInvocation()
            },
            onCommandOutcome = { succeeded ->
                runOnUiThread {
                    if (succeeded) {
                        waveformView.showCommandSuccess()
                    } else {
                        waveformView.showCommandFailure()
                    }
                }
            }
        )

        recognizedSpeechHandler = RecognizedSpeechHandler(
            isActivityUnavailable = {
                isFinishing ||
                        isDestroyed ||
                        assistantSessionManager.isCancellationRequested
            },
            updateStatus = { message ->
                statusText.text = message
            },
            setListenButtonEnabled = { enabled ->
                listenButton.isEnabled = enabled
            },
            commandHandler = commandHandler
        )

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.setPadding(
                bars.left + 24,
                bars.top + 24,
                bars.right + 24,
                bars.bottom + 24
            )

            insets
        }

        listenButton.setOnClickListener {
            Log.i(TAG, "Speak button pressed")
            microphonePermissionHandler.checkPermissionAndStartListening()
        }

        if (assistantSessionManager.isAssistantInvocation) {
            Log.i(TAG, "Starting microphone check for assistant invocation")
            microphonePermissionHandler.checkPermissionAndStartListening()
        }
    }


    private fun startListening() {
        if (
            isFinishing ||
            isDestroyed ||
            assistantSessionManager.isCancellationRequested
        ) {
            Log.w(TAG, "Skipping listening: activity unavailable or cancelled")
            waveformView.setListening(false)
            return
        }

        waveformView.clearCommandOutcome()
        waveformView.setListening(true)
        speechInput?.startListening()
    }


    private fun cancelAssistantInvocation() {
        if (assistantSessionManager.isCancellationRequested) {
            Log.i(TAG, "Cancellation already requested")
            return
        }

        Log.i(TAG, "Cancelling assistant interaction")

        // Set the guard before stopping audio, since stopping TTS may
        // trigger an utterance-completion callback.
        assistantSessionManager.requestCancellation()

        speechInput?.stopListening()
        speechOutput?.stop()
        waveformView.setListening(false)

        assistantSessionManager.finishCancelledInvocation()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        Log.i(TAG, "onNewIntent received")

        val isAssistantInvocation =
            intent.getBooleanExtra(EXTRA_START_LISTENING, false)

        updateAssistantInvocation(isAssistantInvocation)

        if (assistantSessionManager.isAssistantInvocation) {
            speechOutput?.stop()
            microphonePermissionHandler.checkPermissionAndStartListening()
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: cleaning up speech input and output")

        waveformView.setListening(false)

        speechInput?.destroy()
        speechInput = null

        speechOutput?.shutdown()
        speechOutput = null

        super.onDestroy()
    }
}
