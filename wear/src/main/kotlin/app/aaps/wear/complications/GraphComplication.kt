package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import app.aaps.wear.watchfaces.utils.GraphBitmapRenderer
import dagger.android.AndroidInjection
import javax.inject.Inject

/**
 * BG graph complication (PHOTO_IMAGE) for the WFF watch face
 *
 * Renders BG history, predictions, basal and treatments to an image.
 * Updated by DataHandlerWear when new BG / graph / treatment data arrives.
 */
class GraphComplication : ModernBaseComplicationProviderService() {

    @Inject lateinit var graphBitmapRenderer: GraphBitmapRenderer

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
        if (type != ComplicationType.PHOTO_IMAGE) {
            aapsLogger.warn(LTag.WEAR, "GraphComplication unexpected type: $type")
            return null
        }
        // Called on the main thread (base class coroutine scope), required by the chart view
        val png = graphBitmapRenderer.renderPng(data) ?: return null
        return PhotoImageComplicationData.Builder(
            photoImage = Icon.createWithData(png, 0, png.size),
            contentDescription = PlainComplicationText.Builder(text = "Glucose graph").build()
        ).build()
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.PHOTO_IMAGE)
            PhotoImageComplicationData.Builder(
                photoImage = Icon.createWithResource(this, R.drawable.watchface_custom),
                contentDescription = PlainComplicationText.Builder(text = "Glucose graph").build()
            ).build()
        else null

    override fun getComplicationAction(): ComplicationAction = ComplicationAction.NONE
    override fun getProviderCanonicalName(): String = GraphComplication::class.java.canonicalName!!
}
