package os.hyaline.launcher

import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import os.hyaline.core.privilege.Os2EngineController
import os.hyaline.core.privilege.PrivilegeStatus
import os.hyaline.core.theme.*
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
private fun ToolTopBar(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
            Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
        }
        OutlinedButton(onClick = onBack) {
            Text("← Home", color = Color.White)
        }
    }
}

@Composable
fun HyalineInternalToolRouter(
    toolUri: String,
    config: HyalineConfig,
    notes: List<NoteEntry>,
    clips: List<String>,
    repo: LauncherRepository,
    privilegeStatus: PrivilegeStatus,
    engine: Os2EngineController,
    isNotificationListenerActive: Boolean,
    onUpdateConfig: (HyalineConfig) -> Unit,
    onUpdateNotes: (List<NoteEntry>) -> Unit,
    onUpdateClips: (List<String>) -> Unit,
    onImportJson: () -> Unit,
    onExportJson: () -> Unit,
    onResetDefaultTiles: () -> Unit,
    onOpenOobe: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(os1WallpaperBrush(config.os1.wallpaperPreset))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        when (toolUri) {
            HyalinePackages.INTERNAL_NOTES -> HyalineNotesScreen(notes, onUpdateNotes, onBack)
            HyalinePackages.INTERNAL_CLOCK -> HyalineClockScreen(config, onBack)
            HyalinePackages.INTERNAL_CALC -> HyalineCalcScreen(config, onBack)
            HyalinePackages.INTERNAL_PULSE -> HyalinePulseScreen(repo, isNotificationListenerActive, onBack)
            HyalinePackages.INTERNAL_FILES -> HyalineFilesScreen(privilegeStatus, engine, onBack)
            HyalinePackages.INTERNAL_TOOLS -> HyalineQuickToolsScreen(clips, onUpdateClips, onBack)
            else -> HyalineStudioSettingsScreen(
                config = config,
                isNotificationListenerActive = isNotificationListenerActive,
                onUpdateConfig = onUpdateConfig,
                onImportJson = onImportJson,
                onExportJson = onExportJson,
                onResetDefaultTiles = onResetDefaultTiles,
                onOpenOobe = onOpenOobe,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun HyalineNotesScreen(
    notes: List<NoteEntry>,
    onUpdateNotes: (List<NoteEntry>) -> Unit,
    onBack: () -> Unit,
) {
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    val activeNote = notes.firstOrNull { it.id == editingNoteId }

    Column(modifier = Modifier.fillMaxSize()) {
        ToolTopBar("hyaline Notes", "Persistent scratchpad • Pinned note feeds Live Tile", onBack)

        if (activeNote != null) {
            var title by remember(activeNote.id) { mutableStateOf(activeNote.title) }
            var body by remember(activeNote.id) { mutableStateOf(activeNote.body) }
            var pinned by remember(activeNote.id) { mutableStateOf(activeNote.pinnedToTile) }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title", color = Color.White) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Note content", color = Color.White) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                Text("Pin snippet to Start Live Tile", color = Color.White, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val updated = notes.map { n ->
                            if (n.id == activeNote.id) {
                                n.copy(title = title.ifBlank { "Untitled" }, body = body, pinnedToTile = pinned, updatedAtMs = System.currentTimeMillis())
                            } else if (pinned) {
                                n.copy(pinnedToTile = false)
                            } else {
                                n
                            }
                        }
                        onUpdateNotes(updated)
                        editingNoteId = null
                    },
                ) { Text("Save Note") }
                OutlinedButton(
                    onClick = {
                        onUpdateNotes(notes.filterNot { it.id == activeNote.id })
                        editingNoteId = null
                    },
                ) { Text("Delete", color = Color.White) }
            }
        } else {
            Button(
                onClick = {
                    val created = NoteEntry(
                        id = UUID.randomUUID().toString(),
                        title = "New Note",
                        body = "",
                        pinnedToTile = notes.isEmpty(),
                    )
                    onUpdateNotes(listOf(created) + notes)
                    editingNoteId = created.id
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("+ New Note") }

            Spacer(Modifier.height(10.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(notes, key = { it.id }) { note ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingNoteId = note.id },
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(note.title, color = Color.White, fontWeight = FontWeight.Bold)
                                if (note.pinnedToTile) {
                                    Text("★ Live Tile", color = Color(0xFFBAE6FD), fontSize = 11.sp)
                                }
                            }
                            if (note.body.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(note.body, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 3)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HyalineClockScreen(config: HyalineConfig, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var stopwatchRunning by remember { mutableStateOf(false) }
    var stopwatchMs by remember { mutableLongStateOf(0L) }
    var laps by remember { mutableStateOf(listOf<Long>()) }

    var focusRunning by remember { mutableStateOf(false) }
    var focusRemainingSec by remember { mutableIntStateOf(25 * 60) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            nowTick = System.currentTimeMillis()
        }
    }

    LaunchedEffect(stopwatchRunning) {
        while (stopwatchRunning) {
            delay(100L)
            stopwatchMs += 100L
        }
    }

    LaunchedEffect(focusRunning) {
        while (focusRunning && focusRemainingSec > 0) {
            delay(1000L)
            focusRemainingSec -= 1
            if (focusRemainingSec <= 0) focusRunning = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ToolTopBar("Clock & Focus", "World Clock • Stopwatch • Pomodoro Focus", onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("World Clock", "Stopwatch", "Focus Timer").forEachIndexed { idx, label ->
                FilterChip(
                    selected = tab == idx,
                    onClick = { tab = idx },
                    label = { Text(label, color = Color.White) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        when (tab) {
            0 -> {
                val fmt = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
                val zones = listOf(
                    "Local Time" to ZoneId.systemDefault().id,
                    "UTC" to "UTC",
                    "Athens / EET" to "Europe/Athens",
                    "London" to "Europe/London",
                    "New York" to "America/New_York",
                    "Tokyo" to "Asia/Tokyo",
                )
                // Reference nowTick so Compose recomposes each second
                val unused = nowTick
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(zones) { (name, zone) ->
                        val timeStr = runCatching { ZonedDateTime.now(ZoneId.of(zone)).format(fmt) }.getOrDefault("--:--")
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(name, color = Color.White, fontWeight = FontWeight.Medium)
                                Text(timeStr, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            1 -> {
                val totalSec = stopwatchMs / 1000
                val tenths = (stopwatchMs % 1000) / 100
                val formatted = "%02d:%02d.%d".format(totalSec / 60, totalSec % 60, tenths)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(formatted, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { stopwatchRunning = !stopwatchRunning }) {
                            Text(if (stopwatchRunning) "Pause" else "Start")
                        }
                        OutlinedButton(onClick = { if (stopwatchMs > 0) laps = listOf(stopwatchMs) + laps }) {
                            Text("Lap", color = Color.White)
                        }
                        OutlinedButton(onClick = {
                            stopwatchRunning = false
                            stopwatchMs = 0L
                            laps = emptyList()
                        }) {
                            Text("Reset", color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(laps.size) { i ->
                            val ms = laps[i]
                            Text(
                                "Lap ${laps.size - i}: %02d:%02d.%d".format((ms / 1000) / 60, (ms / 1000) % 60, (ms % 1000) / 100),
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            else -> {
                val mins = focusRemainingSec / 60
                val secs = focusRemainingSec % 60
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("%02d:%02d".format(mins, secs), color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 25, 50).forEach { m ->
                            OutlinedButton(onClick = {
                                focusRunning = false
                                focusRemainingSec = m * 60
                            }) { Text("${m}m", color = Color.White) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { focusRunning = !focusRunning }) {
                        Text(if (focusRunning) "Pause Focus" else "Start Focus")
                    }
                }
            }
        }
    }
}

@Composable
private fun HyalineCalcScreen(config: HyalineConfig, onBack: () -> Unit) {
    var display by remember { mutableStateOf("0") }
    var accumulator by remember { mutableStateOf<Double?>(null) }
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var resetNext by remember { mutableStateOf(false) }
    var convInput by remember { mutableStateOf("10") }

    fun applyOp(a: Double, b: Double, op: String): Double = when (op) {
        "+" -> a + b
        "−" -> a - b
        "×" -> a * b
        "÷" -> if (b == 0.0) 0.0 else a / b
        else -> b
    }

    fun formatNum(n: Double): String =
        if (n % 1.0 == 0.0) n.toLong().toString() else "%.6f".format(n).trimEnd('0').trimEnd('.')

    fun onKey(key: String) {
        when (key) {
            "C" -> {
                display = "0"
                accumulator = null
                pendingOp = null
                resetNext = false
            }
            "±" -> {
                val v = display.toDoubleOrNull() ?: return
                display = formatNum(-v)
            }
            "%" -> {
                val v = display.toDoubleOrNull() ?: return
                display = formatNum(v / 100.0)
            }
            "+", "−", "×", "÷" -> {
                val cur = display.toDoubleOrNull() ?: 0.0
                accumulator = if (accumulator != null && pendingOp != null && !resetNext) {
                    applyOp(accumulator!!, cur, pendingOp!!)
                } else {
                    cur
                }
                display = formatNum(accumulator!!)
                pendingOp = key
                resetNext = true
            }
            "=" -> {
                val cur = display.toDoubleOrNull() ?: 0.0
                if (accumulator != null && pendingOp != null) {
                    val res = applyOp(accumulator!!, cur, pendingOp!!)
                    display = formatNum(res)
                    accumulator = null
                    pendingOp = null
                    resetNext = true
                }
            }
            "." -> {
                if (resetNext) {
                    display = "0."
                    resetNext = false
                } else if (!display.contains(".")) {
                    display += "."
                }
            }
            else -> {
                if (display == "0" || resetNext) {
                    display = key
                    resetNext = false
                } else if (display.length < 14) {
                    display += key
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToolTopBar("hyaline Calc", "Metro × Aero Calculator & Quick Converter", onBack)

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.28f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                Text(pendingOp ?: "", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
                Text(display, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace)
            }
        }

        val rows = listOf(
            listOf("C", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "="),
        )
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val spanWeight = if (key == "0") 2f else 1f
                    Box(
                        modifier = Modifier
                            .weight(spanWeight)
                            .height(54.dp)
                            .hyalineGlassTile(config.os1.style, parseHexColor(config.os1.accentHex), 0.42f, 10)
                            .clickable { onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.16f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val v = convInput.toDoubleOrNull() ?: 0.0
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Quick Unit Converter", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedTextField(
                    value = convInput,
                    onValueChange = { convInput = it },
                    label = { Text("Value", color = Color.White) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )
                Text("${"%.2f".format(v)} km = ${"%.2f".format(v * 0.621371)} mi  •  ${"%.2f".format(v)} kg = ${"%.2f".format(v * 2.20462)} lb", color = Color.White, fontSize = 12.sp)
                Text("${"%.1f".format(v)} °C = ${"%.1f".format(v * 9.0 / 5.0 + 32.0)} °F", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HyalinePulseScreen(
    repo: LauncherRepository,
    isNotificationListenerActive: Boolean,
    onBack: () -> Unit,
) {
    var snap by remember { mutableStateOf(repo.readSystemPulse()) }
    LaunchedEffect(Unit) {
        while (true) {
            snap = repo.readSystemPulse()
            delay(5000L)
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolTopBar("System & Battery Pulse", "${Build.MANUFACTURER} ${Build.MODEL} • Android API ${Build.VERSION.SDK_INT}", onBack)

        val metrics = listOf(
            "Battery" to "${snap.batteryPct}% (${if (snap.isCharging) "Charging ⚡" else "Discharging"})",
            "Available RAM" to "${"%.2f".format(snap.availRamGb)} GB free of ${"%.1f".format(snap.totalRamGb)} GB",
            "Internal Storage" to "${"%.1f".format(snap.freeStorageGb)} GB free of ${"%.1f".format(snap.totalStorageGb)} GB",
            "Live Tile Notifications" to if (isNotificationListenerActive) "Connected ✓" else "Permission not granted",
        )
        metrics.forEach { (label, value) ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(label, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                    Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun HyalineFilesScreen(
    privilegeStatus: PrivilegeStatus,
    engine: Os2EngineController,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val initialDir = remember { context.getExternalFilesDir(null) ?: context.filesDir }
    var currentDir by remember { mutableStateOf(initialDir) }
    var rootListing by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val entries = remember(currentDir) {
        currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).orEmpty()
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolTopBar("hyaline Files", "Dual-Mode Explorer • Root/Shizuku unlocks system paths", onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { currentDir.parentFile?.let { currentDir = it; rootListing = null } }) {
                Text("↑ Up", color = Color.White)
            }
            OutlinedButton(onClick = { currentDir = Environment.getExternalStorageDirectory(); rootListing = null }) {
                Text("/sdcard", color = Color.White)
            }
            if (privilegeStatus != PrivilegeStatus.NONE) {
                Button(onClick = {
                    scope.launch {
                        val res = engine.runShellCommand("ls -la /data/local/tmp /sdcard")
                        rootListing = res.output
                    }
                }) {
                    Text("${privilegeStatus.name} ls")
                }
            }
        }

        Text(currentDir.absolutePath, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)

        if (rootListing != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Text(
                    rootListing!!,
                    color = Color(0xFFBAE6FD),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                items(entries) { file: File ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (file.isDirectory) currentDir = file },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("${if (file.isDirectory) "📁" else "📄"} ${file.name}", color = Color.White, fontSize = 13.sp)
                            if (!file.isDirectory) {
                                Text("${file.length() / 1024} KB", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HyalineQuickToolsScreen(
    clips: List<String>,
    onUpdateClips: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var torchOn by remember { mutableStateOf(false) }
    var newClip by remember { mutableStateOf("") }

    fun toggleTorch() {
        runCatching {
            val cm = context.getSystemService(CameraManager::class.java) ?: return
            val camId = cm.cameraIdList.firstOrNull() ?: return
            val next = !torchOn
            cm.setTorchMode(camId, next)
            torchOn = next
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolTopBar("Quick Tools & Clips", "Flashlight • System Shortcuts • Persistent Clip Vault", onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { toggleTorch() }) {
                Text(if (torchOn) "🔦 Torch ON" else "🔦 Torch OFF")
            }
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text("Wi-Fi", color = Color.White) }
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text("Bluetooth", color = Color.White) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newClip,
                onValueChange = { newClip = it },
                label = { Text("Save snippet to Clip Vault", color = Color.White) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )
            Button(onClick = {
                if (newClip.isNotBlank()) {
                    onUpdateClips((listOf(newClip.trim()) + clips).distinct().take(30))
                    newClip = ""
                }
            }) { Text("Save") }
        }

        OutlinedButton(onClick = {
            val cb = context.getSystemService(ClipboardManager::class.java)
            val txt = cb?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
            if (!txt.isNullOrEmpty()) {
                onUpdateClips((listOf(txt) + clips).distinct().take(30))
            }
        }) { Text("Save Current Android Clipboard", color = Color.White) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            items(clips) { clip ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val cb = context.getSystemService(ClipboardManager::class.java)
                            cb?.setPrimaryClip(ClipData.newPlainText("hyalineOS", clip))
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(clip, color = Color.White, fontSize = 13.sp, maxLines = 2, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onUpdateClips(clips.filterNot { it == clip }) }) {
                            Text("✕", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HyalineStudioSettingsScreen(
    config: HyalineConfig,
    isNotificationListenerActive: Boolean,
    onUpdateConfig: (HyalineConfig) -> Unit,
    onImportJson: () -> Unit,
    onExportJson: () -> Unit,
    onResetDefaultTiles: () -> Unit,
    onOpenOobe: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val homeRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {}

    fun requestDefaultHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                homeRoleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        runCatching {
            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ToolTopBar("hyalineOS Studio", "Launcher Defaults • Styles • Glass • Portable JSON", onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { requestDefaultHome() }, modifier = Modifier.weight(1f)) {
                Text("Set Default Launcher")
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isNotificationListenerActive) "Live Badges ✓" else "Enable Live Badges", color = Color.White)
            }
        }

        Text("OS 1 Metro × Frutiger Aero Style", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Os1Style.entries.forEach { style ->
            val selected = config.os1.style == style
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUpdateConfig(config.copy(os1 = config.os1.copy(style = style))) },
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(style.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(style.subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
        }

        Text("Aero Accent Tint", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HyalineConfig.accentPalette.forEach { hex ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(hex))
                        .clickable { onUpdateConfig(config.copy(os1 = config.os1.copy(accentHex = hex))) },
                )
            }
        }

        Text("Glass Opacity: ${(config.os1.glassOpacity * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
        Slider(
            value = config.os1.glassOpacity,
            onValueChange = { onUpdateConfig(config.copy(os1 = config.os1.copy(glassOpacity = it))) },
            valueRange = 0.18f..0.90f,
        )

        Text("Tile Corner Radius: ${config.os1.tileRadiusPx} dp", color = Color.White, fontSize = 13.sp)
        Slider(
            value = config.os1.tileRadiusPx.toFloat(),
            onValueChange = { onUpdateConfig(config.copy(os1 = config.os1.copy(tileRadiusPx = it.toInt()))) },
            valueRange = 0f..24f,
        )

        Text("Wallpaper", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HyalineConfig.aeroWallpapers.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { (id, label) ->
                        FilterChip(
                            selected = config.os1.wallpaperPreset == id,
                            onClick = { onUpdateConfig(config.copy(os1 = config.os1.copy(wallpaperPreset = id))) },
                            label = { Text(label, color = Color.White) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onImportJson, modifier = Modifier.weight(1f)) {
                Text("Import JSON", color = Color.White)
            }
            OutlinedButton(onClick = onExportJson, modifier = Modifier.weight(1f)) {
                Text("Export JSON", color = Color.White)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenOobe, modifier = Modifier.weight(1f)) {
                Text("Re-run OOBE", color = Color.White)
            }
            OutlinedButton(onClick = onResetDefaultTiles, modifier = Modifier.weight(1f)) {
                Text("Reset Start Tiles", color = Color.White)
            }
        }
    }
}
