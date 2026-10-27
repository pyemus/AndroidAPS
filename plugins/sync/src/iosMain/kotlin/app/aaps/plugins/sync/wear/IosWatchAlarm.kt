package app.aaps.plugins.sync.wear

import app.aaps.core.interfaces.alerts.WatchAlarm
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The watch alarm on iOS, which has no Wear OS data layer.
 *
 * [isAvailable] answering false is the important part: the automation chooser does not offer the
 * action, and a rule synced from an Android phone that still carries it is reported as invalid
 * instead of running and doing nothing. That is the difference between a feature being visibly
 * absent and one being present and dead. [raise] still logs an error rather than throwing, so a
 * caller that ignores [isAvailable] is recorded, not crashed.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosWatchAlarm(
    private val aapsLogger: AAPSLogger
) : WatchAlarm {

    override val isAvailable: Boolean = false

    override fun raise(text: String, durationSeconds: Int) {
        aapsLogger.error(LTag.WEAR, "iOS has no watch alarm, alarm \"$text\" dropped")
    }
}
