package os.hyaline.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import os.hyaline.core.privilege.Os2EngineController
import os.hyaline.core.privilege.PrivilegeGate
import os.hyaline.core.privilege.PrivilegeStatus
import os.hyaline.core.theme.*
import rikka.shizuku.Shizuku

private enum class RootRoute {
    OOBE,
    OS1_HOME,
    INTERNAL_TOOL,
    OS2_DEEP,
    DECOY_GUEST,
}

class MainActivity : FragmentActivity() {

    private lateinit var prefs: HyalinePreferences
    private lateinit var repo: LauncherRepository

    private var onHomeIntentCallback: (() -> Unit)? = null
    private var onScreenOffCallback: (() -> Unit)? = null
    private var onPackageChangedCallback: ((String?) -> Unit)? = null
    private var onCustomTileBroadcastCallback: ((LiveTileUpdate) -> Unit)? = null
    private var onShizukuChangedCallback: (() -> Unit)? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                onScreenOffCallback?.invoke()
            }
        }
    }

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pkg = intent?.data?.schemeSpecificPart
            onPackageChangedCallback?.invoke(pkg)
        }
    }

    private val liveTileBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != HyalinePackages.ACTION_LIVE_TILE_UPDATE) return
            val pkg = intent.getStringExtra(HyalinePackages.EXTRA_TILE_PACKAGE) ?: return
            val headline = intent.getStringExtra(HyalinePackages.EXTRA_TILE_HEADLINE).orEmpty()
            val subtext = intent.getStringExtra(HyalinePackages.EXTRA_TILE_SUBTEXT).orEmpty()
            val badge = intent.getIntExtra(HyalinePackages.EXTRA_TILE_BADGE, 0)
            onCustomTileBroadcastCallback?.invoke(LiveTileUpdate(pkg, headline, subtext, badge))
        }
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        onShizukuChangedCallback?.invoke()
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs = HyalinePreferences(this)
        repo = LauncherRepository(this)

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        registerReceiver(
            packageChangeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
        )
        ContextCompat.registerReceiver(
            this,
            liveTileBroadcastReceiver,
            IntentFilter(HyalinePackages.ACTION_LIVE_TILE_UPDATE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }

        setContent {
            val scope = rememberCoroutineScope()
            val engine = remember { Os2EngineController() }

            var config by remember { mutableStateOf(prefs.loadConfig()) }
            var route by remember {
                mutableStateOf(if (prefs.isOobeCompleted) RootRoute.OS1_HOME else RootRoute.OOBE)
            }
            var activeToolUri by remember { mutableStateOf(HyalinePackages.INTERNAL_SETTINGS) }
            var showOs2DoorDialog by remember { mutableStateOf(false) }

            var os2Pin by remember { mutableStateOf(prefs.os2Pin) }
            var decoyPin by remember { mutableStateOf(prefs.decoyPin) }
            var notes by remember { mutableStateOf(prefs.loadNotes()) }
            var vaultNotes by remember { mutableStateOf(prefs.loadVaultNotes()) }
            var clips by remember { mutableStateOf(prefs.loadClips()) }

            var allApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
            var privStatus by remember { mutableStateOf(PrivilegeGate.probe()) }
            var customTileMap by remember { mutableStateOf<Map<String, LiveTileUpdate>>(emptyMap()) }
            val notifTiles by LiveTileNotificationService.liveTiles.collectAsState()

            val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

            fun updateAndSaveConfig(next: HyalineConfig) {
                config = next
                prefs.saveConfig(next)
            }

            // Toggle system wallpaper flag dynamically when "system-wallpaper" preset is chosen
            LaunchedEffect(config.os1.wallpaperPreset, route) {
                val wantSysWallpaper = route == RootRoute.OS1_HOME && config.os1.wallpaperPreset == "system-wallpaper"
                if (wantSysWallpaper) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                    window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                }
            }

            // Discover all installed Android apps & auto-populate smart initial Start tiles on very first run
            LaunchedEffect(Unit) {
                val loadedApps = repo.loadAllApps()
                allApps = loadedApps
                if (!prefs.hasSavedConfig()) {
                    val smartTiles = repo.buildSmartInitialTiles(loadedApps)
                    updateAndSaveConfig(config.copy(os1 = config.os1.copy(tiles = smartTiles)))
                }
            }

            // Wire lifecycle / broadcast callbacks
            DisposableEffect(config.os2.autoLockOnScreenOff) {
                onScreenOffCallback = {
                    if (config.os2.autoLockOnScreenOff && route == RootRoute.OS2_DEEP) {
                        route = RootRoute.OS1_HOME
                    }
                }
                onHomeIntentCallback = {
                    showOs2DoorDialog = false
                    if (route == RootRoute.INTERNAL_TOOL || route == RootRoute.DECOY_GUEST) {
                        route = RootRoute.OS1_HOME
                    }
                    if (route == RootRoute.OS1_HOME) {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    }
                }
                onPackageChangedCallback = { pkg ->
                    repo.clearIconCache(pkg)
                    scope.launch { allApps = repo.loadAllApps() }
                }
                onCustomTileBroadcastCallback = { update ->
                    customTileMap = customTileMap + (update.packageName to update)
                }
                onShizukuChangedCallback = {
                    privStatus = PrivilegeGate.probe()
                }
                onDispose {
                    onScreenOffCallback = null
                    onHomeIntentCallback = null
                    onPackageChangedCallback = null
                    onCustomTileBroadcastCallback = null
                    onShizukuChangedCallback = null
                }
            }

            // Import & Export hyalineos-config.json launchers
            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        HyalineConfig.fromJson(reader.readText()).onSuccess { imported ->
                            updateAndSaveConfig(imported)
                        }
                    }
                }
            }

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(HyalineConfig.toJson(config))
                    }
                }
            }

            val isNotificationListenerEnabled = remember(route) {
                val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
                flat.contains(packageName)
            }

            HyalineTheme(
                darkOs2 = route == RootRoute.OS2_DEEP,
                accentHex = if (route == RootRoute.OS2_DEEP) config.os2.accentHex else config.os1.accentHex,
            ) {
                when (route) {
                    RootRoute.OOBE -> FirstBootOobeWizard(
                        config = config,
                        privilegeStatus = privStatus,
                        os2Pin = os2Pin,
                        decoyPin = decoyPin,
                        onUpdateConfig = ::updateAndSaveConfig,
                        onUpdatePins = { p2, dec ->
                            os2Pin = p2
                            decoyPin = dec
                            prefs.os2Pin = p2
                            prefs.decoyPin = dec
                        },
                        onReprobePrivilege = {
                            PrivilegeGate.requestShizukuPermission()
                            privStatus = PrivilegeGate.probe()
                        },
                        onImportJson = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        onFinish = {
                            prefs.isOobeCompleted = true
                            prefs.saveConfig(config)
                            route = RootRoute.OS1_HOME
                        },
                    )

                    RootRoute.OS1_HOME -> Os1LauncherPager(
                        pagerState = pagerState,
                        config = config,
                        allApps = allApps,
                        liveTiles = notifTiles,
                        customLiveTiles = customTileMap,
                        notes = notes,
                        repo = repo,
                        privilegeStatus = privStatus,
                        secretPinCode = os2Pin,
                        onUpdateConfig = ::updateAndSaveConfig,
                        onLaunchTarget = { pkgOrUri ->
                            repo.launchApp(pkgOrUri) { internalUri ->
                                activeToolUri = internalUri
                                route = RootRoute.INTERNAL_TOOL
                            }
                        },
                        onTriggerOs2Door = {
                            privStatus = PrivilegeGate.probe()
                            showOs2DoorDialog = true
                        },
                        onOpenSettings = {
                            activeToolUri = HyalinePackages.INTERNAL_SETTINGS
                            route = RootRoute.INTERNAL_TOOL
                        },
                    )

                    RootRoute.INTERNAL_TOOL -> {
                        BackHandler { route = RootRoute.OS1_HOME }
                        HyalineInternalToolRouter(
                            toolUri = activeToolUri,
                            config = config,
                            notes = notes,
                            clips = clips,
                            repo = repo,
                            privilegeStatus = privStatus,
                            engine = engine,
                            isNotificationListenerActive = isNotificationListenerEnabled,
                            onUpdateConfig = ::updateAndSaveConfig,
                            onUpdateNotes = { updated ->
                                notes = updated
                                prefs.saveNotes(updated)
                            },
                            onUpdateClips = { updated ->
                                clips = updated
                                prefs.saveClips(updated)
                            },
                            onImportJson = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            onExportJson = { exportLauncher.launch("hyalineos-config.json") },
                            onResetDefaultTiles = {
                                val smart = repo.buildSmartInitialTiles(allApps)
                                updateAndSaveConfig(config.copy(os1 = config.os1.copy(tiles = smart)))
                            },
                            onOpenOobe = { route = RootRoute.OOBE },
                            onBack = { route = RootRoute.OS1_HOME },
                        )
                    }

                    RootRoute.OS2_DEEP -> {
                        BackHandler { route = RootRoute.OS1_HOME }
                        Os2DeepWorkspaceScreen(
                            config = config,
                            privilegeStatus = privStatus,
                            allApps = allApps,
                            engine = engine,
                            vaultNotes = vaultNotes,
                            onUpdateConfig = ::updateAndSaveConfig,
                            onUpdateVaultNotes = { txt ->
                                vaultNotes = txt
                                prefs.saveVaultNotes(txt)
                            },
                            onLaunchNormal = { pkg -> repo.launchApp(pkg) {} },
                            onLockToOs1 = { route = RootRoute.OS1_HOME },
                        )
                    }

                    RootRoute.DECOY_GUEST -> {
                        BackHandler { route = RootRoute.OS1_HOME }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(os1WallpaperBrush("aurora-sky"))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Guest Space", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
                                Text(
                                    "Clean restricted profile active.",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                )
                                Button(onClick = { route = RootRoute.OS1_HOME }) {
                                    Text("Exit Guest Space")
                                }
                            }
                        }
                    }
                }

                if (showOs2DoorDialog) {
                    Os2SmartLockDialog(
                        activity = this@MainActivity,
                        privilegeStatus = privStatus,
                        expectedOs2Pin = os2Pin,
                        expectedDecoyPin = decoyPin,
                        decoyEnabled = config.os2.decoyPinEnabled,
                        onUnlockOs2 = {
                            showOs2DoorDialog = false
                            privStatus = PrivilegeGate.probe()
                            if (privStatus != PrivilegeStatus.NONE) {
                                route = RootRoute.OS2_DEEP
                            }
                        },
                        onUnlockDecoy = {
                            showOs2DoorDialog = false
                            route = RootRoute.DECOY_GUEST
                        },
                        onReprobePrivilege = {
                            PrivilegeGate.requestShizukuPermission()
                            privStatus = PrivilegeGate.probe()
                        },
                        onDismiss = { showOs2DoorDialog = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.action == Intent.ACTION_MAIN) {
            onHomeIntentCallback?.invoke()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenOffReceiver) }
        runCatching { unregisterReceiver(packageChangeReceiver) }
        runCatching { unregisterReceiver(liveTileBroadcastReceiver) }
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
    }
}
