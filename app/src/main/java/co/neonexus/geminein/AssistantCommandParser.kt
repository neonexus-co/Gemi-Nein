package co.neonexus.geminein

import android.util.Log
import java.util.Calendar
import java.util.Locale

sealed class AssistantCommand {
    data class SetTimer(val seconds: Int) : AssistantCommand()
    data class SetAlarm(val hour: Int, val minute: Int) : AssistantCommand()
    data class SetRecurringAlarm(
        val hour: Int,
        val minute: Int,
        val days: List<Int>
    ) : AssistantCommand()

    object NeedsTimerDuration : AssistantCommand()
    object NeedsAlarmTime : AssistantCommand()
    object Cancel : AssistantCommand()
    data class Unknown(val originalText: String) : AssistantCommand()
}

object AssistantCommandParser {
    private const val TAG = "GemiNeinVoice"

    fun parse(text: String): AssistantCommand {
        val commandText = normalizeSpeechText(text)

        Log.d("GemiNeinParser", "Normalized command: '$commandText'")

        if (
            Regex(
                """(?:please\s+)?(?:cancel(?:\s+(?:that|it|this))?|never\s+mind|nevermind|abort)(?:\s+please)?"""
            ).matches(commandText)
        ) {
            Log.i(TAG, "Cancellation command recognized")
            return AssistantCommand.Cancel
        }

        if (
            Regex("""(?:set|start|create)\s+(?:a\s+)?timer""")
                .matches(commandText)
        ) {
            return AssistantCommand.NeedsTimerDuration
        }

        if (
            Regex("""(?:set|create)\s+(?:an?\s+)?alarm""")
                .matches(commandText)
        ) {
            return AssistantCommand.NeedsAlarmTime
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

        // Recurring alarms must be checked before ordinary absolute alarms.
        val recurringAlarmMatch = Regex(
            """(?:set|create)\s+(?:an?\s+)?alarm\s+(?:for\s+)?(.+?)\s+every\s+(.+)"""
        ).matchEntire(commandText)

        Log.d(
            "GemiNeinParser",
            "Recurring match: ${recurringAlarmMatch?.groupValues}"
        )

        if (recurringAlarmMatch != null) {
            val timeText = recurringAlarmMatch.groupValues[1]
                .trim()
                .trimEnd('.', '!', '?', ',')
            val recurrenceText = recurringAlarmMatch.groupValues[2].trim()
            val time = parseClockTime(timeText)
            val days = parseRecurringDays(recurrenceText)

            if (time != null && days != null) {
                Log.i(
                    TAG,
                    "Recurring alarm parse: time='$timeText', " +
                            "recurrence='$recurrenceText', days=$days"
                )

                return AssistantCommand.SetRecurringAlarm(
                    hour = time.first,
                    minute = time.second,
                    days = days
                )
            }

            return AssistantCommand.Unknown(text)
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

                    if (
                        get(Calendar.SECOND) > 0 ||
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
                    "Relative alarm parse: durationMinutes=$totalMinutes, " +
                            "target=$hour:$minute"
                )

                return AssistantCommand.SetAlarm(hour, minute)
            }
        }

        // Absolute alarms.
        val absoluteAlarmMatch = Regex(
            """(?:set|create)\s+(?:an?\s+)?alarm\s+(?:for\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?"""
        ).matchEntire(commandText)

        Log.d(
            "GemiNeinParser",
            "Absolute alarm match: ${absoluteAlarmMatch?.value}"
        )

        if (absoluteAlarmMatch != null) {
            val time = parseClockTime(
                absoluteAlarmMatch.groupValues[1] +
                        (absoluteAlarmMatch.groupValues[2].takeIf { it.isNotEmpty() }?.let { ":$it" } ?: "") +
                        (absoluteAlarmMatch.groupValues[3].takeIf { it.isNotEmpty() }?.let { " $it" } ?: "")
            )

            Log.d("GemiNeinParser", "Parsed clock time: $time")

            if (time == null) {
                return AssistantCommand.Unknown(text)
            }

            return AssistantCommand.SetAlarm(time.first, time.second)
        }

        return AssistantCommand.Unknown(text)
    }

    fun parseTimerDurationAnswer(text: String): Int? {
        val durationText = normalizeSpeechText(text)
        val seconds = parseTimerDurationSeconds(durationText) ?: return null

        return seconds
            .takeIf { it in 1L..86400L }
            ?.toInt()
    }

    fun parseAlarmTimeAnswer(text: String): Pair<Int, Int>? {
        val timeText = normalizeSpeechText(text)
            .removePrefix("at ")
            .removePrefix("for ")
            .trim()

        return parseClockTime(timeText)
    }

