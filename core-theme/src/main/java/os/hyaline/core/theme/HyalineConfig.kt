package os.hyaline.core.theme

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Shared constants across the hyalineOS modular suite:
 * - Pre-included PulseMesh APK (Kostadinosk/PulseMesh)
 * - Standalone theme-synced hyaline Phone & hyaline Messages APKs
 */
object HyalinePackages {
    const val LAUNCHER = "os.hyaline.launcher"
    const val PHONE = "os.hyaline.phone"
    const val MESSAGES = "os.hyaline.messages"
    const val PULSEMESH = "com.KostadinosK.pulsemesh"

    // Built-in native hyalineOS tools (resolved internally by the launcher)
    const val INTERNAL_NOTES = "hyaline://notes"
    const val INTERNAL_CLOCK = "hyaline://clock"
    const val INTERNAL_CALC = "hyaline://calc"
    const val INTERNAL_PULSE = "hyaline://pulse"
    const val INTERNAL_FILES = "hyaline://files"
    const val INTERNAL_TOOLS = "hyaline://tools"
    const val INTERNAL_SETTINGS = "hyaline://settings"

    const val ACTION_LIVE_TILE_UPDATE = "os.hyaline.action.LIVE_TILE_UPDATE"
    const val EXTRA_TILE_PACKAGE = "extra_tile_package"
    const val EXTRA_TILE_HEADLINE = "extra_tile_headline"
    const val EXTRA_TILE_SUBTEXT = "extra_tile_subtext"
    const val EXTRA_TILE_BADGE = "extra_tile_badge"
}

@Serializable
enum class Os1Style(val wireId: String, val displayName: String, val subtitle: String) {
    @SerialName("aero-glass-metro")
    AERO_GLASS_METRO("aero-glass-metro", "Aero-Glass Metro", "Frosted WP8 Live Tiles with top specular gleam"),

    @SerialName("glossy-neo-metro")
    GLYSSY_NEO_METRO("glossy-neo-metro", "Glossy Neo-Metro", "Vibrant color-blocked tiles with liquid Aero gloss"),

    @SerialName("minimal-frosted-aero")
    MINIMAL_FROSTED_AERO("minimal-frosted-aero", "Minimal Frosted Aero", "Airy ultra-lite translucent glass tiles"),

    @SerialName("metro-aero-gadgets")
    METRO_AERO_GADGETS("metro-aero-gadgets", "Metro + Aero Gadgets", "Live Tiles with Vista/7 glass gadgets & bottom dock"),
}

@Serializable
enum class Os2Style(val wireId: String, val displayName: String, val subtitle: String) {
    @SerialName("smoked-aero-desktop")
    SMOKED_AERO_DESKTOP("smoked-aero-desktop", "Smoked Aero Desktop", "Dark frosted pocket desktop with Start orb & taskbar"),

    @SerialName("stealth-obsidian-vault")
    STEALTH_OBSIDIAN_VAULT("stealth-obsidian-vault", "Stealth Obsidian Vault", "Tactical low-profile obsidian UI for privacy"),

    @SerialName("chameleon-persona-spaces")
    CHAMELEON_PERSONA_SPACES("chameleon-persona-spaces", "Chameleon Personas", "Switchable Vault, Work, Travel & Guest spaces"),

    @SerialName("metro-aero-pro")
    METRO_AERO_PRO("metro-aero-pro", "Metro-Aero Pro", "Midnight sapphire mirror of OS 1 with power tools"),
}

@Serializable
enum class Os2EngineMode(val wireId: String, val displayName: String) {
    @SerialName("app-freezing")
    APP_FREEZING("app-freezing", "App Freezing & Hiding"),

    @SerialName("freeform-windows")
    FREEFORM_WINDOWS("freeform-windows", "Freeform Floating Windows"),
}

@Serializable
enum class DoorTrigger(val wireId: String, val displayName: String) {
    @SerialName("long-press-clock")
    LONG_PRESS_CLOCK("long-press-clock", "Long-Press Clock"),

    @SerialName("visible-dock-pill")
    VISIBLE_DOCK_PILL("visible-dock-pill", "Visible Dock Switcher"),

    @SerialName("secret-drawer-code")
    SECRET_DRAWER_CODE("secret-drawer-code", "A–Z Search Secret Code"),
}

@Serializable
enum class TileSize(val wireId: String, val cols: Int, val rows: Int) {
    @SerialName("1x1")
    SMALL("1x1", 1, 1),

