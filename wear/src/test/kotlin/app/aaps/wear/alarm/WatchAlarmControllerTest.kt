package app.aaps.wear.alarm

import android.app.NotificationManager
import android.content.Context
import android.os.Vibrator
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.WatchAlarmAck.State
import app.aaps.wear.AAPSLoggerTest
import app.aaps.wear.comm.DataLayerListenerServiceWear
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The controller against Robolectric's Vibrator, NotificationManager, PowerManager and AlarmManager.
 * The time-out runs in the test's virtual time; the window is bounded, so a regression fails on a
 * count instead of hanging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class WatchAlarmControllerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val rxBus: RxBus = mock()
    private val vibrator: Vibrator = context.getSystemService(Vibrator::class.java)
    private val notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    private fun TestScope.sut() = WatchAlarmController(context, rxBus, AAPSLoggerTest(), backgroundScope)

    private fun postedNotification() = shadowOf(notificationManager).getNotification(DataLayerListenerServiceWear.WATCH_ALARM_NOTIF_ID)

    @Test
    fun `start vibrates on repeat, posts the notification and remembers the alarm`() = runTest {
        val sut = sut()

        assertThat(sut.start(7L, "Low", 90)).isTrue()

        assertThat(shadowOf(vibrator).isVibrating).isTrue()
        assertThat(shadowOf(vibrator).repeat).isEqualTo(0)
        assertThat(postedNotification()).isNotNull()
        assertThat(sut.active.value).isEqualTo(WatchAlarmController.ActiveAlarm(7L, "Low", 90))
    }

    @Test
    fun `dismiss stops the vibration, removes the notification and tells the phone`() = runTest {
        val sut = sut()
        sut.start(7L, "Low", 90)

        sut.dismiss()

        assertThat(shadowOf(vibrator).isCancelled).isTrue()
        assertThat(postedNotification()).isNull()
        assertThat(sut.active.value).isNull()
        verify(rxBus).send(EventWearToMobile(EventData.WatchAlarmAck(7L, State.DISMISSED)))
    }

    @Test
    fun `an alarm nobody dismisses stops after its duration and tells the phone`() = runTest {
        val sut = sut()
        sut.start(7L, "Low", 90)

        advanceTimeBy(89.seconds)
        runCurrent()
        assertThat(sut.active.value).isNotNull()

        advanceTimeBy(1.seconds)
        runCurrent()
        assertThat(sut.active.value).isNull()
        assertThat(shadowOf(vibrator).isCancelled).isTrue()
        verify(rxBus).send(EventWearToMobile(EventData.WatchAlarmAck(7L, State.TIMED_OUT)))
    }

    @Test
    fun `a new alarm replaces the running one, which is not reported`() = runTest {
        val sut = sut()
        sut.start(7L, "Low", 90)

        sut.start(8L, "Lower", 60)

        assertThat(sut.active.value?.id).isEqualTo(8L)
        advanceTimeBy(10.minutes)
        runCurrent()
        verify(rxBus, never()).send(EventWearToMobile(EventData.WatchAlarmAck(7L, State.TIMED_OUT)))
        verify(rxBus).send(EventWearToMobile(EventData.WatchAlarmAck(8L, State.TIMED_OUT)))
    }

    @Test
    fun `dismiss without an alarm does nothing`() = runTest {
        val sut = sut()

        sut.dismiss()

        verify(rxBus, never()).send(any())
    }
}
