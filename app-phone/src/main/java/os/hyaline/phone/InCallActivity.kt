package os.hyaline.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import os.hyaline.core.theme.*

data class ActiveCallSnapshot(
    val number: String,
    val displayName: String?,
    val state: Int,
    val connectTimeMillis: Long,
    val isMuted: Boolean = false,
    val isSpeaker: Boolean = false,
)

private fun Call.compatState(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        details.state
    } else {
        @Suppress("DEPRECATION")
        state
    }

object CallSessionStore {
    private val _activeCall = MutableStateFlow<ActiveCallSnapshot?>(null)
    val activeCall: StateFlow<ActiveCallSnapshot?> = _activeCall.asStateFlow()

    internal var serviceRef: HyalineInCallService? = null
    internal var currentCall: Call? = null

    fun update(snapshot: ActiveCallSnapshot?) {
        _activeCall.value = snapshot
    }

    fun answer() {
        currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun hangupOrReject() {
        val c = currentCall ?: return
        if (c.compatState() == Call.STATE_RINGING) {
            c.reject(false, null)
        } else {
            c.disconnect()
        }
    }

    fun toggleMute() {
        val svc = serviceRef ?: return
        val cur = _activeCall.value ?: return
        val next = !cur.isMuted
        svc.setMuted(next)
        _activeCall.value = cur.copy(isMuted = next)
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val svc = serviceRef ?: return
        val cur = _activeCall.value ?: return
        val nextSpeaker = !cur.isSpeaker
        svc.setAudioRoute(
            if (nextSpeaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE,
        )
        _activeCall.value = cur.copy(isSpeaker = nextSpeaker)
    }

    fun toggleHold() {
        val c = currentCall ?: return
        if (c.compatState() == Call.STATE_HOLDING) c.unhold() else c.hold()
    }

    fun sendDtmf(digit: Char) {
        val c = currentCall ?: return
        c.playDtmfTone(digit)
        c.stopDtmfTone()
    }
}

class HyalineInCallService : InCallService() {

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            syncCallSnapshot(call)
            if (state == Call.STATE_DISCONNECTED) {
                cancelCallNotification()
                CallSessionStore.currentCall = null
                CallSessionStore.update(null)
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details?) {
            syncCallSnapshot(call)
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallSessionStore.serviceRef = this
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallSessionStore.currentCall = call
        call.registerCallback(callCallback)
        syncCallSnapshot(call)
        postCallNotification(call)

        val uiIntent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { startActivity(uiIntent) }
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callCallback)
        if (CallSessionStore.currentCall == call) {
            // Emit Live Tile update if the call ended missed
            if (call.compatState() == Call.STATE_DISCONNECTED && call.details.connectTimeMillis == 0L) {
                val num = call.details.handle?.schemeSpecificPart ?: "Unknown"
                sendBroadcast(
                    Intent(HyalinePackages.ACTION_LIVE_TILE_UPDATE).apply {
                        putExtra(HyalinePackages.EXTRA_TILE_PACKAGE, packageName)
                        putExtra(HyalinePackages.EXTRA_TILE_HEADLINE, "Missed call")
                        putExtra(HyalinePackages.EXTRA_TILE_SUBTEXT, num)
                        putExtra(HyalinePackages.EXTRA_TILE_BADGE, 1)
                    },
                )
            }
            CallSessionStore.currentCall = null
            CallSessionStore.update(null)
            cancelCallNotification()
        }
        super.onCallRemoved(call)
    }

    @Deprecated("Deprecated in Android API 34")
    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        val cur = CallSessionStore.activeCall.value ?: return
        if (audioState != null) {
            CallSessionStore.update(
                cur.copy(
                    isMuted = audioState.isMuted,
                    isSpeaker = audioState.route == CallAudioState.ROUTE_SPEAKER,
                ),
            )
        }
    }

