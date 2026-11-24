package os.hyaline.phone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.InCallService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import os.hyaline.core.theme.*

class HyalineInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
    }
}

class PhoneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialNumber = intent?.data?.schemeSpecificPart.orEmpty()

        setContent {
            var dialInput by remember { mutableStateOf(initialNumber) }
            val config = remember { HyalineConfig() }

            HyalineTheme(accentHex = "#10b981") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(os1WallpaperBrush(config.os1.wallpaperPreset))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("hyaline Phone", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
                        Text("Standalone ROLE_DIALER • Theme-Synced", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = dialInput.ifEmpty { "Enter number" },
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("*", "0", "#"),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                row.forEach { key ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(64.dp)
                                            .hyalineGlassTile(config.os1.style, Color(0xFF10B981), 0.35f, 12)
                                            .clickable { dialInput += key },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(key, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = { if (dialInput.isNotEmpty()) dialInput = dialInput.dropLast(1) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Delete", color = Color.White)
                            }
                            Button(
                                onClick = {
                                    if (dialInput.isNotBlank()) {
                                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialInput")))
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Call")
                            }
                        }
                    }
                }
            }
        }
    }
}
