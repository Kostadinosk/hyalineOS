package os.hyaline.launcher

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.hyaline.core.theme.HyalinePackages
import os.hyaline.core.theme.TileConfig
import os.hyaline.core.theme.TileSize
import java.util.concurrent.ConcurrentHashMap

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val componentName: ComponentName?,
    val icon: ImageBitmap?,
    val fallbackGlyph: String = "📱",
    val isInternalTool: Boolean = false,
    val isSystemApp: Boolean = false,
) {
    val sectionLetter: String
        get() {
            val first = label.trim().firstOrNull()?.uppercaseChar() ?: '#'
            return if (first in 'A'..'Z') first.toString() else "#"
        }
}

data class SystemPulseSnapshot(
    val batteryPct: Int,
    val isCharging: Boolean,
    val batteryTempC: Float,
    val availRamGb: Float,
    val totalRamGb: Float,
    val freeStorageGb: Float,
    val totalStorageGb: Float,
)

/**
 * Discovers all installed launchable Android apps, caches real vector/adaptive app icons,
 * launches packages reliably, expands the Android notification shade, and computes smart
 * initial Start tiles mapped to the user's real installed apps.
 */
class LauncherRepository(private val context: Context) {

    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    val builtInTools: List<InstalledAppInfo> = listOf(
        InstalledAppInfo("hyaline Notes", HyalinePackages.INTERNAL_NOTES, null, null, "📝", isInternalTool = true),
        InstalledAppInfo("hyaline Clock & Focus", HyalinePackages.INTERNAL_CLOCK, null, null, "⏱️", isInternalTool = true),
        InstalledAppInfo("hyaline Calc & Convert", HyalinePackages.INTERNAL_CALC, null, null, "🧮", isInternalTool = true),
        InstalledAppInfo("hyaline Files", HyalinePackages.INTERNAL_FILES, null, null, "📁", isInternalTool = true),
        InstalledAppInfo("System & Battery Pulse", HyalinePackages.INTERNAL_PULSE, null, null, "⚡", isInternalTool = true),
        InstalledAppInfo("Quick Tools & Torch", HyalinePackages.INTERNAL_TOOLS, null, null, "🔦", isInternalTool = true),
        InstalledAppInfo("hyalineOS Studio & Settings", HyalinePackages.INTERNAL_SETTINGS, null, null, "⚙️", isInternalTool = true),
    )

    suspend fun loadAllApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val selfPkg = context.packageName
        val discovered = mutableMapOf<String, InstalledAppInfo>()

        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (launcherApps != null) {
            runCatching {
                val activities = launcherApps.getActivityList(null, Process.myUserHandle())
                for (info in activities) {
                    val pkg = info.applicationInfo.packageName
                    if (pkg == selfPkg) continue
                    val label = info.label?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                    val bitmap = iconCache.getOrPut(pkg) {
                        drawableToImageBitmap(info.getBadgedIcon(0))
                    }
                    val isSys = (info.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    discovered.putIfAbsent(
                        pkg,
                        InstalledAppInfo(
                            label = label,
                            packageName = pkg,
                            componentName = info.componentName,
                            icon = bitmap,
                            isSystemApp = isSys,
                        ),
                    )
                }
            }
        }

        if (discovered.isEmpty()) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(mainIntent, 0)
            for (ri in resolved) {
                val ai = ri.activityInfo ?: continue
                val pkg = ai.packageName
                if (pkg == selfPkg) continue
                val label = ri.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                val bitmap = iconCache.getOrPut(pkg) {
                    drawableToImageBitmap(ri.loadIcon(pm))
                }
                val isSys = (ai.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                discovered.putIfAbsent(
                    pkg,
                    InstalledAppInfo(
                        label = label,
                        packageName = pkg,
                        componentName = ComponentName(pkg, ai.name),
                        icon = bitmap,
                        isSystemApp = isSys,
                    ),
                )
            }
        }

        (discovered.values + builtInTools).sortedBy { it.label.lowercase() }
    }

    fun getCachedIcon(packageName: String): ImageBitmap? = iconCache[packageName]

    fun clearIconCache(packageName: String? = null) {
        if (packageName != null) iconCache.remove(packageName) else iconCache.clear()
    }