    private fun normalizeSpeechText(text: String): String {
        val input = text.lowercase(Locale.ROOT).trim()

        return input
            .replace(Regex("""\b([ap])\s*\.\s*m\s*\.?\b"""), "$1m")
            .replace(Regex("""\b([ap])\s*\.\s*m\b"""), "$1m")
            .replace(Regex("""\band\s+1\s*/\s*2\b"""), "and a half")
            .replace(Regex("""\band\s+one\s+half\b"""), "and a half")
            .replace(Regex("""\band\s+one\s+and\s+a\s+half\b"""), "and a half")
            .trim()
            .trimEnd('.', '!', '?', ',')
            .trim()
    }

    private fun parseClockTime(text: String): Pair<Int, Int>? {
        val match = Regex(
            """(\d{1,2})(?::(\d{2}))?\s*(am|pm)?"""
        ).matchEntire(text.trim()) ?: return null

        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2]
            .takeIf { it.isNotEmpty() }
            ?.toIntOrNull() ?: 0
        val period = match.groupValues[3]

        if (minute !in 0..59) return null

        if (period == "am" || period == "pm") {
            if (hour !in 1..12) return null

            hour %= 12
            if (period == "pm") hour += 12
        } else if (hour !in 0..23) {
            return null
        }

        return Pair(hour, minute)
    }

    private fun parseRecurringDays(text: String): List<Int>? {
        val normalized = text
            .lowercase(Locale.ROOT)
            .trim()
            .trimEnd('.', '!', '?', ',')
            .replace(Regex("""\bweek\s+days?\b"""), "weekdays")
            .replace(Regex("""\bweekends?\b"""), "weekends")
            .replace(Regex("""\bevery\s+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (normalized == "week") {
            return listOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
        }

        if (normalized == "weekday" || normalized == "weekdays") {
            return listOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY
            )
        }

        if (normalized == "weekend" || normalized == "weekends") {
            return listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        }

        val dayNames = mapOf(
            "sun" to Calendar.SUNDAY,
            "sunday" to Calendar.SUNDAY,
            "mon" to Calendar.MONDAY,
            "monday" to Calendar.MONDAY,
            "tue" to Calendar.TUESDAY,
            "tues" to Calendar.TUESDAY,
            "tuesday" to Calendar.TUESDAY,
            "wed" to Calendar.WEDNESDAY,
            "wednesday" to Calendar.WEDNESDAY,
            "thu" to Calendar.THURSDAY,
            "thur" to Calendar.THURSDAY,
            "thurs" to Calendar.THURSDAY,
            "thursday" to Calendar.THURSDAY,
            "fri" to Calendar.FRIDAY,
            "friday" to Calendar.FRIDAY,
            "sat" to Calendar.SATURDAY,
            "saturday" to Calendar.SATURDAY
        )

        val tokens = normalized
            .replace(Regex("""\band\b"""), " ")
            .split(Regex("""[,\s]+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) return null

        val days = tokens.map { dayNames[it] ?: return null }.distinct()
        return days.takeIf { it.isNotEmpty() }
    }

    private fun parseTimerDurationSeconds(durationText: String): Long? {
        if (Regex("""half\s+an?\s+hour|a\s+half\s+hour""").matches(durationText)) {
            return 1800L
        }

        if (Regex("""(?:an?|one)\s+hour\s+and\s+a\s+half""").matches(durationText)) {
            return 5400L
        }

        val hoursAndHalf = Regex(
            """(\d+)\s+and\s+a\s+half\s+hours?"""
        ).matchEntire(durationText)

        if (hoursAndHalf != null) {
            val hours = hoursAndHalf.groupValues[1].toLongOrNull() ?: return null
            return hours * 3600L + 1800L
        }

        val hoursAndMinutes = Regex(
            """(\d+)\s*(?:hours?|hrs?)\s*(?:(?:and\s*)?(\d+)\s*(?:minutes?|mins?))?"""
        ).matchEntire(durationText)

        if (hoursAndMinutes != null) {
            val hours = hoursAndMinutes.groupValues[1].toLongOrNull() ?: return null
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
            val hours = hoursAndHalf.groupValues[1].toLongOrNull() ?: return null
            return hours * 60L + 30L
        }

        val hoursAndMinutes = Regex(
            """(\d+)\s*(?:hours?|hrs?)\s*(?:(?:and\s*)?(\d+)\s*(?:minutes?|mins?))?"""
        ).matchEntire(durationText)

        if (hoursAndMinutes != null) {
            val hours = hoursAndMinutes.groupValues[1].toLongOrNull() ?: return null
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
