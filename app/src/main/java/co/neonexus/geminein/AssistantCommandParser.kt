
package co.neonexus.geminein

import android.util.Log
import java.util.Calendar
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
        val normalizedInput = input
            .replace(Regex("""\b([ap])\s*\.\s*m\s*\.?\b"""), "$1m")
            .replace(Regex("""\b([ap])\s*\.\s*m\b"""), "$1m")
            .replace(Regex("""\band\s+1\s*/\s*2\b"""), "and a half")
            .replace(Regex("""\band\s+one\s+half\b"""), "and a half")
            .replace(Regex("""\band\s+one\s+and\s+a\s+half\b"""), "and a half")
        val commandText = normalizedInput.trimEnd('.', '!', '?', ',')

        val cancelMatch = Regex(
            """(?:please\s+)?(?:cancel(?:\s+(?:that|it|this))?|never\s+mind|nevermind|abort)(?:\s+please)?"""
        )
        if (cancelMatch.matches(commandText)) {
            Log.i(TAG, "Cancellation command recognized")
            return AssistantCommand.Cancel
        }

        // Explicit countdown timers.
        val timerContext = Regex(
            """(?:set|start|create)\s+(?:a\s+)?timer\s+(?:for\s+)?(.+)"""
        ).matchEntire(commandText)

        if (timerContext != null) {
            val durationText = timerContext.groupValues[1].trim()
            val totalSeconds = parseTimerDurationSeconds(durationText)

            if (totalSeconds != null) {
                if (totalSeconds !in 1L..86400L) {
                    return AssistantCommand.Unknown(text)
                }

                return AssistantCommand.SetTimer(totalSeconds.toInt())
            }
        }

        // Relative alarms.
        val relativeAlarmContext = Regex(
            """(?:set|create)\s+(?:an?\s+)?alarm\s+(?:in\s+|for\s+)?(.+?)(?:\s+from\s+now)?"""
        ).matchEntire(commandText)
            ?: Regex(
                """wake\s+me(?:\s+up)?\s+(?:in\s+)?(.+?)(?:\s+from\s+now)?"""
            ).matchEntire(commandText)

        if (relativeAlarmContext != null) {
            val durationText = relativeAlarmContext.groupValues[1].trim()
            val totalMinutes = parseHourMinuteDuration(durationText)

            if (totalMinutes != null) {
                if (totalMinutes !in 1L..1439L) {
                    return AssistantCommand.Unknown(text)
                }

                val target = Calendar.getInstance().apply {
                    add(Calendar.MINUTE, totalMinutes.toInt())

                    if (get(Calendar.SECOND) > 0 ||
                        get(Calendar.MILLISECOND) > 0
                    ) {
                        add(Calendar.MINUTE, 1)
                    }

                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val hour = target.get(Calendar.HOUR_OF_DAY)
                val minute = target.get(Calendar.MINUTE)

                Log.i(
                    TAG,
                    "Relative alarm parse: input='$commandText', " +
                            "durationMinutes=$totalMinutes, target=$hour:$minute"
                )

                return AssistantCommand.SetAlarm(hour, minute)
            }
        }

        // Absolute alarms must match the whole command.
        val alarmMatch = Regex(
            """(?:set|create)\s+(?:an?\s+)?alarm\s+(?:for\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?"""
        ).matchEntire(commandText)

        if (alarmMatch != null) {
            var hour = alarmMatch.groupValues[1].toIntOrNull()
                ?: return AssistantCommand.Unknown(text)

            val minute = alarmMatch.groupValues[2]
                .takeIf { it.isNotEmpty() }
                ?.toIntOrNull() ?: 0

            val period = alarmMatch.groupValues[3]

            if (minute !in 0..59) return AssistantCommand.Unknown(text)

            if (period == "am" || period == "pm") {
                if (hour !in 1..12) return AssistantCommand.Unknown(text)

                hour %= 12
                if (period == "pm") hour += 12
            } else if (hour !in 0..23) {
                return AssistantCommand.Unknown(text)
            }

            Log.i(
                TAG,
                "Absolute alarm parse: input='$commandText', hour=$hour, minute=$minute"
            )

            return AssistantCommand.SetAlarm(hour, minute)
        }

        return AssistantCommand.Unknown(text)
    }

    private fun parseTimerDurationSeconds(durationText: String): Long? {
        val halfHour = Regex(
            """half\s+an?\s+hour|a\s+half\s+hour"""
        )
        if (halfHour.matches(durationText)) return 1800L

        val oneAndHalfHours = Regex(
            """(?:an?|one)\s+hour\s+and\s+a\s+half"""
        )
        if (oneAndHalfHours.matches(durationText)) return 5400L

        val hoursAndHalf = Regex(
            """(\d+)\s+and\s+a\s+half\s+hours?"""
        ).matchEntire(durationText)

        if (hoursAndHalf != null) {
            val hours = hoursAndHalf.groupValues[1].toLongOrNull()
                ?: return null
            return hours * 3600L + 1800L
        }

        val hoursAndMinutes = Regex(
            """(\d+)\s*(?:hours?|hrs?)\s*(?:(?:and\s*)?(\d+)\s*(?:minutes?|mins?))?"""
        ).matchEntire(durationText)

        if (hoursAndMinutes != null) {
            val hours = hoursAndMinutes.groupValues[1].toLongOrNull()
                ?: return null
            val minutesText = hoursAndMinutes.groupValues[2]

            if (minutesText.isNotEmpty()) {
                val minutes = minutesText.toLongOrNull() ?: return null
                return hours * 3600L + minutes * 60L
            }

            return hours * 3600L
        }

        val singleUnit = Regex(
            """(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)"""
        ).matchEntire(durationText) ?: return null

        val amount = singleUnit.groupValues[1].toLongOrNull() ?: return null
        val unit = singleUnit.groupValues[2]

        val multiplier = when {
            unit.startsWith("hour") || unit.startsWith("hr") -> 3600L
            unit.startsWith("min") -> 60L
            else -> 1L
        }

        return amount * multiplier
    }

    private fun parseHourMinuteDuration(durationText: String): Long? {
        if (Regex("""half\s+an?\s+hour|a\s+half\s+hour""").matches(durationText)) {
            return 30L
        }

        if (Regex("""(?:an?|one)\s+hour\s+and\s+a\s+half""").matches(durationText)) {
            return 90L
        }

        val hoursAndHalf = Regex(
            """(\d+)\s+and\s+a\s+half\s+hours?"""
        ).matchEntire(durationText)

        if (hoursAndHalf != null) {
            val hours = hoursAndHalf.groupValues[1].toLongOrNull()
                ?: return null
            return hours * 60L + 30L
        }

        val hoursAndMinutes = Regex(
            """(\d+)\s*(?:hours?|hrs?)\s*(?:(?:and\s*)?(\d+)\s*(?:minutes?|mins?))?"""
        ).matchEntire(durationText)

        if (hoursAndMinutes != null) {
            val hours = hoursAndMinutes.groupValues[1].toLongOrNull()
                ?: return null
            val minutesText = hoursAndMinutes.groupValues[2]
            val minutes = if (minutesText.isNotEmpty()) {
                minutesText.toLongOrNull() ?: return null
            } else {
                0L
            }
            return hours * 60L + minutes
        }

        val singleUnit = Regex(
            """(\d+)\s*(minutes?|mins?|hours?|hrs?)"""
        ).matchEntire(durationText) ?: return null

        val amount = singleUnit.groupValues[1].toLongOrNull() ?: return null
        val unit = singleUnit.groupValues[2]

        return if (unit.startsWith("hour") || unit.startsWith("hr")) {
            amount * 60L
        } else {
            amount
        }
    }
}
