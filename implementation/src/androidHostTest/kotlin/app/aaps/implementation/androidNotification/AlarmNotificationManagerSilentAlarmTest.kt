package app.aaps.implementation.androidNotification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The notification of an alarm with a sound, as "Pump unreachable", from `postSilentAlarmNotification`.
 *
 * It was ongoing and had no delete intent. On the lock screen it could not be swiped at all. On an
 * unlocked phone Android 14 let the user swipe it away, but nobody was told: the sound went on and the
 * alarm stayed in the app. The only way to stop the sound was to open AAPS and press Dismiss.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmNotificationManagerSilentAlarmTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val swipeIntent: PendingIntent =
        PendingIntent.getBroadcast(context, KEY, Intent("swiped").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)

    private fun sut(): AlarmNotificationManager {
        val iconsProvider: IconsProvider = mock()
        whenever(iconsProvider.getNotificationIcon()).thenReturn(android.R.drawable.ic_dialog_alert)
        whenever(iconsProvider.getIcon()).thenReturn(android.R.drawable.ic_dialog_alert)
        return AlarmNotificationManager(
            context = context,
            aapsLogger = mock<AAPSLogger>(),
            preferences = mock<Preferences>(),
            iconsProvider = iconsProvider,
            uiInteractionProvider = { mock<UiInteraction>() },
            notificationHolderProvider = { mock<NotificationHolder>() },
            alarmSoundPlayer = mock<AlarmSoundPlayer>(),
            rh = mock<ResourceHelper>()
        )
    }

    private fun postedNotification(): Notification? =
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .getNotification(AlarmNotificationManager.SOUND_ID_OFFSET + KEY)

    private fun AlarmNotificationManager.post() =
        postSilentAlarmNotification(notificationKey = KEY, title = "Urgent alarm", body = "Pump unreachable", urgent = true, deleteIntent = swipeIntent)

    @Test
    fun aSwipeIsReportedThroughTheDeleteIntent() {
        sut().post()

        assertThat(checkNotNull(postedNotification()).deleteIntent).isEqualTo(swipeIntent)
    }

    /** An ongoing notification cannot be swiped on the lock screen, and before Android 14 not at all. */
    @Test
    fun theNotificationIsNotOngoing() {
        sut().post()

        assertThat(checkNotNull(postedNotification()).flags and Notification.FLAG_ONGOING_EVENT).isEqualTo(0)
    }

    /** A notification that is not ongoing is copied to the watch by Wear OS, and the watch already shows this alarm. */
    @Test
    fun theNotificationStaysOnThePhone() {
        sut().post()

        assertThat(checkNotNull(postedNotification()).flags and Notification.FLAG_LOCAL_ONLY).isNotEqualTo(0)
    }

    /** The other way round still works: an alarm dismissed in the app takes its notification away. */
    @Test
    fun cancellingTheAlarmRemovesTheNotification() {
        val sut = sut()
        sut.post()

        sut.cancelSoundAlarm(KEY)

        assertThat(postedNotification()).isNull()
    }

    private companion object {

        const val KEY = 7
    }
}
