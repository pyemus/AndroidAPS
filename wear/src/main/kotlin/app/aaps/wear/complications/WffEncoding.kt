package app.aaps.wear.complications

import androidx.annotation.DrawableRes
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import app.aaps.wear.interaction.utils.Constants

/**
 * Value encodings for the complications used by the Watch Face Format (WFF) watch face (:wear-wff).
 *
 * A WFF face has no code: it can only compare numbers delivered in RANGED_VALUE complications.
 * All thresholds (BG low/high, stale data, loop age) are therefore resolved here and the face
 * only compares the encoded value (see wear-wff/src/main/res/raw/watchface.xml).
 */
object WffEncoding {

    // BG: value = code, min = BG_MIN, max = BG_MAX
    const val BG_LOW = 0f
    const val BG_IN_RANGE = 1f
    const val BG_HIGH = 2f
    const val BG_STALE = 3f
    const val BG_MIN = BG_LOW
    const val BG_MAX = BG_STALE

    // Loop: value = minutes since last loop run clamped to [0, LOOP_STALE_MINUTES], LOOP_UNKNOWN if unknown
    const val LOOP_UNKNOWN = -1f
    const val LOOP_MIN = LOOP_UNKNOWN
    const val LOOP_STALE_MINUTES = 15 // same as the "> 14" red rule of BaseWatchFace

    // Temp target: value = Status.tempTargetLevel (0 profile, 1 loop adjusted, 2 temp target)
    const val TT_MIN = 0f
    const val TT_MAX = 2f

    // Graph photo complication aspect ratio, must match slot 7 of watchface.xml (450 x 160)
    const val GRAPH_FACE_WIDTH = 450
    const val GRAPH_FACE_HEIGHT = 160

    fun bgCode(bg: EventData.SingleBg, now: Long): Float {
        if (bg.timeStamp <= 0L || bg.sgvString == "---" || now - bg.timeStamp > Constants.STALE_MS) return BG_STALE
        return (bg.sgvLevel.coerceIn(-1L, 1L) + 1).toFloat()
    }

    /** Minutes since the last loop run, or -1 if unknown */
    fun loopMinutes(openApsStatus: Long, now: Long): Int =
        if (openApsStatus <= 0L) -1
        else ((now - openApsStatus) / 60_000L).toInt().coerceAtLeast(0)

    fun loopValue(openApsStatus: Long, now: Long): Float {
        val minutes = loopMinutes(openApsStatus, now)
        return if (minutes < 0) LOOP_UNKNOWN else minutes.coerceAtMost(LOOP_STALE_MINUTES).toFloat()
    }

    fun tempTargetValue(tempTargetLevel: Int): Float = tempTargetLevel.toFloat().coerceIn(TT_MIN, TT_MAX)

    /** Same mapping as CustomWatchface.TrendArrowMap */
    @DrawableRes
    fun arrowDrawable(slopeArrow: String): Int = when (slopeArrow.replace("︎", "").trim()) {
        "⇈" -> R.drawable.ic_doubleup
        "↑" -> R.drawable.ic_singleup
        "↗" -> R.drawable.ic_fortyfiveup
        "→" -> R.drawable.ic_flat
        "↘" -> R.drawable.ic_fortyfivedown
        "↓" -> R.drawable.ic_singledown
        "⇊" -> R.drawable.ic_doubledown
        else     -> R.drawable.ic_invalid
    }

    /** Graph bitmap size in pixels for a display of [displayWidth] pixels */
    fun graphSize(displayWidth: Int): Pair<Int, Int> = displayWidth to displayWidth * GRAPH_FACE_HEIGHT / GRAPH_FACE_WIDTH

    fun delta(bg: EventData.SingleBg, detailed: Boolean): String = if (detailed) bg.deltaDetailed else bg.delta
    fun avgDelta(bg: EventData.SingleBg, detailed: Boolean): String = if (detailed) bg.avgDeltaDetailed else bg.avgDelta
}
