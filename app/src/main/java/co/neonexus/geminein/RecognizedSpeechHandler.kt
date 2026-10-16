
package co.neonexus.geminein

import android.util.Log

class RecognizedSpeechHandler(
    private val isActivityUnavailable: () -> Boolean,
    private val updateStatus: (String) -> Unit,
    private val setListenButtonEnabled: (Boolean) -> Unit,
    private val commandHandler: CommandHandler
) {
    companion object {
        private const val TAG = "GemiNeinVoice"
    }

    fun handle(spokenText: String) {
        if (isActivityUnavailable()) return

        Log.i(TAG, "Transcription received; parsing command")
        updateStatus("Heard: $spokenText")

        val command = AssistantCommandParser.parse(spokenText)
        val continueListeningAfterSpeech =
            commandHandler.handle(command, spokenText)

        setListenButtonEnabled(!continueListeningAfterSpeech)
    }
}
