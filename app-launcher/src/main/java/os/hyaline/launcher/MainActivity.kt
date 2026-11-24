package os.hyaline.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import os.hyaline.core.privilege.Os2EngineController
import os.hyaline.core.privilege.PrivilegeGate
import os.hyaline.core.privilege.PrivilegeStatus
import os.hyaline.core.theme.*

private enum class ActiveScreen { OOBE, OS1, OS2, DECOY_GUEST }

class MainActivity : ComponentActivity() {

    private var screenOffCallback: (() -> Unit)? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                screenOffCallback?.invoke()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        setContent {
            var config by remember { mutableStateOf(HyalineConfig()) }
            var activeScreen by remember { mutableStateOf(ActiveScreen.OOBE) }
            var privStatus by remember { mutableStateOf(PrivilegeGate.probe()) }
            val liveTiles by LiveTileNotificationService.liveTiles.collectAsState()
            val engine = remember { Os2EngineController() }
            val scope = rememberCoroutineScope()
            val context = LocalContext.current

            // Auto-return to OS 1 when screen turns off
            DisposableEffect(config.os2.autoLockOnScreenOff) {
                screenOffCallback = {
                    if (config.os2.autoLockOnScreenOff && activeScreen == ActiveScreen.OS2) {
                        activeScreen = ActiveScreen.OS1
                    }
                }
                onDispose { screenOffCallback = null }
            }

            // Import hyalineos-config.json exported from https://kostadinosk.github.io/hyalineos/
            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        HyalineConfig.fromJson(reader.readText()).onSuccess { loaded ->
                            config = loaded
                        }
                    }
                }
            }

            HyalineTheme(
                darkOs2 = activeScreen == ActiveScreen.OS2,
                accentHex = if (activeScreen == ActiveScreen.OS2) config.os2.accentHex else config.os1.accentHex,
            ) {
                when (activeScreen) {
                    ActiveScreen.OOBE -> FirstBootOobeScreen(
                        config = config,
                        privilegeStatus = privStatus,
                        onUpdateConfig = { config = it },
                        onReprobePrivilege = {
                            PrivilegeGate.requestShizukuPermission()
                            privStatus = PrivilegeGate.probe()
                        },
                        onImportJson = { importLauncher.launch(arrayOf("application/json")) },
                        onFinish = { activeScreen = ActiveScreen.OS1 },
                    )

                    ActiveScreen.OS1 -> Os1SurfaceLiteScreen(
                        config = config,
                        liveTiles = liveTiles,
                        onLaunchPackage = { pkg ->
                            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                                context.startActivity(it)
                            }
                        },
                        onOpenOobe = { activeScreen = ActiveScreen.OOBE },
                        onEnterOs2 = {
                            privStatus = PrivilegeGate.probe()
                            // Strict privilege gate: OS 2 opens ONLY when Root or Shizuku is granted
                            if (privStatus != PrivilegeStatus.NONE) {
                                activeScreen = ActiveScreen.OS2
                            }
                        },
                    )

                    ActiveScreen.OS2 -> Os2DeepSpaceScreen(
                        config = config,
                        privilegeStatus = privStatus,
                        onToggleFreeze = { pkg, freeze ->
                            scope.launch { engine.setPackageFrozen(pkg, freeze) }
                        },
                        onLaunchFreeform = { pkg ->
                            scope.launch {
                                engine.enableFreeformWindowing()
                                engine.launchInFreeformWindow(context, pkg)
                            }
                        },
                        onLockToOs1 = { activeScreen = ActiveScreen.OS1 },
                    )

                    ActiveScreen.DECOY_GUEST -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(os1WallpaperBrush("aurora-sky"))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Guest Mode", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { activeScreen = ActiveScreen.OS1 }) {
                                Text("Return to OS 1")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenOffReceiver)
    }
}

