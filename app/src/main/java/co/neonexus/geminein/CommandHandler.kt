
package co.neonexus.geminein

import android.content.Context
import android.util.Log
import java.util.Calendar
import java.util.Locale

class CommandHandler(
    private val context: Context,
    private val updateStatus: (String) -> Unit,
    private val speak: (String, Boolean) -> Unit,
    private val cancelAssistant: () -> Unit,
    private val onCommandOutcome: (Boolean) -> Unit = {}
) {
    companion object {
        private const val TAG = "GemiNeinVoice"
    }

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

            is AssistantCommand.SetRecurringAlarm -> {
                handleSetRecurringAlarm(command)
                false
            }

            is AssistantCommand.NeedsTimerDuration -> {
                val prompt = "Okay, how long should I set the timer for?"
                updateStatus(prompt)
                speak(prompt, true)
                true
            }

            is AssistantCommand.NeedsAlarmTime -> {
                val prompt = "Okay, what time should I set the alarm for?"
                updateStatus(prompt)
                speak(prompt, true)
                true
            }

            is AssistantCommand.Cancel -> {
                Log.i(TAG, "Handling Cancel command")
                cancelAssistant()
                false
            }

            is AssistantCommand.Unknown -> {
                handleUnknown(spokenText)
                true
            }
        }
    }

    private fun handleSetTimer(command: AssistantCommand.SetTimer) {
        Log.i(TAG, "Handling SetTimer command")

        try {
            ClockActions.setTimer(context, command.seconds)
            onCommandOutcome(true)

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
            onCommandOutcome(false)
            updateStatus(e.message ?: "Couldn't set the timer.")
        }
    }

    private fun handleSetAlarm(command: AssistantCommand.SetAlarm) {
        Log.i(TAG, "Handling SetAlarm command")

        try {
            ClockActions.setAlarm(
                context,
                command.hour,
                command.minute
            )
            onCommandOutcome(true)

            val confirmation =
                "Alarm set for ${formatTime(command.hour, command.minute)}."

            updateStatus(confirmation)
            speak(confirmation, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm", e)
            onCommandOutcome(false)
            updateStatus(e.message ?: "Couldn't set the alarm.")
        }
    }

    private fun handleSetRecurringAlarm(
        command: AssistantCommand.SetRecurringAlarm
    ) {
        Log.i(TAG, "Handling SetRecurringAlarm command")

        try {
            ClockActions.setRecurringAlarm(
                context,
                command.hour,
                command.minute,
                command.days
            )
            onCommandOutcome(true)

            val dayNames = command.days
                .sorted()
                .joinToString(", ") { dayName(it) }

            val confirmation =
                "Recurring alarm set for ${formatTime(command.hour, command.minute)} " +
                        "on $dayNames."

            updateStatus(confirmation)
            speak(confirmation, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set recurring alarm", e)
            onCommandOutcome(false)
            updateStatus(e.message ?: "Couldn't set the recurring alarm.")
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val hour12 = when (hour % 12) {
            0 -> 12
            else -> hour % 12
        }

        val period = if (hour < 12) "AM" else "PM"
        return String.format(
            Locale.ROOT,
            "%d:%02d %s",
            hour12,
            minute,
            period
        )
    }

    private fun dayName(day: Int): String {
        return when (day) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> "unknown day"
        }
    }

    private fun handleUnknown(spokenText: String) {
        Log.i(TAG, "Handling Unknown command")
        onCommandOutcome(false)

        updateStatus("I heard: \"$spokenText\"\n")

        speak(
            "Sorry, I didn't understand that.",
            true
        )
    }
}
