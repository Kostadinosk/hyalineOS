package os.hyaline.launcher

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import os.hyaline.core.theme.HyalineConfig
import java.io.File

@Serializable
data class NoteEntry(
    val id: String,
    val title: String,
    val body: String,
    val pinnedToTile: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis(),
)

/**
 * Persistent storage for hyalineOS Launcher:
 * - OOBE completion state (runs once on first boot, never resets on Home press!)
 * - Active HyalineConfig (`hyalineos-config.v1` JSON)
 * - OS 2 Smart Lock PIN & Decoy PIN
 * - OS 1 Notes & OS 2 Encrypted/Vault Notes
 * - Saved Quick Clips
 */
class HyalinePreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("hyaline_os_prefs", Context.MODE_PRIVATE)
    private val configFile = File(appContext.filesDir, "hyalineos-config.json")
    private val notesFile = File(appContext.filesDir, "hyaline-notes.json")
    private val vaultNotesFile = File(appContext.filesDir, "hyaline-os2-vault-notes.txt")
    private val clipsFile = File(appContext.filesDir, "hyaline-clips.json")

    var isOobeCompleted: Boolean
        get() = prefs.getBoolean("oobe_completed", false)
        set(value) = prefs.edit().putBoolean("oobe_completed", value).apply()

    var os2Pin: String
        get() = prefs.getString("os2_pin", "1337") ?: "1337"
        set(value) = prefs.edit().putString("os2_pin", value).apply()

    var decoyPin: String
        get() = prefs.getString("decoy_pin", "0000") ?: "0000"
        set(value) = prefs.edit().putString("decoy_pin", value).apply()

    fun hasSavedConfig(): Boolean = configFile.exists()

    fun loadConfig(): HyalineConfig {
        if (!configFile.exists()) return HyalineConfig()
        return runCatching {
            HyalineConfig.fromJson(configFile.readText()).getOrThrow()
        }.getOrDefault(HyalineConfig())
    }

    fun saveConfig(config: HyalineConfig) {
        runCatching {
            configFile.writeText(HyalineConfig.toJson(config))
        }
    }

    fun loadNotes(): List<NoteEntry> {
        if (!notesFile.exists()) {
            return listOf(
                NoteEntry(
                    id = "welcome",
                    title = "Welcome to hyalineOS",
                    body = "• Long-press any Start tile to resize (1x1, 2x2, 4x2), tint, reorder, or unpin.\n• Swipe left for the full A–Z App Drawer.\n• Swipe down on Start to pull down notifications.",
                    pinnedToTile = true,
                ),
            )
        }
        return runCatching {
            HyalineConfig.json.decodeFromString<List<NoteEntry>>(notesFile.readText())
        }.getOrDefault(emptyList())
    }

    fun saveNotes(notes: List<NoteEntry>) {
        runCatching {
            notesFile.writeText(HyalineConfig.json.encodeToString(notes))
        }
    }

    fun loadVaultNotes(): String =
        if (vaultNotesFile.exists()) runCatching { vaultNotesFile.readText() }.getOrDefault("") else ""

    fun saveVaultNotes(text: String) {
        runCatching { vaultNotesFile.writeText(text) }
    }

    fun loadClips(): List<String> {
        if (!clipsFile.exists()) return emptyList()
        return runCatching {
            HyalineConfig.json.decodeFromString<List<String>>(clipsFile.readText())
        }.getOrDefault(emptyList())
    }

    fun saveClips(clips: List<String>) {
        runCatching {
            clipsFile.writeText(HyalineConfig.json.encodeToString(clips))
        }
    }
}
