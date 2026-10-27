package app.aaps.wear.alarm

import android.os.Bundle
import android.view.WindowManager
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
 */
class WatchAlarmActivity : WearMetroActivity() {

    @Inject lateinit var controller: WatchAlarmController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (controller.active.value == null) {
            finish()
            return
        }
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                val active by controller.active.collectAsState()
                LaunchedEffect(active) { if (active == null) finish() }
                AlarmScreen(text = active?.text.orEmpty(), onDismiss = { controller.dismiss() })
            }
        }
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
