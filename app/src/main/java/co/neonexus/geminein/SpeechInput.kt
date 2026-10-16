
package co.neonexus.geminein

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class SpeechInput(
    private val context: Context,
    private val updateStatus: (String) -> Unit,
    private val setListeningUiEnabled: (Boolean) -> Unit,
    private val onResult: (String) -> Unit
) {
    companion object {
        private const val TAG = "GemiNeinVoice"
    }

    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening() {
        Log.i(
            TAG,
            "SpeechInput.startListening: " +
                    "recognizerExists=${speechRecognizer != null}"
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.e(TAG, "On-device recognition requires Android 12 or newer")
            updateStatus(
                "On-device recognition requires Android 12 or newer."
            )
            setListeningUiEnabled(true)
            return
        }

        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            Log.e(TAG, "On-device speech recognition is not available")
            updateStatus(
                "On-device speech recognition isn't available. " +
                        "Check your speech services and language downloads."
            )
            setListeningUiEnabled(true)
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer =
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)

            Log.i(
                TAG,
                "Created on-device SpeechRecognizer: " +
                        "instance=${System.identityHashCode(speechRecognizer)}"
            )
        }

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    Log.i(TAG, "RecognitionListener: onReadyForSpeech")
                    updateStatus("Listening... Speak now.")
                }

                override fun onBeginningOfSpeech() {
                    Log.i(TAG, "RecognitionListener: onBeginningOfSpeech")
                    updateStatus("I hear you...")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Reserved for future listening visualization.
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.i(TAG, "RecognitionListener: onEndOfSpeech")
                    updateStatus("Processing speech...")
                }

                override fun onError(error: Int) {
                    Log.e(
                        TAG,
                        "RecognitionListener: onError " +
                                "code=$error name=${recognitionErrorName(error)}"
                    )

                    updateStatus(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "I didn't catch that. Please try again."

                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "I didn't hear anything. Try again."

                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Microphone permission is missing."

                            else ->
                                "Speech recognition failed (code $error). " +
                                        "Please try again."
                        }
                    )

                    setListeningUiEnabled(true)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    Log.i(
                        TAG,
                        "RecognitionListener: onResults; " +
                                "candidateCount=${matches?.size ?: 0}"
                    )

                    val spokenText = matches?.firstOrNull()

                    if (spokenText == null) {
                        Log.w(TAG, "No usable transcription returned")
                        updateStatus(
                            "Sorry, I couldn't understand that."
                        )
                        setListeningUiEnabled(true)
                        return
                    }

                    Log.i(TAG, "Transcription received")
                    onResult(spokenText)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val count = partialResults
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.size ?: 0

                    Log.d(
                        TAG,
                        "RecognitionListener: onPartialResults; " +
                                "candidateCount=$count"
                    )
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    Log.d(
                        TAG,
                        "RecognitionListener: onEvent; " +
                                "eventType=$eventType"
                    )
                }
            }
        )

        val recognitionIntent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
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

        setListeningUiEnabled(false)
        updateStatus("Starting microphone...")

        try {
            Log.i(
                TAG,
                "Calling startListening(); " +
                        "instance=${System.identityHashCode(speechRecognizer)}"
            )

            speechRecognizer?.startListening(recognitionIntent)

            Log.i(TAG, "SpeechRecognizer.startListening() returned")
        } catch (e: Exception) {
            Log.e(TAG, "Exception thrown by startListening()", e)
            updateStatus("Couldn't start listening: ${e.message}")
            setListeningUiEnabled(true)
        }
    }

    fun destroy() {
        Log.i(TAG, "Destroying SpeechRecognizer")
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun recognitionErrorName(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "ERROR_SPEECH_TIMEOUT"
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                "ERROR_SERVER_DISCONNECTED"
            else -> "UNKNOWN_ERROR"
        }
    }
}
