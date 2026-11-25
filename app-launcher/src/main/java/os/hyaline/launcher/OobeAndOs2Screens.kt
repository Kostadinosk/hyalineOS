package os.hyaline.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import os.hyaline.core.privilege.Os2EngineController
import os.hyaline.core.privilege.PrivilegeStatus
import os.hyaline.core.theme.*

@Composable
fun FirstBootOobeWizard(
    config: HyalineConfig,
    privilegeStatus: PrivilegeStatus,
    os2Pin: String,
    decoyPin: String,
    onUpdateConfig: (HyalineConfig) -> Unit,
    onUpdatePins: (String, String) -> Unit,
    onReprobePrivilege: () -> Unit,
    onImportJson: () -> Unit,
    onFinish: () -> Unit,
) {
    var step by remember { mutableIntStateOf(1) }
    var draftOs2Pin by remember { mutableStateOf(os2Pin) }
    var draftDecoyPin by remember { mutableStateOf(decoyPin) }
    val context = LocalContext.current

    val homeRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(os1WallpaperBrush(config.os1.wallpaperPreset))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
    ) {
        Text("hyalineOS Setup", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Text("Step $step of 4", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (step) {
                1 -> {
                    Text(
                        "Choose your OS 1 Metro × Frutiger Aero Lite style:",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Os1Style.entries.forEach { style ->
                        val selected = config.os1.style == style
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.14f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUpdateConfig(config.copy(os1 = config.os1.copy(style = style))) },
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(style.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(style.subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val rm = context.getSystemService(RoleManager::class.java)
                                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                                    homeRoleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                                    return@OutlinedButton
                                }
                            }
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Set hyalineOS as Default Home Launcher", color = Color.White)
                    }
                }

                2 -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xDD071828),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "OS 2 Privilege Gate: ${privilegeStatus.name}",
                                color = Color(0xFF7DD3FC),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                            Text(
                                if (privilegeStatus == PrivilegeStatus.NONE) {
                                    "Strict Privilege Gate: OS 2 stays completely locked and hidden until Shizuku or Root (`su`) is granted. Zero non-privileged fallback."
                                } else {
                                    "Privileged backend (${privilegeStatus.name}) active! OS 2 Deep OS setup is unlocked."
                                },
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 13.sp,
                            )
                            Button(onClick = onReprobePrivilege, modifier = Modifier.fillMaxWidth()) {
                                Text("Request / Re-check Shizuku or Root")
                            }
                            OutlinedButton(onClick = onImportJson, modifier = Modifier.fillMaxWidth()) {
                                Text("Import hyalineos-config.json from Web Studio", color = Color.White)
                            }
                        }
                    }
                }

                3 -> {
                    Text(
                        "Choose OS 2 Deep OS Style & Engine Mode:",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Os2Style.entries.forEach { style ->
                        val selected = config.os2.style == style
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) Color(0xCC0D2E4D) else Color(0x99071828),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUpdateConfig(config.copy(os2 = config.os2.copy(style = style))) },
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(style.displayName, color = Color(0xFFE0F2FE), fontWeight = FontWeight.Bold)
                                Text(style.subtitle, color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }
                    }

                    Text("OS 2 Primary Engine Mode:", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Os2EngineMode.entries.forEach { mode ->
                            FilterChip(
                                selected = config.os2.engineMode == mode,
                                onClick = { onUpdateConfig(config.copy(os2 = config.os2.copy(engineMode = mode))) },
                                label = { Text(mode.displayName, color = Color.White) },
                            )
                        }
                    }
                }

                else -> {
                    Text("Configurable Door & Smart Lock PINs:", color = Color.White, fontWeight = FontWeight.SemiBold)
                    DoorTrigger.entries.forEach { trigger ->
                        FilterChip(
                            selected = config.os2.doorTrigger == trigger,
                            onClick = { onUpdateConfig(config.copy(os2 = config.os2.copy(doorTrigger = trigger))) },
                            label = { Text(trigger.displayName, color = Color.White) },
                        )
                    }
                    OutlinedTextField(
                        value = draftOs2Pin,
                        onValueChange = { draftOs2Pin = it.filter(Char::isDigit).take(8) },
                        label = { Text("OS 2 Unlock PIN (4–8 digits)", color = Color.White) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                    )
                    OutlinedTextField(
                        value = draftDecoyPin,
                        onValueChange = { draftDecoyPin = it.filter(Char::isDigit).take(8) },
                        label = { Text("Decoy Guest PIN", color = Color.White) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (step > 1) {
                OutlinedButton(onClick = { step -= 1 }, modifier = Modifier.weight(1f)) {
                    Text("Back", color = Color.White)
                }
            }
            Button(
                onClick = {
                    if (step < 4) {
                        step += 1
                    } else {
                        onUpdatePins(
                            draftOs2Pin.ifBlank { "1337" },
                            draftDecoyPin.ifBlank { "0000" },
                        )
                        onFinish()
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (step < 4) "Next →" else "Finish & Start hyalineOS")
            }
        }
    }
}

@Composable
fun Os2SmartLockDialog(
    activity: FragmentActivity,
    privilegeStatus: PrivilegeStatus,
    expectedOs2Pin: String,
    expectedDecoyPin: String,
    decoyEnabled: Boolean,
    onUnlockOs2: () -> Unit,
    onUnlockDecoy: () -> Unit,
    onReprobePrivilege: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pinBuffer by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun triggerBiometric() {
        val bm = BiometricManager.from(activity)
        if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            errorMsg = "Biometrics not enrolled on this device — use PIN."
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlockOs2()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock hyalineOS Deep Space (OS 2)")
            .setSubtitle("Authenticate to enter privileged OS 2")
            .setNegativeButtonText("Use PIN")
            .build()
        prompt.authenticate(info)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF2081829),
        title = {
            Text(
                if (privilegeStatus == PrivilegeStatus.NONE) "OS 2 Strictly Locked" else "OS 2 Smart Lock",
                color = Color(0xFFBAE6FD),
            )
        },
        text = {
            if (privilegeStatus == PrivilegeStatus.NONE) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "No Root (`su`) or Shizuku permission is active. Per strict privilege gate policy, OS 2 stays completely locked with zero non-privileged fallback.",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                    Button(onClick = onReprobePrivilege, modifier = Modifier.fillMaxWidth()) {
                        Text("Grant Shizuku / Check Root")
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter OS 2 PIN (${privilegeStatus.name} ready)",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                    Text(
                        "● ".repeat(pinBuffer.length).ifBlank { "—" },
                        color = Color(0xFF38BDF8),
                        fontSize = 24.sp,
                    )
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = Color(0xFFFCA5A5), fontSize = 12.sp)
                    }

                    val pad = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("CLR", "0", "BIO"),
                    )
                    pad.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { key ->
                                OutlinedButton(
                                    onClick = {
                                        errorMsg = null
                                        when (key) {
                                            "CLR" -> pinBuffer = ""
                                            "BIO" -> triggerBiometric()
                                            else -> {
                                                pinBuffer += key
                                                if (pinBuffer == expectedOs2Pin) {
                                                    onUnlockOs2()
                                                } else if (decoyEnabled && pinBuffer == expectedDecoyPin) {
                                                    onUnlockDecoy()
                                                } else if (pinBuffer.length >= maxOf(expectedOs2Pin.length, 4)) {
                                                    errorMsg = "Incorrect PIN"
                                                    pinBuffer = ""
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(key, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) }
        },
    )
}

@Composable
fun Os2DeepWorkspaceScreen(
    config: HyalineConfig,
    privilegeStatus: PrivilegeStatus,
    allApps: List<InstalledAppInfo>,
    engine: Os2EngineController,
    vaultNotes: String,
    onUpdateConfig: (HyalineConfig) -> Unit,
    onUpdateVaultNotes: (String) -> Unit,
    onLaunchNormal: (String) -> Unit,
    onLockToOs1: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Engine, 1 = Terminal, 2 = Vault Notes, 3 = Add Apps
    var shellCmd by remember { mutableStateOf("") }
    var shellHistory by remember {
        mutableStateOf(
            listOf(
                "hyalineOS Deep Space (${privilegeStatus.name})",
                "Ready for privileged pm / settings / shell execution.",
            ),
        )
    }
    var frozenSet by remember { mutableStateOf(setOf<String>()) }

    val realApps = remember(allApps) { allApps.filterNot { it.isInternalTool } }
    val isolatedApps = remember(realApps, config.os2.isolatedPackages) {
        val targetSet = config.os2.isolatedPackages.toSet()
        if (targetSet.isEmpty()) {
            realApps.filter { it.packageName == HyalinePackages.PULSEMESH }.ifEmpty { realApps.take(5) }
        } else {
            realApps.filter { targetSet.contains(it.packageName) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(os2WallpaperBrush(config.os2.style))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // OS 2 Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "OS 2 • ${config.os2.style.displayName}",
                    color = Color(0xFFBAE6FD),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    "${privilegeStatus.name} • ${config.os2.engineMode.displayName}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                )
            }
            Button(onClick = onLockToOs1) {
                Text("Lock → OS 1")
            }
        }

        // Chameleon Persona Strip
        if (config.os2.style == Os2Style.CHAMELEON_PERSONA_SPACES) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Private Vault", "Work", "Travel", "Guest").forEach { persona ->
                    FilterChip(
                        selected = config.os2.activePersona == persona,
                        onClick = { onUpdateConfig(config.copy(os2 = config.os2.copy(activePersona = persona))) },
                        label = { Text(persona, color = Color.White, fontSize = 11.sp) },
                    )
                }
            }
        }

        // Workspace Content Area
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                0 -> {
                    if (config.os2.engineMode == Os2EngineMode.APP_FREEZING) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("App Freezer & Isolation", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { activeTab = 3 }) {
                                        Text("+ Pick Apps", color = Color(0xFF7DD3FC), fontSize = 12.sp)
                                    }
                                    Button(onClick = {
                                        scope.launch {
                                            isolatedApps.forEach { app ->
                                                engine.setPackageFrozen(app.packageName, true)
                                                frozenSet = frozenSet + app.packageName
                                            }
                                            onLockToOs1()
                                        }
                                    }) {
                                        Text("Freeze All & Lock", fontSize = 12.sp)
                                    }
                                }
                            }

                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(isolatedApps, key = { it.packageName }) { app ->
                                    val isFrozen = frozenSet.contains(app.packageName)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xAA0C1E32),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            if (app.icon != null) {
                                                Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                Text(app.packageName, color = Color(0xFF94A3B8), fontSize = 10.sp)
                                            }
                                            OutlinedButton(onClick = {
                                                scope.launch {
                                                    if (isFrozen) {
                                                        engine.setPackageFrozen(app.packageName, false)
                                                        frozenSet = frozenSet - app.packageName
                                                    }
                                                    onLaunchNormal(app.packageName)
                                                }
                                            }) {
                                                Text("Open", color = Color.White, fontSize = 11.sp)
                                            }
                                            Button(onClick = {
                                                scope.launch {
                                                    val nextFreeze = !isFrozen
                                                    val res = engine.setPackageFrozen(app.packageName, nextFreeze)
                                                    if (res.success) {
                                                        frozenSet = if (nextFreeze) frozenSet + app.packageName else frozenSet - app.packageName
                                                    }
                                                    shellHistory = shellHistory + "$ pm ${if (nextFreeze) "disable" else "enable"} ${app.packageName} -> ${res.output}"
                                                }
                                            }) {
                                                Text(if (isFrozen) "Unfreeze" else "Freeze", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Freeform Floating Windows Desktop Mode
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            Text(
                                "Tap any app to launch in an Android Freeform Floating Window:",
                                color = Color(0xFFBAE6FD),
                                fontSize = 13.sp,
                            )
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(realApps, key = { it.packageName }) { app ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xAA0C1E32),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    engine.enableFreeformWindowing()
                                                    engine.launchInFreeformWindow(context, app.packageName)
                                                }
                                            },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            if (app.icon != null) {
                                                Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                                            }
                                            Text(app.label, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                            Text("🪟 Float", color = Color(0xFF7DD3FC), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // Interactive Privileged Shell Terminal
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("id", "uname -a", "pm list packages -d", "wm size").forEach { quick ->
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        val res = engine.runShellCommand(quick)
                                        shellHistory = shellHistory + "$ $quick\n${res.output}"
                                    }
                                }) {
                                    Text(quick.substringBefore(" "), color = Color(0xFF7DD3FC), fontSize = 11.sp)
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xEE030810),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        ) {
                            Text(
                                text = shellHistory.takeLast(30).joinToString("\n\n"),
                                color = Color(0xFF7DD3FC),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = shellCmd,
                                onValueChange = { shellCmd = it },
                                placeholder = { Text("${privilegeStatus.name.lowercase()} # command...", color = Color(0xFF64748B)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                ),
                            )
                            Button(onClick = {
                                val cmd = shellCmd.trim()
                                if (cmd.isNotEmpty()) {
                                    shellCmd = ""
                                    scope.launch {
                                        val res = engine.runShellCommand(cmd)
                                        shellHistory = shellHistory + "$ $cmd\n${res.output}"
                                    }
                                }
                            }) { Text("Run") }
                        }
                    }
                }

                2 -> {
                    // Secure OS 2 Vault Notes
                    var draft by remember(vaultNotes) { mutableStateOf(vaultNotes) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        Text("OS 2 Private Vault Pad (Hidden from OS 1)", color = Color(0xFFBAE6FD), fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = draft,
                            onValueChange = {
                                draft = it
                                onUpdateVaultNotes(it)
                            },
                            placeholder = { Text("Encrypted / PIN-gated OS 2 notes...", color = Color(0xFF64748B)) },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                        )
                    }
                }

                else -> {
                    // App Picker for OS 2 Isolation List
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Select Apps Managed in OS 2", color = Color.White, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { activeTab = 0 }) { Text("Done", color = Color(0xFF7DD3FC)) }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(realApps, key = { it.packageName }) { app ->
                                val checked = config.os2.isolatedPackages.contains(app.packageName)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xAA0C1E32),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val next = if (checked) {
                                                config.os2.isolatedPackages - app.packageName
                                            } else {
                                                (config.os2.isolatedPackages + app.packageName).distinct()
                                            }
                                            onUpdateConfig(config.copy(os2 = config.os2.copy(isolatedPackages = next)))
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Checkbox(checked = checked, onCheckedChange = null)
                                        if (app.icon != null) {
                                            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                                        }
                                        Text(app.label, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Smoked Aero Bottom Taskbar
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xDD081626),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x447DD3FC), RoundedCornerShape(18.dp)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0EA5E9))
                        .clickable {
                            val nextMode = if (config.os2.engineMode == Os2EngineMode.APP_FREEZING) {
                                Os2EngineMode.FREEFORM_WINDOWS
                            } else {
                                Os2EngineMode.APP_FREEZING
                            }
                            onUpdateConfig(config.copy(os2 = config.os2.copy(engineMode = nextMode)))
                            activeTab = 0
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("❖", color = Color.White, fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = { activeTab = 0 }) {
                    Text(
                        if (config.os2.engineMode == Os2EngineMode.APP_FREEZING) "❄️ Freezer" else "🪟 Desktop",
                        color = if (activeTab == 0) Color(0xFF7DD3FC) else Color.White,
                    )
                }
                TextButton(onClick = { activeTab = 1 }) {
                    Text(">_ Shell", color = if (activeTab == 1) Color(0xFF7DD3FC) else Color.White)
                }
                TextButton(onClick = { activeTab = 2 }) {
                    Text("🔐 Vault", color = if (activeTab == 2) Color(0xFF7DD3FC) else Color.White)
                }
            }
        }
    }
}
