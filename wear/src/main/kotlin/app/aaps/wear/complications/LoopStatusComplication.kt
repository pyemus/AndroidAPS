package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import app.aaps.core.interfaces.logging.LTag
import dagger.android.AndroidInjection
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Loop status complication
 *
 * RANGED_VALUE: value = minutes since last loop run clamped to [0, 15], -1 if unknown.
 * value >= max means the loop is stale (red on the WFF face). Text = auto-updating minutes.
 * SHORT_TEXT: auto-updating minutes since last loop run.
 */
class LoopStatusComplication : ModernBaseComplicationProviderService() {

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
        val openApsStatus = data.statusData.openApsStatus
        val text = loopText(openApsStatus)
        val description = PlainComplicationText.Builder(text = "Loop").build()
        return when (type) {
            ComplicationType.RANGED_VALUE -> {
                RangedValueComplicationData.Builder(
                    value = WffEncoding.loopValue(openApsStatus, System.currentTimeMillis()),
                    min = WffEncoding.LOOP_MIN,
                    max = WffEncoding.LOOP_STALE_MINUTES.toFloat(),
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
                aapsLogger.warn(LTag.WEAR, "LoopStatusComplication unexpected type: $type")
                null
            }
        }
    }

    private fun loopText(openApsStatus: Long): ComplicationText =
        if (openApsStatus <= 0L) PlainComplicationText.Builder(text = "--").build()
        else TimeDifferenceComplicationText.Builder(
            style = TimeDifferenceStyle.SHORT_SINGLE_UNIT,
            countUpTimeReference = CountUpTimeReference(Instant.ofEpochMilli(openApsStatus))
        )
            .setMinimumTimeUnit(TimeUnit.MINUTES)
            .setText("^1")
            .build()

    override fun getComplicationAction(): ComplicationAction = ComplicationAction.STATUS
    override fun getProviderCanonicalName(): String = LoopStatusComplication::class.java.canonicalName!!
}
