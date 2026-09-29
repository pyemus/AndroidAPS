package app.aaps.wear.watchfaces.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.wear.R
import app.aaps.wear.complications.WffEncoding
import app.aaps.wear.data.ComplicationData
import lecho.lib.hellocharts.view.LineChartView
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders the BG graph (same content as the watch face chart) to a PNG for the graph photo complication.
 *
 * Uses an offscreen [LineChartView] which must be created and drawn on the main thread.
 */
@Singleton
class GraphBitmapRenderer @Inject constructor(
    private val context: Context,
    private val sp: SP,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) {

    private var chartView: LineChartView? = null
    private var lastPng: ByteArray? = null

    /**
     * @return PNG bytes of the graph, the previous rendering if this one fails, or null if nothing to show yet
     */
    @MainThread
    fun renderPng(data: ComplicationData): ByteArray? {
        val entries = data.graphData.entries
        if (entries.isEmpty()) return lastPng
        return try {
            val (width, height) = WffEncoding.graphSize(context.resources.displayMetrics.widthPixels)
            val timeframe = sp.getString(R.string.key_chart_time_frame, "3").toIntOrNull() ?: 3
            val treatments = data.treatmentData
            val builder = BgGraphBuilder(
                sp, dateUtil, entries, treatments.predictions, treatments.temps, treatments.basals, treatments.boluses,
                POINT_SIZE,
                "#FFFF00".toColorInt(), // high, as CustomWatchface default
                "#FF0000".toColorInt(), // low
                "#00FF00".toColorInt(), // in range
                Color.WHITE,            // grid
                ContextCompat.getColor(context, R.color.basal_dark),
                ContextCompat.getColor(context, R.color.basal_light),
                Color.MAGENTA,          // bolus
                ContextCompat.getColor(context, R.color.carbs),
                timeframe
            )
            val view = chartView ?: LineChartView(context).also {
                it.isInteractive = false
                it.isZoomEnabled = false
                chartView = it
            }
            view.lineChartData = builder.lineData()
            view.isViewportCalculationEnabled = true
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)

            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val png = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
            bitmap.recycle()
            aapsLogger.debug(LTag.WEAR, "Graph rendered ${width}x$height ${png.size} bytes, ${entries.size} BGs")
            lastPng = png
            png
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "Graph rendering failed", e)
            lastPng
        }
    }

    companion object {

        private const val POINT_SIZE = 2
    }
}
