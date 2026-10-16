
package co.neonexus.geminein

import android.content.Context
import android.util.Log

class CommandHandler(
    private val context: Context,
    private val updateStatus: (String) -> Unit,
    private val speak: (String, Boolean) -> Unit
) {
    companion object {
        private const val TAG = "GemiNeinVoice"
    }

    /**
     * Executes a parsed command.
     *
     * Returns true when the assistant should continue listening
     * after speaking its response.
     */
    fun handle(
        command: AssistantCommand,
        spokenText: String
    ): Boolean {
        return when (command) {
            is AssistantCommand.SetTimer -> {
                handleSetTimer(command)
                false
            }

            is AssistantCommand.SetAlarm -> {
                handleSetAlarm(command)
                false
            }

            is AssistantCommand.Unknown -> {
                handleUnknown(spokenText)
                true
            }
        }
    }

    private fun handleSetTimer(
        command: AssistantCommand.SetTimer
    ) {
        Log.i(TAG, "Handling SetTimer command")

        try {
            ClockActions.setTimer(context, command.seconds)

            val minutes = command.seconds / 60
            val seconds = command.seconds % 60

            val durationParts = buildList {
                if (minutes > 0) {
                    val unit = if (minutes == 1) "minute" else "minutes"
                    add("$minutes $unit")
                }

                if (seconds > 0) {
                    val unit = if (seconds == 1) "second" else "seconds"
                    add("$seconds $unit")
                }
            }

            val confirmation =
                "Timer set for ${durationParts.joinToString(" and ")}."

            updateStatus(confirmation)
            speak(confirmation, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set timer", e)
            updateStatus(e.message ?: "Couldn't set the timer.")
        }
    }

    private fun handleSetAlarm(
        command: AssistantCommand.SetAlarm
    ) {
        Log.i(TAG, "Handling SetAlarm command")

        try {
            ClockActions.setAlarm(
                context,
                command.hour,
                command.minute
            )

            val hour12 = when (command.hour % 12) {
                0 -> 12
                else -> command.hour % 12
            }

            val period = if (command.hour < 12) "AM" else "PM"

            val confirmation =
                "Alarm set for $hour12:%02d $period".format(command.minute)

            updateStatus(confirmation)
            speak(confirmation, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm", e)
            updateStatus(e.message ?: "Couldn't set the alarm.")
        }
    }

    private fun handleUnknown(spokenText: String) {
        Log.i(TAG, "Handling Unknown command")

        updateStatus(
            "I heard: \"$spokenText\"\n" +
                    "Say help for suggestions, or cancel to exit."
        )

        speak(
            "Sorry, I didn't understand that. " +
                    "Say help for suggestions, or cancel to exit.",
            true
        )
    }
}
