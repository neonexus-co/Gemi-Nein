package com.example.gemi_nein

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var listenButton: Button
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private var assistantInvocation = false

    companion object {
        private const val MICROPHONE_REQUEST = 100
        const val EXTRA_START_LISTENING = "com.example.gemi_nein.START_LISTENING"
    }

    private fun finishAssistantInvocation() {
        if (assistantInvocation && !isFinishing) {
            finishAndRemoveTask()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assistantInvocation =
            intent.getBooleanExtra(EXTRA_START_LISTENING, false)

        if (assistantInvocation) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        listenButton = findViewById(R.id.listenButton)
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.getDefault())

                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED

                textToSpeech?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}

                        override fun onDone(utteranceId: String?) {
                            runOnUiThread {
                                finishAssistantInvocation()
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            runOnUiThread {
                                finishAssistantInvocation()
                            }
                        }

                        override fun onError(
                            utteranceId: String?,
                            errorCode: Int
                        ) {
                            runOnUiThread {
                                finishAssistantInvocation()
                            }
                        }
                    }
                )
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
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
            checkMicrophonePermission()
        }

        if (intent.getBooleanExtra(EXTRA_START_LISTENING, false)) {
            checkMicrophonePermission()
        }
    }

    private fun speak(message: String) {
        if (!ttsReady) {
            finishAssistantInvocation()
            return
        }

        textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "gemi-nein-utterance"
        )
    }
    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MICROPHONE_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == MICROPHONE_REQUEST) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startListening()
            } else {
                statusText.text = "Microphone permission is required."
            }
        }
    }

    private fun startListening() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            statusText.text = "On-device recognition requires Android 12 or newer."
            return
        }

        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            statusText.text =
                "On-device speech recognition isn't available. Check your speech services and language downloads."
            return
        }

        speechRecognizer?.destroy()

        speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                statusText.text = "Listening... Speak now."
            }

            override fun onBeginningOfSpeech() {
                statusText.text = "I hear you..."
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                statusText.text = "Processing speech..."
            }

            override fun onError(error: Int) {
                statusText.text = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        "I didn't catch that. Please try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "I didn't hear anything. Try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Microphone permission is missing."
                    else ->
                        "Speech recognition failed (code $error). Please try again."
                }
                listenButton.isEnabled = true
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )

                val spokenText = matches?.firstOrNull()

                if (spokenText == null) {
                    statusText.text = "Sorry, I couldn't understand that."
                    listenButton.isEnabled = true
                    return
                }

                statusText.text = "Heard: $spokenText"

                when (val command = AssistantCommandParser.parse(spokenText)) {
                    is AssistantCommand.SetTimer -> {
                        try {
                            ClockActions.setTimer(this@MainActivity, command.seconds)

                            val minutes = command.seconds / 60
                            val seconds = command.seconds % 60

                            val duration = buildList {
                                if (minutes > 0) {
                                    add("$minutes ${if (minutes == 1) "minute" else "minutes"}")
                                }
                                if (seconds > 0) {
                                    add("$seconds ${if (seconds == 1) "second" else "seconds"}")
                                }
                            }.joinToString(" and ")

                            val confirmation = "Timer set for $duration."
                            statusText.text = confirmation
                            speak(confirmation)
                        } catch (e: Exception) {
                            statusText.text = e.message ?: "Couldn't set the timer."
                        }
                    }

                    is AssistantCommand.SetAlarm -> {
                        try {
                            ClockActions.setAlarm(
                                this@MainActivity,
                                command.hour,
                                command.minute
                            )
                            val hour12 = when (command.hour % 12) {
                                0 -> 12
                                else -> command.hour % 12
                            }

                            val period = if (command.hour < 12) "AM" else "PM"

                            val confirmation = "Alarm set for $hour12:%02d $period".format(
                                command.minute
                            )

                            statusText.text = confirmation
                            speak(confirmation)
                        } catch (e: Exception) {
                            statusText.text = e.message ?: "Couldn't set the alarm."
                        }
                    }

                    is AssistantCommand.Unknown -> {
                        statusText.text =
                            "I heard: \"$spokenText\"\nTry a timer or alarm command."
                    }
                }

                listenButton.isEnabled = true
            }


            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault().toLanguageTag()
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        listenButton.isEnabled = false
        statusText.text = "Starting microphone..."

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            statusText.text = "Couldn't start listening: ${e.message}"
            listenButton.isEnabled = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        assistantInvocation =
            intent.getBooleanExtra(EXTRA_START_LISTENING, false)

        if (assistantInvocation) {
            // Stop any leftover speech from the previous invocation.
            textToSpeech?.stop()

            setShowWhenLocked(true)
            setTurnScreenOn(true)

            // Recheck permission and start a fresh listening cycle.
            checkMicrophonePermission()
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        super.onDestroy()
    }
}
