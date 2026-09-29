package app.aaps.wear.complications

import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import app.aaps.wear.interaction.utils.Constants
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WffEncodingTest {

    private val now = 1_790_000_000_000L

    private fun bg(level: Long, timeStamp: Long = now - 60_000L, sgvString: String = "6.5") = EventData.SingleBg(
        dataset = 0,
        timeStamp = timeStamp,
        sgvString = sgvString,
        delta = "+0.1",
        deltaDetailed = "+0.12",
        avgDelta = "+0.2",
        avgDeltaDetailed = "+0.21",
        sgvLevel = level,
        sgv = 117.0,
        high = 180.0,
        low = 72.0
    )

    @Test fun bgCodeFollowsSgvLevel() {
        assertThat(WffEncoding.bgCode(bg(-1), now)).isEqualTo(WffEncoding.BG_LOW)
        assertThat(WffEncoding.bgCode(bg(0), now)).isEqualTo(WffEncoding.BG_IN_RANGE)
        assertThat(WffEncoding.bgCode(bg(1), now)).isEqualTo(WffEncoding.BG_HIGH)
        // out of range levels are clamped
        assertThat(WffEncoding.bgCode(bg(-5), now)).isEqualTo(WffEncoding.BG_LOW)
        assertThat(WffEncoding.bgCode(bg(5), now)).isEqualTo(WffEncoding.BG_HIGH)
    }

    @Test fun bgCodeStale() {
        assertThat(WffEncoding.bgCode(bg(0, timeStamp = now - Constants.STALE_MS - 1), now)).isEqualTo(WffEncoding.BG_STALE)
        assertThat(WffEncoding.bgCode(bg(0, timeStamp = now - Constants.STALE_MS), now)).isEqualTo(WffEncoding.BG_IN_RANGE)
        assertThat(WffEncoding.bgCode(bg(0, timeStamp = 0L), now)).isEqualTo(WffEncoding.BG_STALE)
        assertThat(WffEncoding.bgCode(bg(0, sgvString = "---"), now)).isEqualTo(WffEncoding.BG_STALE)
    }

    @Test fun bgCodeWithinRange() {
        for (level in -1L..1L) {
            val code = WffEncoding.bgCode(bg(level), now)
            assertThat(code).isAtLeast(WffEncoding.BG_MIN)
            assertThat(code).isAtMost(WffEncoding.BG_MAX)
        }
    }

    @Test fun loopValue() {
        assertThat(WffEncoding.loopValue(-1L, now)).isEqualTo(WffEncoding.LOOP_UNKNOWN)
        assertThat(WffEncoding.loopValue(0L, now)).isEqualTo(WffEncoding.LOOP_UNKNOWN)
        assertThat(WffEncoding.loopValue(now - 3 * 60_000L, now)).isEqualTo(3f)
        assertThat(WffEncoding.loopValue(now - 40 * 60_000L, now)).isEqualTo(WffEncoding.LOOP_STALE_MINUTES.toFloat())
        // loop run in the future (clock skew) counts as just now
        assertThat(WffEncoding.loopValue(now + 60_000L, now)).isEqualTo(0f)
        assertThat(WffEncoding.loopMinutes(now - 14 * 60_000L - 59_000L, now)).isEqualTo(14)
    }

    @Test fun tempTargetValue() {
        assertThat(WffEncoding.tempTargetValue(0)).isEqualTo(0f)
        assertThat(WffEncoding.tempTargetValue(1)).isEqualTo(1f)
        assertThat(WffEncoding.tempTargetValue(2)).isEqualTo(2f)
        assertThat(WffEncoding.tempTargetValue(7)).isEqualTo(WffEncoding.TT_MAX)
        assertThat(WffEncoding.tempTargetValue(-3)).isEqualTo(WffEncoding.TT_MIN)
    }

    @Test fun arrowDrawable() {
        assertThat(WffEncoding.arrowDrawable("⇈")).isEqualTo(R.drawable.ic_doubleup)
        assertThat(WffEncoding.arrowDrawable("↑")).isEqualTo(R.drawable.ic_singleup)
        assertThat(WffEncoding.arrowDrawable("↗")).isEqualTo(R.drawable.ic_fortyfiveup)
        assertThat(WffEncoding.arrowDrawable("→")).isEqualTo(R.drawable.ic_flat)
        assertThat(WffEncoding.arrowDrawable("→︎")).isEqualTo(R.drawable.ic_flat)
        assertThat(WffEncoding.arrowDrawable("↘")).isEqualTo(R.drawable.ic_fortyfivedown)
        assertThat(WffEncoding.arrowDrawable("↓")).isEqualTo(R.drawable.ic_singledown)
        assertThat(WffEncoding.arrowDrawable("⇊")).isEqualTo(R.drawable.ic_doubledown)
        assertThat(WffEncoding.arrowDrawable("--")).isEqualTo(R.drawable.ic_invalid)
        assertThat(WffEncoding.arrowDrawable("")).isEqualTo(R.drawable.ic_invalid)
    }

    @Test fun graphSizeKeepsFaceAspectRatio() {
        assertThat(WffEncoding.graphSize(456)).isEqualTo(456 to 162)
        assertThat(WffEncoding.graphSize(450)).isEqualTo(450 to 160)
    }

    @Test fun deltaSelection() {
        val b = bg(0)
        assertThat(WffEncoding.delta(b, detailed = false)).isEqualTo("+0.1")
        assertThat(WffEncoding.delta(b, detailed = true)).isEqualTo("+0.12")
        assertThat(WffEncoding.avgDelta(b, detailed = false)).isEqualTo("+0.2")
        assertThat(WffEncoding.avgDelta(b, detailed = true)).isEqualTo("+0.21")
    }
}
