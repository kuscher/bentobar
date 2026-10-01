package io.github.kuscher.bentobar.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import io.github.kuscher.bentobar.items.Env
import io.github.kuscher.bentobar.items.Notify
import kotlinx.coroutines.flow.MutableStateFlow

/** What Setup shows as done, read in one go. */
@Immutable
data class SetupState(
    val serviceOn: Boolean = false,
    val notifications: Boolean = false,
    val liveUpdates: Boolean = false,
    val calendar: Boolean = false,
    val exactAlarms: Boolean = false,
    val advancedProtection: Boolean = false,
)

/**
 * The setup state as one observed value. Screens read [state] instead of asking Android while they
 * draw: a value read during composition is never re-read when nothing the composable depends on
 * changes (strong skipping), and on a Googlebook the settings that change it open in their own
 * window while BentoBar stays resumed, so onResume alone isn't enough. [refresh] runs on resume,
 * window focus, permission results, accessibility service changes, and when the service connects
 * or stops.
 */
object Setup {
    val state = MutableStateFlow(SetupState())

    fun refresh(context: Context) {
        val app = context.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        state.value = SetupState(
            serviceOn = MainActivity.serviceOn(app),
            notifications = Notify.allowed(app),
            liveUpdates = runCatching { nm?.canPostPromotedNotifications() == true }.getOrDefault(false),
            calendar = app.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED,
            exactAlarms = app.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true,
            advancedProtection = Env.advancedProtection(),
        )
    }
}
