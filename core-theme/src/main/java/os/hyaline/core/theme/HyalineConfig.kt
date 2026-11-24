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

    const val ACTION_LIVE_TILE_UPDATE = "os.hyaline.action.LIVE_TILE_UPDATE"
    const val EXTRA_TILE_PACKAGE = "extra_tile_package"
    const val EXTRA_TILE_HEADLINE = "extra_tile_headline"
    const val EXTRA_TILE_SUBTEXT = "extra_tile_subtext"
    const val EXTRA_TILE_BADGE = "extra_tile_badge"
}

@Serializable
enum class Os1Style(val wireId: String, val displayName: String) {
    @SerialName("aero-glass-metro")
    AERO_GLASS_METRO("aero-glass-metro", "Aero-Glass Metro"),

    @SerialName("glossy-neo-metro")
    GLYSSY_NEO_METRO("glossy-neo-metro", "Glossy Neo-Metro"),

    @SerialName("minimal-frosted-aero")
    MINIMAL_FROSTED_AERO("minimal-frosted-aero", "Minimal Frosted Aero"),

    @SerialName("metro-aero-gadgets")
    METRO_AERO_GADGETS("metro-aero-gadgets", "Metro + Aero Gadgets"),
}

@Serializable
enum class Os2Style(val wireId: String, val displayName: String) {
    @SerialName("smoked-aero-desktop")
    SMOKED_AERO_DESKTOP("smoked-aero-desktop", "Smoked Aero Desktop"),

    @SerialName("stealth-obsidian-vault")
    STEALTH_OBSIDIAN_VAULT("stealth-obsidian-vault", "Stealth Obsidian Vault"),

    @SerialName("chameleon-persona-spaces")
    CHAMELEON_PERSONA_SPACES("chameleon-persona-spaces", "Chameleon Personas"),

    @SerialName("metro-aero-pro")
    METRO_AERO_PRO("metro-aero-pro", "Metro-Aero Pro"),
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
    SECRET_DRAWER_CODE("secret-drawer-code", "A–Z Secret Code"),
}

@Serializable
enum class TileSize(val wireId: String, val cols: Int, val rows: Int) {
    @SerialName("1x1")
    SMALL("1x1", 1, 1),

    @SerialName("2x2")
    MEDIUM("2x2", 2, 2),

    @SerialName("4x2")
    WIDE("4x2", 4, 2),
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
    val tiles: List<TileConfig> = defaultTiles(),
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
)

/**
 * Portable configuration schema (`hyalineos-config.v1`) shared between
 * https://kostadinosk.github.io/hyalineos/ and the native Android suite.
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

        fun fromJson(raw: String): Result<HyalineConfig> = runCatching {
            json.decodeFromString(serializer(), raw)
        }

        fun toJson(config: HyalineConfig): String =
            json.encodeToString(serializer(), config)

        fun defaultTiles(): List<TileConfig> = listOf(
            TileConfig("phone", "hyaline Phone", HyalinePackages.PHONE, "📞", TileSize.MEDIUM, "#10b981"),
            TileConfig("messages", "hyaline Messages", HyalinePackages.MESSAGES, "💬", TileSize.MEDIUM, "#0096c7"),
            TileConfig("pulsemesh", "PulseMesh", HyalinePackages.PULSEMESH, "📡", TileSize.WIDE, "#7c3aed"),
            TileConfig("notes", "hyaline Notes", "${HyalinePackages.LAUNCHER}.notes", "📝", TileSize.MEDIUM, "#0ea5e9"),
            TileConfig("weather", "Weather & Pulse", "${HyalinePackages.LAUNCHER}.pulse", "🌤️", TileSize.MEDIUM, "#0284c7"),
            TileConfig("files", "hyaline Files", "${HyalinePackages.LAUNCHER}.files", "📁", TileSize.SMALL, "#f59e0b"),
            TileConfig("calc", "hyaline Calc", "${HyalinePackages.LAUNCHER}.calc", "🧮", TileSize.SMALL, "#06b6d4"),
            TileConfig("qr", "QR & Voice", "${HyalinePackages.LAUNCHER}.capture", "🎙️", TileSize.MEDIUM, "#14b8a6"),
        )
    }
}
