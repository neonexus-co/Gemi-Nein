package co.neonexus.geminein

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class SpeechOutput(
    context: Context,
    private val onSpeakingChanged: (Boolean) -> Unit = {},
    private val onUtteranceFinished: (String?) -> Unit
) {
    companion object {
        private const val TAG = "GemiNeinVoice"

        const val UTTERANCE_FINISH = "gemi-nein-finish"
        const val UTTERANCE_CONTINUE = "gemi-nein-continue"
    }

    private var textToSpeech: TextToSpeech? = null

    var isReady: Boolean = false
        private set

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            Log.i(TAG, "TTS initialization callback: status=$status")

            if (status == TextToSpeech.SUCCESS) {
                val languageResult =
                    textToSpeech?.setLanguage(Locale.getDefault())

                isReady =
                    languageResult != TextToSpeech.LANG_MISSING_DATA &&
                            languageResult != TextToSpeech.LANG_NOT_SUPPORTED

                Log.i(
                    TAG,
                    "TTS ready=$isReady, languageResult=$languageResult"
                )

                textToSpeech?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Log.i(
                                TAG,
                                "TTS onStart: utteranceId=$utteranceId"
                            )
                            onSpeakingChanged(true)
                        }

                        override fun onDone(utteranceId: String?) {
                            Log.i(
                                TAG,
                                "TTS onDone: utteranceId=$utteranceId"
                            )
                            onSpeakingChanged(false)
                            onUtteranceFinished(utteranceId)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            Log.e(
                                TAG,
                                "TTS onError: utteranceId=$utteranceId"
                            )
                            onSpeakingChanged(false)
                            onUtteranceFinished(utteranceId)
                        }

                        override fun onError(
                            utteranceId: String?,
                            errorCode: Int
                        ) {
                            Log.e(
                                TAG,
                                "TTS onError: utteranceId=$utteranceId, " +
                                        "errorCode=$errorCode"
                            )
                            onSpeakingChanged(false)
                            onUtteranceFinished(utteranceId)
                        }
                    }
                )
            } else {
                Log.e(TAG, "TTS initialization failed: status=$status")
            }
        }
    }

    fun speak(message: String, utteranceId: String): Int {
        return textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        ) ?: TextToSpeech.ERROR
    }

    fun stop() {
        textToSpeech?.stop()
        onSpeakingChanged(false)
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isReady = false
        onSpeakingChanged(false)
    }
}