    /**
     * Builds smart initial Start tiles mapped to REAL installed apps on this specific device
     * (preferring hyaline Phone, hyaline Messages, and PulseMesh when installed, and seamlessly
     * falling back to the device's real default Dialer/SMS/Browser/Camera/Settings/Store).
     */
    fun buildSmartInitialTiles(installedApps: List<InstalledAppInfo>): List<TileConfig> {
        val byPkg = installedApps.associateBy { it.packageName }
        val pm = context.packageManager

        fun resolveDefaultPkg(intent: Intent): String? =
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
                ?.takeIf { it != "android" && byPkg.containsKey(it) }

        val phonePkg = when {
            byPkg.containsKey(HyalinePackages.PHONE) -> HyalinePackages.PHONE
            else -> resolveDefaultPkg(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
                ?: installedApps.firstOrNull { it.packageName.contains("dialer", true) || it.label.equals("Phone", true) }?.packageName
                ?: HyalinePackages.PHONE
        }

        val smsPkg = when {
            byPkg.containsKey(HyalinePackages.MESSAGES) -> HyalinePackages.MESSAGES
            else -> Telephony.Sms.getDefaultSmsPackage(context)?.takeIf { byPkg.containsKey(it) }
                ?: installedApps.firstOrNull { it.packageName.contains("messaging", true) || it.label.contains("Messages", true) }?.packageName
                ?: HyalinePackages.MESSAGES
        }

        val browserPkg = resolveDefaultPkg(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
            ?: installedApps.firstOrNull {
                it.packageName.contains("chrome", true) ||
                    it.packageName.contains("browser", true) ||
                    it.packageName.contains("firefox", true)
            }?.packageName

        val cameraPkg = resolveDefaultPkg(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            ?: installedApps.firstOrNull { it.packageName.contains("camera", true) }?.packageName

        val settingsPkg = resolveDefaultPkg(Intent(Settings.ACTION_SETTINGS))
            ?: "com.android.settings"

        val galleryOrStorePkg = installedApps.firstOrNull {
            it.packageName == "com.android.vending" ||
                it.packageName.contains("photos", true) ||
                it.packageName.contains("gallery", true)
        }?.packageName

        val tiles = mutableListOf<TileConfig>()
        tiles += TileConfig(
            id = "phone",
            label = byPkg[phonePkg]?.label ?: "Phone",
            packageName = phonePkg,
            icon = "📞",
            size = TileSize.MEDIUM,
            accentOverride = "#10b981",
        )
        tiles += TileConfig(
            id = "messages",
            label = byPkg[smsPkg]?.label ?: "Messages",
            packageName = smsPkg,
            icon = "💬",
            size = TileSize.MEDIUM,
            accentOverride = "#0096c7",
        )
        if (byPkg.containsKey(HyalinePackages.PULSEMESH)) {
            tiles += TileConfig(
                id = "pulsemesh",
                label = byPkg[HyalinePackages.PULSEMESH]?.label ?: "PulseMesh",
                packageName = HyalinePackages.PULSEMESH,
                icon = "📡",
                size = TileSize.WIDE,
                accentOverride = "#7c3aed",
            )
        }
        if (browserPkg != null) {
            tiles += TileConfig(
                id = "browser",
                label = byPkg[browserPkg]?.label ?: "Browser",
                packageName = browserPkg,
                icon = "🌐",
                size = TileSize.MEDIUM,
                accentOverride = "#0284c7",
            )
        }
        tiles += TileConfig(
            id = "notes",
            label = "hyaline Notes",
            packageName = HyalinePackages.INTERNAL_NOTES,
            icon = "📝",
            size = TileSize.MEDIUM,
            accentOverride = "#0ea5e9",
        )
        if (cameraPkg != null) {
            tiles += TileConfig(
                id = "camera",
                label = byPkg[cameraPkg]?.label ?: "Camera",
                packageName = cameraPkg,
                icon = "📷",
                size = TileSize.SMALL,
                accentOverride = "#ec4899",
            )
        }
        tiles += TileConfig(
            id = "calc",
            label = "Calc",
            packageName = HyalinePackages.INTERNAL_CALC,
            icon = "🧮",
            size = TileSize.SMALL,
            accentOverride = "#06b6d4",
        )
        if (byPkg.containsKey(settingsPkg)) {
            tiles += TileConfig(
                id = "sys-settings",
                label = byPkg[settingsPkg]?.label ?: "Settings",
                packageName = settingsPkg,
                icon = "⚙️",
                size = TileSize.SMALL,
                accentOverride = "#475569",
            )
        }
        if (galleryOrStorePkg != null) {
            tiles += TileConfig(
                id = "store-or-photos",
                label = byPkg[galleryOrStorePkg]?.label ?: "Apps",
                packageName = galleryOrStorePkg,
                icon = "🛍️",
                size = TileSize.SMALL,
                accentOverride = "#8b5cf6",
            )
        }
        tiles += TileConfig(
            id = "clock",
            label = "Clock & Focus",
            packageName = HyalinePackages.INTERNAL_CLOCK,
            icon = "⏱️",
            size = TileSize.MEDIUM,
            accentOverride = "#0284c7",
        )
        tiles += TileConfig(
            id = "pulse",
            label = "System Pulse",
            packageName = HyalinePackages.INTERNAL_PULSE,
            icon = "⚡",
            size = TileSize.MEDIUM,
            accentOverride = "#14b8a6",
        )
        tiles += TileConfig(
            id = "files",
            label = "Files",
            packageName = HyalinePackages.INTERNAL_FILES,
            icon = "📁",
            size = TileSize.SMALL,
            accentOverride = "#f59e0b",
        )
        tiles += TileConfig(
            id = "tools",
            label = "Quick Tools",
            packageName = HyalinePackages.INTERNAL_TOOLS,
            icon = "🔦",
            size = TileSize.SMALL,
            accentOverride = "#6366f1",
        )
        tiles += TileConfig(
            id = "os-studio",
            label = "OS Studio",
            packageName = HyalinePackages.INTERNAL_SETTINGS,
            icon = "✨",
            size = TileSize.MEDIUM,
            accentOverride = "#00a8e8",
        )
        return tiles
    }

    fun launchApp(targetPackageOrUri: String, onOpenInternal: (String) -> Unit): Boolean {
        if (targetPackageOrUri.startsWith("hyaline://")) {
            onOpenInternal(targetPackageOrUri)
            return true
        }
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (launcherApps != null) {
            val activity = runCatching {
                launcherApps.getActivityList(targetPackageOrUri, Process.myUserHandle()).firstOrNull()
            }.getOrNull()
            if (activity != null) {
                return runCatching {
                    launcherApps.startMainActivity(activity.componentName, Process.myUserHandle(), null, null)
                    true
                }.getOrDefault(false)
            }
        }
        val intent = context.packageManager.getLaunchIntentForPackage(targetPackageOrUri)
            ?: when (targetPackageOrUri) {
                HyalinePackages.PHONE -> Intent(Intent.ACTION_DIAL)
                HyalinePackages.MESSAGES -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
                "com.android.settings" -> Intent(Settings.ACTION_SETTINGS)
                else -> null
            }
            ?: return false

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openAppInfo(packageName: String) {
        if (packageName.startsWith("hyaline://")) return
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun requestUninstall(packageName: String) {
        if (packageName.startsWith("hyaline://")) return
        val intent = Intent(
            Intent.ACTION_DELETE,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    @Suppress("WrongConstant")
    fun expandNotificationShade() {
        runCatching {
            val statusBarService = context.getSystemService("statusbar") ?: return
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            statusBarManager.getMethod("expandNotificationsPanel").invoke(statusBarService)
        }
    }

    fun readSystemPulse(): SystemPulseSnapshot {
        val bm = context.getSystemService(BatteryManager::class.java)
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 100
        val isCharging = bm?.isCharging ?: false

        val am = context.getSystemService(ActivityManager::class.java)
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val gb = 1024f * 1024f * 1024f
        val availRamGb = memInfo.availMem / gb
        val totalRamGb = memInfo.totalMem / gb

        val stat = StatFs(Environment.getDataDirectory().path)
        val freeStorageGb = (stat.availableBlocksLong * stat.blockSizeLong) / gb
        val totalStorageGb = (stat.blockCountLong * stat.blockSizeLong) / gb

        return SystemPulseSnapshot(
            batteryPct = batteryPct,
            isCharging = isCharging,
            batteryTempC = 30.5f,
            availRamGb = availRamGb,
            totalRamGb = totalRamGb,
            freeStorageGb = freeStorageGb,
            totalStorageGb = totalStorageGb,
        )
    }

    private fun drawableToImageBitmap(drawable: Drawable, sizePx: Int = 128): ImageBitmap {
        val w = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(sizePx) ?: sizePx
        val h = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(sizePx) ?: sizePx
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
