package app.aaps.plugins.sync.wear.wearintegration

import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.WatchAlarmAck.State
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.plugins.sync.SyncStringsValues
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The fallback state machine in virtual time. Every case advances over a bounded window and
 * asserts a count, so a regression shows up as a wrong number and never as a hang.
 */
class AndroidWatchAlarmTest : TestBaseWithProfile() {

    @Mock lateinit var uiInteraction: UiInteraction

    /** Real English, so the fallback alarm text is checked as the user reads it. */
    private val text = generatedTextResolver("sync" to SyncStringsValues::textOf)

    /** Everything the sut launches goes to [TestScope.backgroundScope], which the test cancels at its end. */
    private fun TestScope.sut(): AndroidWatchAlarm = AndroidWatchAlarm(context, rxBus, aapsLogger, text, uiInteraction, dateUtil, backgroundScope)

    /** Every [EventData] the phone sent to the watch, in order. Subscribed before anything is raised. */
    private fun TestScope.sentToWatch(): MutableList<EventData> {
        val sent = mutableListOf<EventData>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            rxBus.toFlow(EventMobileToWear::class).collect { sent += it.payload }
        }
        return sent
    }

    private fun TestScope.ack(id: Long, state: State) {
        rxBus.send(EventData.WatchAlarmAck(id, state))
        runCurrent()
    }

    private fun phoneRangTimes(n: Int) = verify(uiInteraction, times(n)).runAlarm(any(), any(), eq(AlarmSound.ALARM))

    @Test fun `raise sends the alarm to the watch and does not ring the phone at once`() = runTest {
        val sent = sentToWatch()
        val sut = sut()

        sut.raise("Low", 90)
        runCurrent()

        assertThat(sent).containsExactly(EventData.WatchAlarm(dateUtil.now(), "Low", 90))
        verify(uiInteraction, never()).runAlarm(any(), any(), any())
    }

    @Test fun `no receipt rings the phone once after the receipt timeout and never again`() = runTest {
        val sut = sut()
        sut.raise("Low", 90)
        runCurrent()

        advanceTimeBy(29.seconds)
        runCurrent()
        phoneRangTimes(0)

        advanceTimeBy(1.seconds)
        runCurrent()
        phoneRangTimes(1)
        verify(uiInteraction).runAlarm("Low (the watch did not confirm)", "Watch alarm not confirmed", AlarmSound.ALARM)

        advanceTimeBy(10.minutes)
        runCurrent()
        phoneRangTimes(1)
    }

    @Test fun `received and dismissed never rings the phone`() = runTest {
        val sut = sut()
        sut.raise("Low", 90)
        runCurrent()
        ack(dateUtil.now(), State.RECEIVED)

        advanceTimeBy(60.seconds)
        ack(dateUtil.now(), State.DISMISSED)

        advanceTimeBy(10.minutes)
        runCurrent()
        phoneRangTimes(0)
        verify(uiInteraction, never()).stopAlarm(any())
    }

    @Test fun `received but never dismissed rings the phone after the duration plus the grace`() = runTest {
        val sut = sut()
        sut.raise("Low", 90)
        runCurrent()
        ack(dateUtil.now(), State.RECEIVED)

        // 90 s alarm + 15 s grace = 105 s after the send
        advanceTimeBy(104.seconds)
        runCurrent()
        phoneRangTimes(0)

        advanceTimeBy(1.seconds)
        runCurrent()
        phoneRangTimes(1)

        advanceTimeBy(10.minutes)
        runCurrent()
        phoneRangTimes(1)
    }

    @Test fun `timed out on the watch rings the phone at once`() = runTest {
        val sut = sut()
        sut.raise("Low", 90)
        runCurrent()
        ack(dateUtil.now(), State.RECEIVED)

        advanceTimeBy(90.seconds)
        ack(dateUtil.now(), State.TIMED_OUT)
        phoneRangTimes(1)

        advanceTimeBy(10.minutes)
        runCurrent()
        phoneRangTimes(1)
    }

    @Test fun `a watch that could not start the alarm makes the phone ring at once`() = runTest {
        val sut = sut()
        sut.raise("Low", 90)
        runCurrent()

        ack(dateUtil.now(), State.RECEIVED)
        ack(dateUtil.now(), State.FAILED)
        phoneRangTimes(1)

        advanceTimeBy(10.minutes)
        runCurrent()
        phoneRangTimes(1)
    }

    @Test fun `an ack for another alarm is ignored`() = runTest {
        val sut = sut()
        sut.raise("Low", 90)
        runCurrent()

        ack(dateUtil.now() + 1, State.RECEIVED)
        ack(dateUtil.now() + 1, State.DISMISSED)

        advanceTimeBy(30.seconds)
        runCurrent()
        phoneRangTimes(1)
    }

    @Test fun `a second raise replaces the first and only the new one is watched`() = runTest {
        val sent = sentToWatch()
        val sut = sut()
        val firstId = dateUtil.now()
        sut.raise("Low", 90)
        runCurrent()

        // Same millisecond on purpose: the ids must still differ, or the old alarm's acks would match the new one.
        val secondId = firstId + 1
        sut.raise("Lower", 60)
        runCurrent()
        assertThat(sent.map { (it as EventData.WatchAlarm).id }).containsExactly(firstId, secondId).inOrder()

        ack(secondId, State.RECEIVED)
        advanceTimeBy(30.seconds)
        runCurrent()
        // The first alarm was never confirmed, but it was replaced, so it does not ring.
        phoneRangTimes(0)

        ack(secondId, State.DISMISSED)
        advanceTimeBy(10.minutes)
        runCurrent()
        phoneRangTimes(0)
    }

    @Test fun `a receipt that arrives late moves the dismiss deadline`() = runTest {
        val sut = sut()
        sut.raise("Low", 90)
        runCurrent()

        advanceTimeBy(29.seconds)
        runCurrent()
        ack(dateUtil.now(), State.RECEIVED)

        // The watch rings 90 s from ITS start, so the deadline is 29 + 90 + 15 = 134 s after the send.
        advanceTimeBy(104.seconds)
        runCurrent()
        phoneRangTimes(0)

        advanceTimeBy(1.seconds)
        runCurrent()
        phoneRangTimes(1)
    }

    @Test fun `a time-out that arrives after a dismiss does not ring the phone`() = runTest {
        val sut = sut()
        sut.raise("Low", 90)
        runCurrent()
        ack(dateUtil.now(), State.RECEIVED)
        ack(dateUtil.now(), State.DISMISSED)

        ack(dateUtil.now(), State.TIMED_OUT)
        ack(dateUtil.now(), State.FAILED)

        advanceTimeBy(10.minutes)
        runCurrent()
        phoneRangTimes(0)
    }

    @Test fun `a dismiss that arrives after the phone rang stops the phone`() = runTest {
        val sut = sut()
        sut.raise("Low", 90)
        runCurrent()

        advanceTimeBy(30.seconds)
        runCurrent()
        phoneRangTimes(1)

        ack(dateUtil.now(), State.DISMISSED)
        verify(uiInteraction).stopAlarm(any())
    }
}