@Composable
private fun FirstBootOobeScreen(
    config: HyalineConfig,
    privilegeStatus: PrivilegeStatus,
    onUpdateConfig: (HyalineConfig) -> Unit,
    onReprobePrivilege: () -> Unit,
    onImportJson: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(os1WallpaperBrush(config.os1.wallpaperPreset))
            .statusBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("hyalineOS Setup", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Text(
            "Choose your OS 1 Metro × Frutiger Aero style and optionally unlock OS 2 with Root or Shizuku.",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 14.sp,
        )

        Os1Style.entries.forEach { style ->
            val selected = config.os1.style == style
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUpdateConfig(config.copy(os1 = config.os1.copy(style = style))) },
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(style.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (selected) Text("Selected", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xCC071828),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "OS 2 Privilege Gate: ${privilegeStatus.name}",
                    color = Color(0xFF7DD3FC),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (privilegeStatus == PrivilegeStatus.NONE) {
                        "Strict Gate: OS 2 stays completely locked & hidden until Root or Shizuku is granted."
                    } else {
                        "Unlocked via ${privilegeStatus.name}. Engine: ${config.os2.engineMode.displayName}."
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReprobePrivilege) {
                        Text("Check Shizuku / Root", color = Color.White)
                    }
                    OutlinedButton(onClick = onImportJson) {
                        Text("Import Web JSON", color = Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Launch hyalineOS")
        }
    }
}

@Composable
private fun Os1SurfaceLiteScreen(
    config: HyalineConfig,
    liveTiles: Map<String, LiveTileUpdate>,
    onLaunchPackage: (String) -> Unit,
    onOpenOobe: () -> Unit,
    onEnterOs2: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(os1WallpaperBrush(config.os1.wallpaperPreset))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "hyalineOS",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.clickable(onClick = onEnterOs2),
            )
            TextButton(onClick = onOpenOobe) {
                Text("Setup / Styles", color = Color.White)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(
                items = config.os1.tiles,
                key = { it.id },
                span = { GridItemSpan(it.size.cols) },
            ) { tile ->
                val tint = parseHexColor(tile.accentOverride ?: config.os1.accentHex)
                val fg = if (config.os1.style == Os1Style.MINIMAL_FROSTED_AERO) Color.White else contrastingForeground(tint)
                val live = liveTiles[tile.packageName]
                val heightDp = if (tile.size == TileSize.SMALL) 82.dp else 156.dp

                Box(
                    modifier = Modifier
                        .height(heightDp)
                        .hyalineGlassTile(
                            style = config.os1.style,
                            tint = tint,
                            glassOpacity = config.os1.glassOpacity,
                            cornerRadiusDp = config.os1.tileRadiusPx,
                        )
                        .clickable { onLaunchPackage(tile.packageName) }
                        .padding(10.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(tile.icon, fontSize = 22.sp)
                            if (live != null && live.count > 0) {
                                Text("${live.count}", color = fg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        if (tile.size != TileSize.SMALL && live != null) {
                            Text(
                                text = live.text.ifBlank { live.title },
                                color = fg.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                maxLines = 2,
                            )
                        }
                        Text(tile.label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Os2DeepSpaceScreen(
    config: HyalineConfig,
    privilegeStatus: PrivilegeStatus,
    onToggleFreeze: (String, Boolean) -> Unit,
    onLaunchFreeform: (String) -> Unit,
    onLockToOs1: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(os2WallpaperBrush(config.os2.style))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("OS 2 • ${config.os2.style.displayName}", color = Color(0xFFBAE6FD), fontWeight = FontWeight.Bold)
                Text(
                    "Backend: ${privilegeStatus.name} • Mode: ${config.os2.engineMode.displayName}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                )
            }
            Button(onClick = onLockToOs1) {
                Text("Lock → OS 1")
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xAA0C1D30),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pre-included PulseMesh (Deep Node)", color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onLaunchFreeform(HyalinePackages.PULSEMESH) }) {
                        Text("Open in Freeform Window")
                    }
                    OutlinedButton(onClick = { onToggleFreeze(HyalinePackages.PULSEMESH, true) }) {
                        Text("Freeze in OS 1", color = Color(0xFFBAE6FD))
                    }
                }
            }
        }
    }
}
