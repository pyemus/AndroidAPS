package app.aaps.plugins.automation.actions

import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.InputString
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class ActionWatchAlarmTest : ActionsTestBase() {

    private lateinit var sut: ActionWatchAlarm

    @BeforeEach
    fun setup() {
        sut = ActionWatchAlarm(aapsLogger, text, { pumpEnactResultProvider() }, watchAlarm)
    }

    @Test fun friendlyNameTest() {
        assertThat(sut.friendlyName()).isEqualTo(AutomationStrings.watch_alarm)
    }

    @Test fun shortDescriptionTest() {
        sut.text = InputString("Asd")
        assertThat(sut.shortDescription()).isEqualTo("Alarm on watch: Asd")
    }

    @Test fun isValidTest() {
        // Empty text: the watch would show nothing.
        assertThat(sut.isValid()).isFalse()
        sut.text = InputString("Asd")
        assertThat(sut.isValid()).isTrue()
        // No watch on this platform: the rule is reported invalid instead of running silently.
        whenever(watchAlarm.isAvailable).thenReturn(false)
        assertThat(sut.isValid()).isFalse()
    }

    @Test fun doActionTest() = runTest {
        sut.text = InputString("Asd")
        sut.durationSeconds = 60
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        verify(watchAlarm).raise("Asd", 60)
    }

    @Test fun hasDialogTest() {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() {
        sut.text = InputString("Asd")
        sut.durationSeconds = 60
        JSONAssert.assertEquals("""{"data":{"text":"Asd","durationSeconds":60},"type":"ActionWatchAlarm"}""", sut.toJSON(), true)
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("""{"text":"Asd","durationSeconds":120}""")
        assertThat(sut.text.value).isEqualTo("Asd")
        assertThat(sut.durationSeconds).isEqualTo(120)
    }

    @Test fun fromJSONWithoutDurationUsesTheDefault() {
        sut.fromJSON("""{"text":"Asd"}""")
        assertThat(sut.durationSeconds).isEqualTo(ActionWatchAlarm.DEFAULT_DURATION_SECONDS)
    }

    @Test fun fromJSONOutOfRangeDurationUsesTheDefault() {
        sut.fromJSON("""{"text":"Asd","durationSeconds":5}""")
        assertThat(sut.durationSeconds).isEqualTo(ActionWatchAlarm.DEFAULT_DURATION_SECONDS)
        sut.fromJSON("""{"text":"Asd","durationSeconds":9999}""")
        assertThat(sut.durationSeconds).isEqualTo(ActionWatchAlarm.DEFAULT_DURATION_SECONDS)
    }

    @Test fun factoryBuildsItFromItsName() {
        val built = actionFactory.instantiate("ActionWatchAlarm")
        assertThat(built).isInstanceOf(ActionWatchAlarm::class.java)
        assertThat(actionFactory.allActions().any { it is ActionWatchAlarm }).isTrue()
    }
}
