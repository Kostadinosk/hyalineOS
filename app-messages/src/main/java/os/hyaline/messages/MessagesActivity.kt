package os.hyaline.messages

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import os.hyaline.core.theme.*

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Persist incoming SMS via Telephony.Sms.Inbox & emit Live Tile broadcast to hyalineOS
    }
}

class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {}
}

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}

class MessagesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val config = remember { HyalineConfig() }
            var draft by remember { mutableStateOf("") }

            HyalineTheme(accentHex = "#0096c7") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(os1WallpaperBrush(config.os1.wallpaperPreset))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("hyaline Messages", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
                    Text("Standalone ROLE_SMS • Theme-Synced with hyalineOS", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hyalineGlassTile(config.os1.style, Color(0xFF0096C7), 0.42f, 12)
                            .padding(14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Ready for default SMS role", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Standard Android Telephony SMS storage — styled in Metro × Frutiger Aero with Live Tile unread updates.",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Compose SMS...") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
