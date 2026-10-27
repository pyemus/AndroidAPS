package app.aaps.wear.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.WatchAlarmAck.State
import app.aaps.wear.R
import app.aaps.wear.comm.DataLayerListenerServiceWear
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Rings one alarm on the watch: a repeating vibration, an ongoing notification with a Dismiss
 * action, and the [WatchAlarmActivity] on a woken screen.
 *
 * The order inside [start] matters. The wake lock and the vibration come first and depend on
 * nothing else; they are the part that wakes a sleeping wearer. The notification and the screen are
 * extras that may be refused by the system; if they are, the vibration still runs, and when nobody
 * dismisses it the phone rings through the TIMED_OUT ack.
 *
 * Waking the screen copies the phone app: two `setAlarmClock` alarms, one to a receiver that holds
 * a screen-on wake lock, one that launches the activity. Alarm-clock alarms get the
 * background-activity-launch exemption; a plain `startActivity` from this background process is
 * refused unless the app already has a visible window.
 *
 * Calls are synchronized because [start] comes from the data layer's IO thread, [dismiss] from the
 * main thread and the time-out from [scope].
 */
@SingleIn(AppScope::class)
class WatchAlarmController internal constructor(
    private val context: Context,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    private val scope: CoroutineScope
) {

    @Inject
    constructor(context: Context, rxBus: RxBus, aapsLogger: AAPSLogger) :
        this(context, rxBus, aapsLogger, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    data class ActiveAlarm(val id: Long, val text: String, val durationSeconds: Int)

    private val _active = MutableStateFlow<ActiveAlarm?>(null)

    /** The alarm ringing now, or null. [WatchAlarmActivity] closes when this becomes null. */
    val active: StateFlow<ActiveAlarm?> = _active.asStateFlow()

    private var timer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Starts [text] ringing for [durationSeconds], replacing an alarm that is still running.
     * Returns false when this watch cannot vibrate; the caller then tells the phone FAILED.
     */
    @Synchronized
    fun start(id: Long, text: String, durationSeconds: Int): Boolean {
        val vibrator = context.getSystemService(Vibrator::class.java)
        if (vibrator == null || !vibrator.hasVibrator()) {
            aapsLogger.error(LTag.WEAR, "Watch alarm $id: this watch has no vibrator")
            return false
        }
        stopRinging()
        val alarm = ActiveAlarm(id, text, durationSeconds)
        _active.value = alarm
        aapsLogger.info(LTag.WEAR, "Watch alarm start id=$id duration=${durationSeconds}s")
        try {
            acquireWakeLock(durationSeconds)
            vibrate(vibrator)
            // The timer comes right after the vibration: it is the one stop nothing can refuse, so
            // a vibration that started must always have it.
            timer = scope.launch {
                delay(durationSeconds.seconds)
                timedOut(id)
            }
        } catch (e: Exception) {
            // "false" must mean "nothing is running", so undo the half-start before reporting it.
            aapsLogger.error(LTag.WEAR, "Watch alarm $id could not start", e)
            stopRinging()
            _active.value = null
            return false
        }
        // The extras may be refused by the system. The vibration above does not depend on them.
        runCatching { postNotification(alarm) }
            .onFailure { aapsLogger.error(LTag.WEAR, "Watch alarm $id: notification refused", it) }
        runCatching { scheduleScreenWakeAndLaunch() }
            .onFailure { aapsLogger.error(LTag.WEAR, "Watch alarm $id: screen wake refused", it) }
        tryStartActivityDirectly()
        return true
    }

    /** The wearer pressed Dismiss, on the alarm screen or on the notification. */
    @Synchronized
    fun dismiss() {
        val alarm = _active.value ?: return
        aapsLogger.info(LTag.WEAR, "Watch alarm dismiss id=${alarm.id}")
        stopRinging()
        _active.value = null
        rxBus.send(EventWearToMobile(EventData.WatchAlarmAck(alarm.id, State.DISMISSED)))
    }

    @Synchronized
    private fun timedOut(id: Long) {
        val alarm = _active.value ?: return
        if (alarm.id != id) return
        aapsLogger.info(LTag.WEAR, "Watch alarm timed out id=$id")
        stopRinging()
        _active.value = null
        rxBus.send(EventWearToMobile(EventData.WatchAlarmAck(id, State.TIMED_OUT)))
    }

    private fun stopRinging() {
        timer?.cancel()
        timer = null
        context.getSystemService(Vibrator::class.java)?.cancel()
        context.getSystemService(NotificationManager::class.java)?.cancel(DataLayerListenerServiceWear.WATCH_ALARM_NOTIF_ID)
        // A dismiss inside the 1.5 s window would otherwise still light the screen for 10 s.
        context.getSystemService(AlarmManager::class.java)?.let { am ->
            am.cancel(wakePendingIntent())
            am.cancel(showPendingIntent())
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun acquireWakeLock(durationSeconds: Int) {
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).also {
            // A timeout, so the lock can never outlive the alarm if a stop is missed.
            it.acquire((durationSeconds + WAKE_LOCK_MARGIN_SECONDS) * 1000L)
        }
    }

    private fun vibrate(vibrator: Vibrator) {
        val effect = VibrationEffect.createWaveform(PATTERN, AMPLITUDES, 0)
        // USAGE_ALARM: lets the vibration through Bedtime mode and Do Not Disturb, which is the whole
        // point of a night-time alarm.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun postNotification(alarm: ActiveAlarm) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !nm.canUseFullScreenIntent())
            aapsLogger.warn(LTag.WEAR, "Full-screen intent not allowed - the alarm screen relies on the alarm-clock launch")
        // No channel vibration or sound: the Vibrator above does the work, and a second vibration
        // request from the notification would only cut into the alarm pattern.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.watch_alarm_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.watch_alarm_channel_description)
                enableVibration(false)
                setSound(null, null)
            }
        )
        val show = showPendingIntent()
        val dismiss = PendingIntent.getService(
            context, REQUEST_DISMISS,
            Intent(context, DataLayerListenerServiceWear::class.java).setAction(DataLayerListenerServiceWear.INTENT_DISMISS_WATCH_ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notif_icon)
            .setContentTitle(context.getString(R.string.watch_alarm_title))
            .setContentText(alarm.text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setLocalOnly(true)
            .setContentIntent(show)
            .setFullScreenIntent(show, true)
            .addAction(R.drawable.ic_cancel, context.getString(R.string.watch_alarm_dismiss), dismiss)
            .build()
        nm.notify(DataLayerListenerServiceWear.WATCH_ALARM_NOTIF_ID, notification)
    }

    private fun activityIntent(): Intent =
        Intent(context, WatchAlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun showPendingIntent(): PendingIntent =
        PendingIntent.getActivity(context, REQUEST_SHOW, activityIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun wakePendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_WAKE,
            Intent(context, WatchAlarmScreenWakeReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Port of the phone's `AlarmNotificationManager.scheduleScreenWakeAndLaunch`. */
    private fun scheduleScreenWakeAndLaunch() {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            aapsLogger.warn(LTag.WEAR, "Exact alarms not allowed - the watch vibrates, but the screen is not woken")
            return
        }
        val triggerAt = System.currentTimeMillis() + SCREEN_WAKE_DELAY_MS
        val show = showPendingIntent()
        val wake = wakePendingIntent()
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), wake)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), show)
        } catch (e: SecurityException) {
            aapsLogger.error(LTag.WEAR, "Exact alarms revoked - the watch vibrates, but the screen is not woken", e)
            return
        }
        aapsLogger.debug(LTag.WEAR, "Scheduled screen-wake + alarm screen launch in ${SCREEN_WAKE_DELAY_MS}ms")
    }

    /** Works when the app already has a visible window (one of its own watch faces); refused otherwise. */
    private fun tryStartActivityDirectly() {
        runCatching { context.startActivity(activityIntent()) }
            .onFailure { aapsLogger.debug(LTag.WEAR, "Direct alarm screen launch refused: ${it.message}") }
    }

    companion object {

        /** Three long pulses, a pause, repeat. Pixel Watch haptics are moderate, so the pulses are long and at full strength. */
        val PATTERN = longArrayOf(0, 800, 300, 800, 300, 800, 1500)
        val AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255, 0)

        const val CHANNEL_ID = "AndroidAPS-WatchAlarm-v1"
        private const val WAKE_LOCK_TAG = "aaps:watch-alarm"
        private const val WAKE_LOCK_MARGIN_SECONDS = 10
        private const val SCREEN_WAKE_DELAY_MS = 1500L
        private const val REQUEST_SHOW = 40
        private const val REQUEST_WAKE = 41
        private const val REQUEST_DISMISS = 42
    }
}
