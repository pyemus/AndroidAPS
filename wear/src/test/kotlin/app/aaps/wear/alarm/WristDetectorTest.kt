package app.aaps.wear.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import app.aaps.wear.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import kotlin.time.Duration.Companion.milliseconds

/** The wrist check against Robolectric's SensorManager. Events are sent by hand, so the answers are exact. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class WristDetectorTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val sensorManager: ShadowSensorManager = shadowOf(context.getSystemService(SensorManager::class.java))

    private fun sut() = WristDetector(context, AAPSLoggerTest())

    private val offBodySensor: Sensor = ShadowSensor.newInstance(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)

    private fun addOffBodySensor() {
        sensorManager.addSensor(offBodySensor)
    }

    private fun sendOffBodyValue(value: Float) {
        val event = SensorEventBuilder.newBuilder()
            .setSensor(offBodySensor)
            .setValues(floatArrayOf(value))
            .setTimestamp(1L)
            .build()
        sensorManager.sendSensorEventToListeners(event)
    }

    @Test
    fun `a watch on the wrist is worn`() = runTest {
        addOffBodySensor()
        val worn = async { sut().isWorn() }
        runCurrent()

        sendOffBodyValue(1f)

        assertThat(worn.await()).isTrue()
    }

    @Test
    fun `a watch off the wrist is not worn`() = runTest {
        addOffBodySensor()
        val worn = async { sut().isWorn() }
        runCurrent()

        sendOffBodyValue(0f)

        assertThat(worn.await()).isFalse()
    }

    @Test
    fun `no sensor means unknown`() = runTest {
        assertThat(sut().isWorn()).isNull()
    }

    @Test
    fun `a sensor that does not answer means unknown, and its listener is removed`() = runTest {
        addOffBodySensor()
        val worn = async { sut().isWorn() }
        runCurrent()

        advanceTimeBy(WristDetector.TIMEOUT_MS.milliseconds + 1.milliseconds)
        runCurrent()

        assertThat(worn.await()).isNull()
        sendOffBodyValue(0f) // must not reach a removed listener or crash
    }
}
