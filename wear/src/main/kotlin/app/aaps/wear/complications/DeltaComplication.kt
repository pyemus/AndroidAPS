package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import dagger.android.AndroidInjection
import javax.inject.Inject

/**
 * Delta complication
 *
 * SHORT_TEXT: text = delta, title = average delta (detailed values if "show detailed delta" is enabled)
 */
class DeltaComplication : ModernBaseComplicationProviderService() {

    @Inject lateinit var sp: SP

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
        val detailed = sp.getBoolean(R.string.key_show_detailed_delta, false)
        val delta = WffEncoding.delta(bg, detailed)
        val avgDelta = WffEncoding.avgDelta(bg, detailed)
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = delta).build(),
                    contentDescription = PlainComplicationText.Builder(text = "Delta $delta average $avgDelta").build()
                )
                    .setTitle(PlainComplicationText.Builder(text = avgDelta).build())
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                        -> {
                aapsLogger.warn(LTag.WEAR, "DeltaComplication unexpected type: $type")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = DeltaComplication::class.java.canonicalName!!
}
