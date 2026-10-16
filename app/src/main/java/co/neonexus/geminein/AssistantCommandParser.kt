package co.neonexus.geminein

import java.util.Locale

sealed class AssistantCommand {
    data class SetTimer(val seconds: Int) : AssistantCommand()
    data class SetAlarm(val hour: Int, val minute: Int) : AssistantCommand()
    data class Unknown(val originalText: String) : AssistantCommand()
}

object AssistantCommandParser {

    fun parse(text: String): AssistantCommand {
        val input = text.lowercase(Locale.ROOT).trim()

        // Examples: "set a timer for 5 minutes"
        //           "set timer for 30 seconds"
        val timerMatch = Regex(
            """(?:set|start|create)\s+(?:a\s+)?timer\s+(?:for\s+)?(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)"""
        ).find(input)

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

        // Examples: "set an alarm for 7:30 AM"
        //           "set alarm for 18:45"
        val alarmMatch = Regex(
            """(?:set|create)\s+(?:an?\s+)?alarm\s+(?:for\s+)?(\d{1,2}):(\d{2})\s*(am|pm)?"""
        ).find(input)

        if (alarmMatch != null) {
            var hour = alarmMatch.groupValues[1].toInt()
            val minute = alarmMatch.groupValues[2].toInt()
            val period = alarmMatch.groupValues[3]

            if (minute !in 0..59 || hour !in 0..23) {
                return AssistantCommand.Unknown(text)
            }

            if (period == "am" || period == "pm") {
                if (hour !in 1..12) return AssistantCommand.Unknown(text)
                hour %= 12
                if (period == "pm") hour += 12
            }

            return AssistantCommand.SetAlarm(hour, minute)
        }

        return AssistantCommand.Unknown(text)
    }
}
