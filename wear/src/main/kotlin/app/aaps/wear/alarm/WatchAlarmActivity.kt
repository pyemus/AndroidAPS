package app.aaps.wear.alarm

import android.os.BatteryManager
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import app.aaps.wear.di.WearMetroActivity
import dev.zacsweers.metro.Inject

/**
 * The alarm screen: the alarm text, large, and one Dismiss button.
 *
 * It shows over the lock screen and turns the screen on when the system lets it be launched (see
 * [WatchAlarmController]). It is only a view on the controller's state: Dismiss goes to the
 * controller, and the screen closes by itself when the alarm ends for any reason, also when the
 * launch arrives late, after a dismiss or a time-out.
 *
 * Leaving the screen is a dismiss too. Pressing the crown or swiping the screen away means the
 * wearer is awake and acting, and the Wear OS clock alarm stops the same way. Without this the
 * vibration went on in the background with no visible way to stop it (the Dismiss action on the
 * notification is not something anyone finds half asleep). The screen turning off by itself is not
 * a user action and does not dismiss, so an untouched alarm still ends in the phone ringing.
 */
class WatchAlarmActivity : WearMetroActivity() {

    @Inject lateinit var controller: WatchAlarmController
    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (controller.active.value == null) {
            finish()
            return
        }
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this) {
            controller.dismiss("back gesture on the alarm screen")
            finish()
        }
        setContent {
            MaterialTheme {
                val active by controller.active.collectAsState()
                LaunchedEffect(active) { if (active == null) finish() }
                AlarmScreen(text = active?.text.orEmpty(), onDismiss = { controller.dismiss("Dismiss button") })
            }
        }
    }

    /**
     * The crown (home) button. The screen turning off does not cause this, and the controller's own
     * re-launches of this screen carry FLAG_ACTIVITY_NO_USER_ACTION so they do not either.
     *
     * Any other activity coming on top does cause it, and on the charger Wear OS puts its charging
     * screen on top as soon as the display wakes (seen 2026-11-04: the alarm dismissed itself one
     * second after it started). So while charging this is not taken as a dismiss: a watch left on
     * the charger at night keeps ringing to its time-out and the phone rings, which is the point.
     * On the wrist nothing comes on top by itself, so there the hint is the crown.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (onPower()) {
            aapsLogger.info(LTag.WEAR, "Watch alarm screen left while on the charger (charging overlay?), not a dismiss")
            return
        }
        controller.dismiss("crown pressed on the alarm screen")
    }

    /**
     * Whether the watch sits on its charger. The sticky battery broadcast says if power is plugged
     * in; `BatteryManager.isCharging` alone is false once the battery is full, which is exactly the
     * state of a watch that has been on the charger for a while.
     */
    private fun onPower(): Boolean {
        val plugged = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged != 0 || getSystemService(BatteryManager::class.java)?.isCharging == true
    }
}

@Composable
private fun AlarmScreen(text: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.watch_alarm_title),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.watch_alarm_dismiss))
        }
    }
}
