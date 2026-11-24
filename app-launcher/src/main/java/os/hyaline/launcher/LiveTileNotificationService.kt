package os.hyaline.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveTileUpdate(
    val packageName: String,
    val title: String,
    val text: String,
    val count: Int,
)

/**
 * Feeds real-time unread counts, message previews, and media info into OS 1 Metro Live Tiles.
 */
class LiveTileNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshActiveTiles()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshActiveTiles()
    }

    private fun refreshActiveTiles() {
        val grouped = activeNotifications.orEmpty().groupBy { it.packageName }
        val updates = grouped.mapValues { (pkg, list) ->
            val latest = list.maxByOrNull { it.postTime }?.notification?.extras
            LiveTileUpdate(
                packageName = pkg,
                title = latest?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = latest?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                count = list.size,
            )
        }
        _liveTiles.value = updates
    }

    companion object {
        private val _liveTiles = MutableStateFlow<Map<String, LiveTileUpdate>>(emptyMap())
        val liveTiles: StateFlow<Map<String, LiveTileUpdate>> = _liveTiles.asStateFlow()
    }
}
