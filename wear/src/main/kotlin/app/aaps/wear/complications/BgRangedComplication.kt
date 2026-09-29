package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.weardata.EventData
import dagger.android.AndroidInjection
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * BG complication for the WFF watch face (also usable on other faces)
 *
 * RANGED_VALUE: value = [WffEncoding.bgCode] (0 low, 1 in range, 2 high, 3 stale/no data)
 * so a watch face without code can color the BG. Text = BG, title = reading age (auto-updating),
 * monochromatic image = trend arrow (can be tinted by the face).
 * SHORT_TEXT: BG + arrow, title = reading age.
 */
class BgRangedComplication : ModernBaseComplicationProviderService() {

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
        val bg = data.bgData
        val arrow = MonochromaticImage.Builder(Icon.createWithResource(this, WffEncoding.arrowDrawable(bg.slopeArrow))).build()
        return when (type) {
            ComplicationType.RANGED_VALUE -> {
                val code = WffEncoding.bgCode(bg, System.currentTimeMillis())
                RangedValueComplicationData.Builder(
                    value = code,
                    min = WffEncoding.BG_MIN,
                    max = WffEncoding.BG_MAX,
                    contentDescription = PlainComplicationText.Builder(text = "Glucose ${bg.sgvString} ${bg.slopeArrow}").build()
                )
                    .setText(PlainComplicationText.Builder(text = bg.sgvString).build())
                    .setTitle(ageText(bg))
                    .setMonochromaticImage(arrow)
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            ComplicationType.SHORT_TEXT   -> {
                val text = bg.sgvString + bg.slopeArrow + "︎"
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = text).build(),
                    contentDescription = PlainComplicationText.Builder(text = "Glucose $text").build()
                )
                    .setTitle(ageText(bg))
                    .setMonochromaticImage(arrow)
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                          -> {
                aapsLogger.warn(LTag.WEAR, "BgRangedComplication unexpected type: $type")
                null
            }
        }
    }

    /** Auto-updating reading age, e.g. "3m" */
    private fun ageText(bg: EventData.SingleBg): ComplicationText =
        if (bg.timeStamp <= 0L) PlainComplicationText.Builder(text = "--").build()
        else TimeDifferenceComplicationText.Builder(
            style = TimeDifferenceStyle.SHORT_SINGLE_UNIT,
            countUpTimeReference = CountUpTimeReference(Instant.ofEpochMilli(bg.timeStamp))
        )
            .setMinimumTimeUnit(TimeUnit.MINUTES)
            .setText("^1")
            .build()

    override fun getProviderCanonicalName(): String = BgRangedComplication::class.java.canonicalName!!
}
