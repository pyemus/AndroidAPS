package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.asJsonObject
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerTimeRangeTest : TriggerTestBase() {

    private var timeJson = "{\"data\":{\"start\":753,\"end\":784},\"type\":\"TriggerTimeRange\"}"

    @BeforeEach
    fun mock() {
        now = 754 // in minutes from midnight
        val nowMills = MidnightTime.calcMidnightPlusMinutes(now.toInt())
        whenever(dateUtil.now()).thenReturn(nowMills)
    }

    @Test
    fun shouldRunTest() = runTest {
        // range starts 1 min in the future
        var t: TriggerTimeRange = TriggerTimeRange(triggerDeps).period((now + 1).toInt(), (now + 30).toInt())
        assertThat(t.shouldRun()).isFalse()

        // range starts 30 min back
        t = TriggerTimeRange(triggerDeps).period((now - 30).toInt(), (now + 30).toInt())
        assertThat(t.shouldRun()).isTrue()

        // Period is all day long
        t = TriggerTimeRange(triggerDeps).period(1, 1440)
        assertThat(t.shouldRun()).isTrue()
    }

    @Test
    fun midnightCrossingRangeTest() = runTest {
        // 23:00 to 07:00 is stored as start > end; a night rule depends on this reading correctly.
        val t = TriggerTimeRange(triggerDeps).period(23 * 60, 7 * 60)
        for ((minutesSinceMidnight, expected) in listOf(23 * 60 + 20 to true, 5 * 60 to true, 12 * 60 + 34 to false, 7 * 60 + 10 to false, 22 * 60 + 50 to false)) {
            whenever(dateUtil.now()).thenReturn(MidnightTime.calcMidnightPlusMinutes(minutesSinceMidnight))
            assertThat(t.shouldRun()).isEqualTo(expected)
        }
    }

    @Test
    fun toJSONTest() = runTest {
        val t: TriggerTimeRange = TriggerTimeRange(triggerDeps).period((now - 1).toInt(), (now + 30).toInt())
        JSONAssert.assertEquals(timeJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() = runTest {
        val t: TriggerTimeRange = TriggerTimeRange(triggerDeps).period(120, 180)
        val t2 = triggerFactory.instantiate(t.toJSON().asJsonObject()) as TriggerTimeRange
        assertThat(t2.period(753, 360).range.start).isEqualTo((now - 1).toInt())
        assertThat(t2.period(753, 360).range.end).isEqualTo(360)
    }

    @Test fun copyConstructorTest() = runTest {
        val t = TriggerTimeRange(triggerDeps)
        t.period(now.toInt(), (now + 30).toInt())
        val t1 = t.duplicate() as TriggerTimeRange
        assertThat(t1.range.start).isEqualTo(now.toInt())
    }

    @Test fun friendlyNameTest() = runTest {
        assertThat(TriggerTimeRange(triggerDeps).friendlyName()).isEqualTo(AutomationStrings.time_range)
    }

    @Test fun friendlyDescriptionTest() = runTest {
        assertThat(TriggerTimeRange(triggerDeps).friendlyDescription()).isEqualTo("Time is between 12:34 PM and 12:34 PM")
    }
}
