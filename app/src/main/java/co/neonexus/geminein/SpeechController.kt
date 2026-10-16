
package co.neonexus.geminein

import android.util.Log

class SpeechController(
    private val speechOutput: () -> SpeechOutput?,
    private val assistantSessionManager: AssistantSessionManager,
    private val startListening: () -> Unit
) {
    companion object {
        private const val TAG = "GemiNeinVoice"
    }

    fun speak(
        message: String,
        continueListening: Boolean = false
    ) {
        val utteranceId = if (continueListening) {
            SpeechOutput.UTTERANCE_CONTINUE
        } else {
            SpeechOutput.UTTERANCE_FINISH
        }

        val output = speechOutput()

        Log.i(
            TAG,
            "speak requested: utteranceId=$utteranceId, " +
                    "ttsReady=${output?.isReady == true}"
        )

        if (output?.isReady != true) {
            Log.w(TAG, "TTS is not ready; handling fallback")

            if (continueListening) {
                startListening()
            } else {
                assistantSessionManager.finishInvocation()
            }

            return
        }

        val result = output.speak(message, utteranceId)
        Log.i(TAG, "TTS speak() returned: result=$result")
    }
}
