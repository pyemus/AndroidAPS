package app.aaps.wear.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/**
 * Powers the watch display on for a background alarm. A port of the phone's
 * `AlarmScreenWakeReceiver`.
 *
 * Fired by an [android.app.AlarmManager.setAlarmClock] alarm from [WatchAlarmController]. That
 * alarm-clock context is what lets the deprecated-but-working `ACQUIRE_CAUSES_WAKEUP` wake lock turn
 * the screen on from the background; `Activity.setTurnScreenOn()` alone cannot do that for an
 * activity launched from the background. A sibling alarm launches [WatchAlarmActivity] once the
 * screen is lit. Needs only the normal `WAKE_LOCK` permission.
 */
class WatchAlarmScreenWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "aaps:watch-alarm-screen-wake"
        )
        // Short hold: long enough to power the display and let the alarm screen take over.
        wl.acquire(WAKE_HOLD_MS)
        Log.i(TAG, "Watch alarm screen wake lock acquired (interactive=${pm.isInteractive})")
    }

    private companion object {

        const val TAG = "AAPSWatchAlarmWake"
        const val WAKE_HOLD_MS = 10_000L
    }
}
