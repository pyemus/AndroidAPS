package app.aaps.plugins.automation

import androidx.datastore.preferences.core.preferencesOf
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.actions.ActionSMBChange
import app.aaps.plugins.automation.actions.ActionStopProcessing
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerConnectorTest
import app.aaps.plugins.automation.triggers.TriggerDummy
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.skyscreamer.jsonassert.JSONAssert

class AutomationEventTest : TestBase() {

    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is AutomationEventObject) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is Action) {
                it.aapsLogger = aapsLogger
                it.rh = rh
            }
            if (it is ActionSMBChange) {
                it.dateUtil = dateUtil
                it.preferences = preferences
            }
        }
    }

    @Test fun testCloneEvent() {
        // create test object
        val event = AutomationEventObject(injector)
        event.title = "Test"
        event.trigger = TriggerDummy(injector).instantiate(JSONObject(TriggerConnectorTest().oneItem)) as TriggerConnector
        event.addAction(ActionSMBChange(injector))

        // export to json
        val eventJsonExpected =
            "{\"userAction\":false,\"autoRemove\":false,\"readOnly\":false,\"trigger\":\"{\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[\\\"{\\\\\\\"data\\\\\\\":{\\\\\\\"connectorType\\\\\\\":\\\\\\\"AND\\\\\\\",\\\\\\\"triggerList\\\\\\\":[]},\\\\\\\"type\\\\\\\":\\\\\\\"TriggerConnector\\\\\\\"}\\\"]},\\\"type\\\":\\\"TriggerConnector\\\"}\",\"title\":\"Test\",\"systemAction\":false,\"minRepeatMinutes\":5,\"actions\":[\"{\\\"data\\\":{\\\"smbState\\\":true},\\\"type\\\":\\\"ActionSMBChange\\\"}\"],\"enabled\":true}"
        JSONAssert.assertEquals(eventJsonExpected, event.toJSON(), true)

        // clone
        val clone = AutomationEventObject(injector).fromJSON(eventJsonExpected)

        // check title
        assertThat(clone.title).isEqualTo(event.title)

        // check trigger
        assertThat(clone.trigger).isNotNull()
        assertThat(event.trigger).isNotSameInstanceAs(clone.trigger)
        assertThat(event.trigger.javaClass).isNotInstanceOf(clone.trigger.javaClass)
        JSONAssert.assertEquals(event.trigger.toJSON(), clone.trigger.toJSON(), true)

        // check action
        assertThat(clone.actions).hasSize(1)
        assertThat(event.actions).isNotSameInstanceAs(clone.actions)
        JSONAssert.assertEquals(clone.toJSON(), clone.toJSON(), true)
    }

    @Test fun minRepeatMinutesLimitsReRun() {
        val now = 100_000_000L
        `when`(dateUtil.now()).thenReturn(now)
        val event = AutomationEventObject(injector)
        event.minRepeatMinutes = 30

        event.lastRun = now - T.mins(29).msecs()
        assertThat(event.shouldRun()).isFalse()
        event.lastRun = now - T.mins(30).msecs()
        assertThat(event.shouldRun()).isTrue()

        // values below the hardcoded 5 min are not allowed
        event.minRepeatMinutes = 1
        event.lastRun = now - T.mins(4).msecs()
        assertThat(event.shouldRun()).isFalse()
    }

    @Test fun minRepeatMinutesJson() {
        val event = AutomationEventObject(injector)
        event.title = "Test"
        event.minRepeatMinutes = 45
        assertThat(AutomationEventObject(injector).fromJSON(event.toJSON()).minRepeatMinutes).isEqualTo(45)

        // events stored before this field existed keep the 5 min default
        val legacy = JSONObject(event.toJSON()).apply { remove("minRepeatMinutes") }.toString()
        assertThat(AutomationEventObject(injector).fromJSON(legacy).minRepeatMinutes).isEqualTo(5)
    }

    @Test fun hasStopProcessing() {
        val event = AutomationEventObject(injector)
        event.title = "Test"
        event.trigger = TriggerDummy(injector).instantiate(JSONObject(TriggerConnectorTest().oneItem)) as TriggerConnector
        assertThat(event.hasStopProcessing()).isFalse()
        event.addAction(ActionStopProcessing(injector))
        assertThat(event.hasStopProcessing()).isTrue()
    }
}