    @SerialName("2x2")
    MEDIUM("2x2", 2, 2),

    @SerialName("4x2")
    WIDE("4x2", 4, 2);

    fun next(): TileSize = when (this) {
        SMALL -> MEDIUM
        MEDIUM -> WIDE
        WIDE -> SMALL
    }
}

@Serializable
data class TileConfig(
    val id: String,
    val label: String,
    val packageName: String,
    val icon: String = "",
    val size: TileSize = TileSize.MEDIUM,
    val accentOverride: String? = null,
)

@Serializable
data class Os1Config(
    val style: Os1Style = Os1Style.AERO_GLASS_METRO,
    val accentHex: String = "#00a8e8",
    val glassOpacity: Float = 0.44f,
    val glassBlurPx: Int = 18,
    val wallpaperPreset: String = "aurora-sky",
    val tileRadiusPx: Int = 10,
    val tiles: List<TileConfig> = HyalineConfig.defaultTiles(),
)

@Serializable
data class Os2Config(
    val requiresPrivilege: String = "root-or-shizuku",
    val style: Os2Style = Os2Style.SMOKED_AERO_DESKTOP,
    val engineMode: Os2EngineMode = Os2EngineMode.APP_FREEZING,
    val doorTrigger: DoorTrigger = DoorTrigger.LONG_PRESS_CLOCK,
    val autoLockOnScreenOff: Boolean = true,
    val decoyPinEnabled: Boolean = true,
    val accentHex: String = "#38bdf8",
    val glassOpacity: Float = 0.62f,
    val activePersona: String = "Private Vault",
    val isolatedPackages: List<String> = emptyList(),
)

/**
 * Portable configuration schema (`hyalineos-config.v1`) shared between
 * https://kostadinosk.github.io/hyalineOS/ and the native Android suite.
 */
@Serializable
data class HyalineConfig(
    val schemaVersion: Int = 1,
    val osName: String = "hyalineOS",
    val os1: Os1Config = Os1Config(),
    val os2: Os2Config = Os2Config(),
) {
    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

        val aeroWallpapers = listOf(
            "system-wallpaper" to "System Wallpaper",
            "aurora-sky" to "Aurora Sky",
            "aqua-bokeh" to "Aqua Bokeh",
            "emerald-meadow" to "Emerald Meadow",
            "sunset-prism" to "Sunset Prism",
            "midnight-glass" to "Midnight Glass",
        )

        val accentPalette = listOf(
            "#00a8e8",
            "#10b981",
            "#0ea5e9",
            "#8b5cf6",
            "#f43f5e",
            "#f59e0b",
            "#14b8a6",
            "#3b82f6",
        )

        fun fromJson(raw: String): Result<HyalineConfig> = runCatching {
            json.decodeFromString(serializer(), raw)
        }

        fun toJson(config: HyalineConfig): String =
            json.encodeToString(serializer(), config)

        fun defaultTiles(): List<TileConfig> = listOf(
            TileConfig("phone", "Phone", HyalinePackages.PHONE, "📞", TileSize.MEDIUM, "#10b981"),
            TileConfig("messages", "Messages", HyalinePackages.MESSAGES, "💬", TileSize.MEDIUM, "#0096c7"),
            TileConfig("pulsemesh", "PulseMesh", HyalinePackages.PULSEMESH, "📡", TileSize.WIDE, "#7c3aed"),
            TileConfig("notes", "hyaline Notes", HyalinePackages.INTERNAL_NOTES, "📝", TileSize.MEDIUM, "#0ea5e9"),
            TileConfig("clock", "Clock & Focus", HyalinePackages.INTERNAL_CLOCK, "⏱️", TileSize.MEDIUM, "#0284c7"),
            TileConfig("calc", "Calc", HyalinePackages.INTERNAL_CALC, "🧮", TileSize.SMALL, "#06b6d4"),
            TileConfig("files", "Files", HyalinePackages.INTERNAL_FILES, "📁", TileSize.SMALL, "#f59e0b"),
            TileConfig("pulse", "System Pulse", HyalinePackages.INTERNAL_PULSE, "⚡", TileSize.MEDIUM, "#14b8a6"),
            TileConfig("tools", "Quick Tools", HyalinePackages.INTERNAL_TOOLS, "🔦", TileSize.SMALL, "#6366f1"),
            TileConfig("settings", "OS Studio", HyalinePackages.INTERNAL_SETTINGS, "⚙️", TileSize.SMALL, "#0284c7"),
        )
    }
}