    override fun onDestroy() {
        if (CallSessionStore.serviceRef == this) {
            CallSessionStore.serviceRef = null
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun syncCallSnapshot(call: Call) {
        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
        val name = lookupContactName(this, number)
        val audio = callAudioState
        CallSessionStore.update(
            ActiveCallSnapshot(
                number = number,
                displayName = name,
                state = call.compatState(),
                connectTimeMillis = call.details.connectTimeMillis,
                isMuted = audio?.isMuted ?: false,
                isSpeaker = audio?.route == CallAudioState.ROUTE_SPEAKER,
            ),
        )
    }

    private fun postCallNotification(call: Call) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channelId = "hyaline_calls"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Active & Incoming Calls", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(
            this,
            100,
            Intent(this, InCallActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val num = call.details.handle?.schemeSpecificPart ?: "Phone Call"
        val isRinging = call.compatState() == Call.STATE_RINGING
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(if (isRinging) "Incoming Call" else "Ongoing Call")
            .setContentText(lookupContactName(this, num) ?: num)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
        nm.notify(9001, notif)
    }

    private fun cancelCallNotification() {
        getSystemService(NotificationManager::class.java)?.cancel(9001)
    }
}

internal fun lookupContactName(context: Context, phoneNumber: String): String? {
    if (phoneNumber.isBlank()) return null
    return runCatching {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber),
        )
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val snap by CallSessionStore.activeCall.collectAsState()
            var showDtmfPad by remember { mutableStateOf(false) }
            var elapsedSec by remember { mutableLongStateOf(0L) }

            LaunchedEffect(snap) {
                if (snap == null) {
                    finish()
                }
            }

            LaunchedEffect(snap?.state, snap?.connectTimeMillis) {
                while (snap?.state == Call.STATE_ACTIVE) {
                    val base = snap?.connectTimeMillis?.takeIf { it > 0L } ?: System.currentTimeMillis()
                    elapsedSec = ((System.currentTimeMillis() - base) / 1000L).coerceAtLeast(0L)
                    delay(1000L)
                }
            }

            val current = snap ?: return@setContent
            val stateLabel = when (current.state) {
                Call.STATE_RINGING -> "Incoming Call..."
                Call.STATE_DIALING, Call.STATE_CONNECTING -> "Dialing..."
                Call.STATE_HOLDING -> "On Hold"
                Call.STATE_ACTIVE -> "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)
                else -> "Ending..."
            }

            HyalineTheme(accentHex = "#10b981") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(os1WallpaperBrush("aurora-sky"))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 32.dp)) {
                        Text(stateLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            current.displayName ?: current.number,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Light,
                        )
                        if (current.displayName != null) {
                            Text(current.number, color = Color.White.copy(alpha = 0.78f), fontSize = 15.sp)
                        }
                    }

                    if (showDtmfPad && current.state != Call.STATE_RINGING) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                listOf('1', '2', '3'),
                                listOf('4', '5', '6'),
                                listOf('7', '8', '9'),
                                listOf('*', '0', '#'),
                            ).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    row.forEach { d ->
                                        OutlinedButton(
                                            onClick = { CallSessionStore.sendDtmf(d) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text("$d", color = Color.White, fontSize = 20.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (current.state == Call.STATE_RINGING) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Button(
                                onClick = { CallSessionStore.hangupOrReject() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                modifier = Modifier.weight(1f).height(64.dp),
                            ) {
                                Text("Decline", fontSize = 18.sp)
                            }
                            Button(
                                onClick = { CallSessionStore.answer() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier.weight(1f).height(64.dp),
                            ) {
                                Text("Answer", fontSize = 18.sp)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                FilterChip(
                                    selected = current.isMuted,
                                    onClick = { CallSessionStore.toggleMute() },
                                    label = { Text(if (current.isMuted) "Muted" else "Mute", color = Color.White) },
                                    modifier = Modifier.weight(1f),
                                )
                                FilterChip(
                                    selected = current.isSpeaker,
                                    onClick = { CallSessionStore.toggleSpeaker() },
                                    label = { Text("Speaker", color = Color.White) },
                                    modifier = Modifier.weight(1f),
                                )
                                FilterChip(
                                    selected = current.state == Call.STATE_HOLDING,
                                    onClick = { CallSessionStore.toggleHold() },
                                    label = { Text("Hold", color = Color.White) },
                                    modifier = Modifier.weight(1f),
                                )
                                FilterChip(
                                    selected = showDtmfPad,
                                    onClick = { showDtmfPad = !showDtmfPad },
                                    label = { Text("Keypad", color = Color.White) },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Button(
                                onClick = { CallSessionStore.hangupOrReject() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth().height(60.dp),
                            ) {
                                Text("End Call", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
