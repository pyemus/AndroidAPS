package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import app.aaps.core.interfaces.alerts.WatchAlarm
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.utils.lenientInt
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.InputString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Rings an alarm on the paired watch instead of on the phone.
 *
 * Made for the night: a low BG should wake the wearer through the watch, not through a loud
 * phone. The phone still rings when the watch does not confirm the alarm or the wearer does not
 * dismiss it in time - that fallback lives in [WatchAlarm], not here.
 *
 * [isValid] does not look at the watch connection on purpose. The runtime disables a rule for good
 * when one of its actions is invalid, and a watch that is out of range for a minute must not switch
 * off a safety rule.
 */
class ActionWatchAlarm(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val watchAlarm: WatchAlarm
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    var text = InputString()
    var durationSeconds: Int = DEFAULT_DURATION_SECONDS

    override fun friendlyName(): TextRef = AutomationStrings.watch_alarm
    override fun shortDescription(): String = rh.gs(AutomationStrings.watch_alarm_message, text.value)
    override fun composeIcon() = Icons.Filled.Watch
    override fun elementType() = ElementType.BG_CHECK

    override fun isValid(): Boolean = text.value.isNotEmpty() && watchAlarm.isAvailable

    override suspend fun doAction(): PumpEnactResult {
        watchAlarm.raise(text.value, durationSeconds)
        return pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)
    }

    override fun toJSON(): String =
        buildJsonObject {
            put("type", this@ActionWatchAlarm::class.simpleName)
            put("data", buildJsonObject {
                put("text", text.value)
                put("durationSeconds", durationSeconds)
            })
        }.toString()

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        text.value = o.lenientString("text", "")
        durationSeconds = o.lenientInt("durationSeconds", DEFAULT_DURATION_SECONDS)
            .takeIf { it in MIN_DURATION_SECONDS..MAX_DURATION_SECONDS } ?: DEFAULT_DURATION_SECONDS
        return this
    }

    override fun hasDialog(): Boolean = true

    companion object {

        const val DEFAULT_DURATION_SECONDS = 90
        const val MIN_DURATION_SECONDS = 15
        const val MAX_DURATION_SECONDS = 300
        const val DURATION_STEP_SECONDS = 15
    }
}
