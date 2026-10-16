package co.neonexus.geminein

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

object ClockActions {

    fun setTimer(context: Context, seconds: Int) {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Gemi-nein timer")
        }

        launchClockAction(context, intent)
    }

    fun setAlarm(context: Context, hour: Int, minute: Int) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Gemi-nein alarm")
        }

        launchClockAction(context, intent)
    }

    private fun launchClockAction(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Couldn't launch the Clock app to perform this action.",
                e
            )
        }
    }
}
