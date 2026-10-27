package app.aaps.plugins.sync.wear.wearintegration

import android.content.Context
import android.os.PowerManager
import app.aaps.core.interfaces.alerts.WatchAlarm
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.WatchAlarmAck.State
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.sync.SyncStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Sends an alarm to the watch and rings the phone when the watch does not confirm it.
 *
 * The data layer is fire-and-forget: a message to a watch that is off, out of range or running an
 * older app is simply lost. So every alarm has two deadlines:
 *  - [RECEIPT_TIMEOUT] after the send: the watch must have answered RECEIVED, or the phone rings;
 *  - the alarm duration plus [DISMISS_GRACE] after the receipt: the wearer must have dismissed it,
 *    or the phone rings. It counts from the receipt because the watch counts its duration from its
 *    own start, and the receipt can arrive late.
 * TIMED_OUT and FAILED from the watch ring the phone at once. A DISMISSED that arrives after the
 * phone started ringing stops the phone again, because the wearer is awake. Acks for an alarm that
 * already ended on the watch are ignored, so a late TIMED_OUT cannot wake a wearer who dismissed.
 *
 * A partial wake lock is held while a fallback is pending. The deadlines are coroutine delays on
 * the monotonic clock, which stops while the phone sleeps; at night that would make them fire
 * late, which is exactly when this alarm matters. The lock has a timeout, so it can never leak.
 *
 * The acks are read straight from the bus here instead of in `DataHandlerMobile`, so the whole
 * state of one alarm lives in one object. The Wear plugin is not asked whether it is enabled: when
 * it is off, nothing is sent and the receipt timeout rings the phone, which is the right answer.
 * Known limit: the state is in memory only. If the app process dies while an alarm is pending, the
 * fallback is lost; the automation rule re-fires on its repeat interval while the trigger holds.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidWatchAlarm(
    private val context: Context,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val uiInteraction: UiInteraction,
    private val dateUtil: DateUtil,
    @ApplicationScope private val scope: CoroutineScope
) : WatchAlarm {

    override val isAvailable: Boolean = true

    /** One alarm, from its [raise] until the next. Only touched under [mutex]. */
    private class Pending(val id: Long, val text: String, val durationSeconds: Int) {

        var received = false

        /** Dismissed, timed out or failed on the watch: later acks for it are ignored. */
        var finished = false
        var rang = false
        var receiptJob: Job? = null
        var dismissJob: Job? = null

        fun cancelJobs() {
            receiptJob?.cancel()
            dismissJob?.cancel()
        }
    }

    private val mutex = Mutex()
    private var current: Pending? = null
    private var lastId = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    init {
        // UNDISPATCHED: the bus has no replay, and a watch answers within milliseconds of a send.
        rxBus.toFlow(EventData.WatchAlarmAck::class)
            .collectResilient(scope, aapsLogger, LTag.WEAR, start = CoroutineStart.UNDISPATCHED) { onAck(it) }
    }

    override fun raise(text: String, durationSeconds: Int) {
        scope.launch {
            mutex.withLock {
                // A replaced alarm gets no fallback of its own; the new one takes over the duty.
                current?.cancelJobs()
                val alarm = Pending(nextId(), text, durationSeconds)
                current = alarm
                holdCpu(alarm)
                // The deadline exists before the send, so a send that fails still ends in a phone alarm.
                alarm.receiptJob = launch {
                    delay(RECEIPT_TIMEOUT)
                    mutex.withLock {
                        if (current === alarm && !alarm.received && !alarm.finished) ring(alarm, "no receipt from the watch")
                    }
                }
                try {
                    aapsLogger.info(LTag.WEAR, "WatchAlarm sent id=${alarm.id} duration=${durationSeconds}s")
                    rxBus.send(EventMobileToWear(EventData.WatchAlarm(alarm.id, text, durationSeconds)))
                } catch (e: Exception) {
                    aapsLogger.error(LTag.WEAR, "WatchAlarm ${alarm.id} could not be sent", e)
                    ring(alarm, "the message could not be sent")
                }
            }
        }
    }

    /** Ids rise even for two alarms in the same millisecond, so an old alarm's acks never match a new one. */
    private fun nextId(): Long {
        lastId = max(dateUtil.now(), lastId + 1)
        return lastId
    }

    private suspend fun onAck(ack: EventData.WatchAlarmAck) {
        mutex.withLock {
            val alarm = current
            if (alarm == null || alarm.id != ack.id) {
                aapsLogger.debug(LTag.WEAR, "WatchAlarmAck ${ack.state} id=${ack.id} ignored, current id=${alarm?.id}")
                return
            }
            if (alarm.finished) {
                aapsLogger.debug(LTag.WEAR, "WatchAlarmAck ${ack.state} id=${ack.id} after the alarm ended, ignored")
                return
            }
            aapsLogger.info(LTag.WEAR, "WatchAlarmAck ${ack.state} id=${ack.id}")
            when (ack.state) {
                State.RECEIVED  -> if (!alarm.received) {
                    alarm.received = true
                    alarm.receiptJob?.cancel()
                    // The watch counts the duration from its own start, so the dismiss deadline starts now.
                    alarm.dismissJob = scope.launch {
                        delay(alarm.durationSeconds.seconds + DISMISS_GRACE)
                        mutex.withLock {
                            if (current === alarm && !alarm.finished) ring(alarm, "not dismissed on the watch")
                        }
                    }
                }

                State.DISMISSED -> {
                    finish(alarm)
                    if (alarm.rang) uiInteraction.stopAlarm("Watch alarm ${alarm.id} dismissed on the watch")
                }

                State.TIMED_OUT -> {
                    finish(alarm)
                    ring(alarm, "timed out on the watch")
                }

                State.FAILED    -> {
                    finish(alarm)
                    ring(alarm, "the watch could not start it")
                }
            }
        }
    }

    private fun finish(alarm: Pending) {
        alarm.finished = true
        alarm.cancelJobs()
        releaseCpu()
    }

    private fun ring(alarm: Pending, reason: String) {
        if (alarm.rang) return
        alarm.rang = true
        releaseCpu()
        aapsLogger.warn(LTag.WEAR, "Watch alarm fallback ($reason) id=${alarm.id} - ringing on the phone")
        uiInteraction.runAlarm(
            status = rh.gs(SyncStrings.watch_alarm_fallback_status, alarm.text),
            title = rh.gs(SyncStrings.watch_alarm_fallback_title),
            sound = AlarmSound.ALARM
        )
    }

    private fun holdCpu(alarm: Pending) {
        releaseCpu()
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).also {
            it.acquire((RECEIPT_TIMEOUT + alarm.durationSeconds.seconds + DISMISS_GRACE + WAKE_LOCK_MARGIN).inWholeMilliseconds)
        }
    }

    private fun releaseCpu() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {

        /** How long the watch gets to confirm that the message arrived. */
        val RECEIPT_TIMEOUT: Duration = 30.seconds

        /** Added to the alarm duration before "not dismissed" rings the phone, for message latency. */
        val DISMISS_GRACE: Duration = 15.seconds

        private val WAKE_LOCK_MARGIN: Duration = 5.seconds
        private const val WAKE_LOCK_TAG = "aaps:watch-alarm-fallback"
    }
}
