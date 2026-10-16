
package co.neonexus.geminein

import android.util.Log
import java.util.Locale

sealed class AssistantCommand {
    data class SetTimer(val seconds: Int) : AssistantCommand()
    data class SetAlarm(val hour: Int, val minute: Int) : AssistantCommand()
    object Cancel : AssistantCommand()
    data class Unknown(val originalText: String) : AssistantCommand()
}

object AssistantCommandParser {

    private const val TAG = "GemiNeinVoice"

    fun parse(text: String): AssistantCommand {
        val input = text.lowercase(Locale.ROOT).trim()

        // Normalize common speech-recognition variations:
        // "p.m.", "p.m", "pm" -> "pm"
        // "a.m.", "a.m", "am" -> "am"
        val normalizedInput = input
            .replace(Regex("""\b([ap])\s*\.\s*m\s*\.?\b"""), "$1m")
            .replace(Regex("""\b([ap])\s*\.\s*m\b"""), "$1m")

        // Recognize explicit cancellation phrases without matching
        // commands such as "cancel the timer".
        val cancellationText = normalizedInput.trimEnd('.', '!', '?', ',')

        val cancelMatch = Regex(
            """(?:please\s+)?(?:cancel(?:\s+(?:that|it|this))?|never\s+mind|nevermind|abort)(?:\s+please)?"""
        )

        if (cancelMatch.matches(cancellationText)) {
            Log.i(TAG, "Cancellation command recognized")
            return AssistantCommand.Cancel
        }

        // Examples: "set a timer for 5 minutes"
        //           "set timer for 30 seconds"
        val timerMatch = Regex(
            """(?:set|start|create)\s+(?:a\s+)?timer\s+(?:for\s+)?(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)"""
        ).find(normalizedInput)

        if (timerMatch != null) {
            val amount = timerMatch.groupValues[1].toLongOrNull()
                ?: return AssistantCommand.Unknown(text)

            val unit = timerMatch.groupValues[2]
            val multiplier = when {
                unit.startsWith("hour") || unit.startsWith("hr") -> 3600L
                unit.startsWith("min") -> 60L
                else -> 1L
            }

            val seconds = amount * multiplier

            if (seconds in 1L..86400L) {
                return AssistantCommand.SetTimer(seconds.toInt())
            }

            return AssistantCommand.Unknown(text)
        }

        // Supports "set alarm for 7:30 AM", "set alarm for 7 PM",
        // and 24-hour times such as "set alarm for 18:45".
        val alarmMatch = Regex(
            """(?:set|create)\s+(?:an?\s+)?alarm\s+(?:for\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b"""
        ).find(normalizedInput)

        if (alarmMatch != null) {
            var hour = alarmMatch.groupValues[1].toIntOrNull()
                ?: return AssistantCommand.Unknown(text)

            val minute = alarmMatch.groupValues[2]
                .takeIf { it.isNotEmpty() }
                ?.toIntOrNull() ?: 0

            val period = alarmMatch.groupValues[3]

            Log.i(
                TAG,
                "Alarm parse: input='$input', normalized='$normalizedInput', " +
                        "match='${alarmMatch.value}', hour=$hour, " +
                        "minute=$minute, period='$period'"
            )

            if (minute !in 0..59) {
                return AssistantCommand.Unknown(text)
            }

            if (period == "am" || period == "pm") {
                if (hour !in 1..12) return AssistantCommand.Unknown(text)

                hour %= 12
                if (period == "pm") hour += 12
            } else if (hour !in 0..23) {
                return AssistantCommand.Unknown(text)
            }

            return AssistantCommand.SetAlarm(hour, minute)
        }

        return AssistantCommand.Unknown(text)
    }
}
