
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

    private enum class PendingQuestion {
        TIMER_DURATION,
        ALARM_TIME
    }

    private var pendingQuestion: PendingQuestion? = null

    fun handle(spokenText: String) {
        if (isActivityUnavailable()) return

        Log.i(TAG, "Transcription received; parsing command")
        updateStatus("Heard: $spokenText")

        val command = resolveCommand(spokenText)

        when (command) {
            is AssistantCommand.NeedsTimerDuration -> {
                pendingQuestion = PendingQuestion.TIMER_DURATION
            }

            is AssistantCommand.NeedsAlarmTime -> {
                pendingQuestion = PendingQuestion.ALARM_TIME
            }

            else -> Unit
        }

        val continueListeningAfterSpeech =
            commandHandler.handle(command, spokenText)

        setListenButtonEnabled(!continueListeningAfterSpeech)
    }

    private fun resolveCommand(spokenText: String): AssistantCommand {
        val pending = pendingQuestion
            ?: return AssistantCommandParser.parse(spokenText)

        val parsedCommand = AssistantCommandParser.parse(spokenText)

        // Allow cancellation or a new, complete command during a follow-up.
        if (
            parsedCommand !is AssistantCommand.Unknown
        ) {
            pendingQuestion = null
            return parsedCommand
        }

        val followUpCommand = when (pending) {
            PendingQuestion.TIMER_DURATION -> {
                AssistantCommandParser.parseTimerDurationAnswer(spokenText)
                    ?.let { AssistantCommand.SetTimer(it) }
            }

            PendingQuestion.ALARM_TIME -> {
                AssistantCommandParser.parseAlarmTimeAnswer(spokenText)
                    ?.let { AssistantCommand.SetAlarm(it.first, it.second) }
            }
        }

        if (followUpCommand != null) {
            pendingQuestion = null
            Log.i(TAG, "Follow-up answer recognized for $pending")
            return followUpCommand
        }

        Log.i(TAG, "Follow-up answer not understood; still waiting for $pending")
        return parsedCommand
    }
}
