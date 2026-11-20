package app.aaps.wear.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Tells whether the watch is on the wrist, using the off-body sensor (the same one Wear OS uses to
 * lock the watch when it is taken off).
 *
 * The sensor reports on change only, but Android sends its current state once right after a
 * listener registers. So one registration gives the answer, and the listener is removed again.
 */
@SingleIn(AppScope::class)
@Inject
class WristDetector(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) {

    /**
     * True when the watch is worn, false when it is not, and null when this cannot be known: no
     * off-body sensor, or no answer within [TIMEOUT_MS]. A caller must treat null as "maybe worn",
     * so a missing sensor never takes the alarm away from the watch.
     */
    suspend fun isWorn(): Boolean? {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
        if (sensorManager == null || sensor == null) {
            aapsLogger.warn(LTag.WEAR, "No off-body sensor - cannot tell if the watch is worn")
            return null
        }
        val worn = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sensorManager.unregisterListener(this)
                        val value = event.values.firstOrNull()
                        if (continuation.isActive) continuation.resume(value?.let { it >= ON_BODY_THRESHOLD })
                    }

                    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
                }
                continuation.invokeOnCancellation { sensorManager.unregisterListener(listener) }
                val registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, Handler(Looper.getMainLooper()))
                if (!registered && continuation.isActive) continuation.resume(null)
            }
        }
        aapsLogger.info(LTag.WEAR, "Off-body sensor: worn=$worn")
        return worn
    }

    companion object {

        /** The sensor sends its state within milliseconds; this only stops a broken sensor from holding up the alarm. */
        const val TIMEOUT_MS = 3_000L

        /** The sensor reports 1.0 for on the wrist and 0.0 for off. */
        private const val ON_BODY_THRESHOLD = 0.5f
    }
}
