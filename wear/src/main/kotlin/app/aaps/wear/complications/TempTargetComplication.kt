package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.core.interfaces.logging.LTag
import dagger.android.AndroidInjection

/**
 * Temp target complication
 *
 * RANGED_VALUE: value = Status.tempTargetLevel (0 profile target, 1 loop adjusted, 2 temp target active),
 * text = current target.
 * SHORT_TEXT: current target.
 */
class TempTargetComplication : ModernBaseComplicationProviderService() {

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun buildComplicationData(
        type: ComplicationType,
        data: app.aaps.wear.data.ComplicationData,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        val status = data.statusData
        val text = PlainComplicationText.Builder(text = status.tempTarget).build()
        val description = PlainComplicationText.Builder(text = "Target ${status.tempTarget}").build()
        return when (type) {
            ComplicationType.RANGED_VALUE -> {
                RangedValueComplicationData.Builder(
                    value = WffEncoding.tempTargetValue(status.tempTargetLevel),
                    min = WffEncoding.TT_MIN,
                    max = WffEncoding.TT_MAX,
                    contentDescription = description
                )
                    .setText(text)
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            ComplicationType.SHORT_TEXT   -> {
                ShortTextComplicationData.Builder(text = text, contentDescription = description)
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                          -> {
                aapsLogger.warn(LTag.WEAR, "TempTargetComplication unexpected type: $type")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = TempTargetComplication::class.java.canonicalName!!
}
