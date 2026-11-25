package os.hyaline.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.hyaline.core.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class CallLogEntry(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val type: Int,
    val dateMillis: Long,
    val durationSec: Long,
)

private data class ContactItem(
    val id: Long,
    val name: String,
    val number: String,
)

class PhoneActivity : ComponentActivity() {

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialNumber = intent?.data?.schemeSpecificPart.orEmpty()

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val config = remember { HyalineConfig() }

            var tab by remember { mutableIntStateOf(0) } // 0 = Keypad, 1 = Recents, 2 = Contacts
            var dialInput by remember { mutableStateOf(initialNumber) }
            var contactSearch by remember { mutableStateOf("") }

            var isDefaultDialer by remember { mutableStateOf(checkIsDefaultDialer(context)) }
            var callLogs by remember { mutableStateOf<List<CallLogEntry>>(emptyList()) }
            var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }

            fun refreshData() {
                isDefaultDialer = checkIsDefaultDialer(context)
                scope.launch {
                    callLogs = loadCallLogs(context)
                    contacts = loadContacts(context)
                    val missedCount = callLogs.count { it.type == CallLog.Calls.MISSED_TYPE }
                    if (missedCount > 0) {
                        val latestMissed = callLogs.first { it.type == CallLog.Calls.MISSED_TYPE }
                        context.sendBroadcast(
                            Intent(HyalinePackages.ACTION_LIVE_TILE_UPDATE).apply {
                                putExtra(HyalinePackages.EXTRA_TILE_PACKAGE, context.packageName)
                                putExtra(HyalinePackages.EXTRA_TILE_HEADLINE, "$missedCount missed calls")
                                putExtra(HyalinePackages.EXTRA_TILE_SUBTEXT, latestMissed.cachedName ?: latestMissed.number)
                                putExtra(HyalinePackages.EXTRA_TILE_BADGE, missedCount)
                            },
                        )
                    }
                }
            }

            val permLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { refreshData() }

            val roleLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { refreshData() }

            LaunchedEffect(Unit) {
                val needed = buildList {
                    add(Manifest.permission.CALL_PHONE)
                    add(Manifest.permission.READ_CALL_LOG)
                    add(Manifest.permission.READ_CONTACTS)
                    add(Manifest.permission.READ_PHONE_STATE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) add(Manifest.permission.ANSWER_PHONE_CALLS)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

                if (needed.isNotEmpty()) {
                    permLauncher.launch(needed.toTypedArray())
                } else {
                    refreshData()
                }
            }

            fun placeCall(rawNumber: String) {
                val clean = rawNumber.trim()
                if (clean.isEmpty()) return
                val uri = Uri.fromParts("tel", clean, null)
                val hasCallPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE,
                ) == PackageManager.PERMISSION_GRANTED

                if (hasCallPerm) {
                    val tm = context.getSystemService(TelecomManager::class.java)
                    val placed = runCatching {
                        tm?.placeCall(uri, Bundle())
                        tm != null
                    }.getOrDefault(false)
                    if (placed) return

                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        return
                    }
                }
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }

            HyalineTheme(accentHex = "#10b981") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(os1WallpaperBrush(config.os1.wallpaperPreset))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("hyaline Phone", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
                            Text(
                                if (isDefaultDialer) "Default Phone App ✓" else "Tap Set Default for In-Call Aero UI",
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 12.sp,
                            )
                        }
                        if (!isDefaultDialer) {
                            Button(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    val rm = context.getSystemService(RoleManager::class.java)
                                    if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                                        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                                        return@Button
                                    }
                                }
                                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                                roleLauncher.launch(intent)
                            }) {
                                Text("Set Default")
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Keypad", "Recents (${callLogs.size})", "Contacts (${contacts.size})").forEachIndexed { idx, label ->
                            FilterChip(
                                selected = tab == idx,
                                onClick = { tab = idx },
                                label = { Text(label, color = Color.White) },
                            )
                        }
                    }

                    when (tab) {
                        0 -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.Black.copy(alpha = 0.22f),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        val matchedContact = remember(dialInput, contacts) {
                                            if (dialInput.length < 3) null else contacts.firstOrNull { it.number.replace(" ", "").contains(dialInput) }
                                        }
                                        Text(
                                            text = dialInput.ifEmpty { "Enter number" },
                                            color = Color.White,
                                            fontSize = 30.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                        if (matchedContact != null) {
                                            Text(matchedContact.name, color = Color(0xFFA7F3D0), fontSize = 13.sp)
                                        }
                                    }
                                }

                                val rows = listOf(
                                    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
                                    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
                                    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
                                    listOf("*" to "", "0" to "+", "#" to ""),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rows.forEach { row ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            row.forEach { (digit, sub) ->
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(62.dp)
                                                        .hyalineGlassTile(config.os1.style, Color(0xFF10B981), 0.36f, 12)
                                                        .combinedClickable(
                                                            onClick = { dialInput += digit },
                                                            onLongClick = { if (digit == "0") dialInput += "+" },
                                                        ),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(digit, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                                        if (sub.isNotEmpty()) {
                                                            Text(sub, color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(
                                            onClick = { if (dialInput.isNotEmpty()) dialInput = dialInput.dropLast(1) },
                                            modifier = Modifier.weight(1f).height(54.dp),
                                        ) {
                                            Text("⌫ Delete", color = Color.White)
                                        }
                                        Button(
                                            onClick = { placeCall(dialInput) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                            modifier = Modifier.weight(2f).height(54.dp),
                                        ) {
                                            Text("📞 Call", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
                            if (callLogs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No call history yet (or Call Log permission pending).", color = Color.White)
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                                    items(callLogs, key = { it.id }) { entry ->
                                        val icon = when (entry.type) {
                                            CallLog.Calls.MISSED_TYPE -> "✕ Missed"
                                            CallLog.Calls.OUTGOING_TYPE -> "↗ Outgoing"
                                            else -> "↙ Incoming"
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.White.copy(alpha = 0.16f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { placeCall(entry.number) },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        entry.cachedName?.takeIf { it.isNotBlank() } ?: entry.number,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                    Text(
                                                        "$icon • ${fmt.format(Date(entry.dateMillis))}",
                                                        color = if (entry.type == CallLog.Calls.MISSED_TYPE) Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.78f),
                                                        fontSize = 12.sp,
                                                    )
                                                }
                                                Text("📞", fontSize = 20.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            val filtered = remember(contacts, contactSearch) {
                                if (contactSearch.isBlank()) contacts else contacts.filter {
                                    it.name.contains(contactSearch, true) || it.number.contains(contactSearch)
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                                OutlinedTextField(
                                    value = contactSearch,
                                    onValueChange = { contactSearch = it },
                                    placeholder = { Text("Search contacts...", color = Color.White.copy(alpha = 0.7f)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                    ),
                                )
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                    items(filtered, key = { "${it.id}-${it.number}" }) { c ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.White.copy(alpha = 0.16f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { placeCall(c.number) },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(c.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                                    Text(c.number, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                                                }
                                                Text("📞", fontSize = 18.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun checkIsDefaultDialer(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = context.getSystemService(RoleManager::class.java)
        if (rm != null) return rm.isRoleHeld(RoleManager.ROLE_DIALER)
    }
    val tm = context.getSystemService(TelecomManager::class.java)
    return tm?.defaultDialerPackage == context.packageName
}

private suspend fun loadCallLogs(context: Context): List<CallLogEntry> = withContext(Dispatchers.IO) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
        return@withContext emptyList()
    }
    val out = mutableListOf<CallLogEntry>()
    runCatching {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
            ),
            null,
            null,
            "${CallLog.Calls.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext() && out.size < 100) {
                out += CallLogEntry(
                    id = c.getLong(0),
                    number = c.getString(1).orEmpty(),
                    cachedName = c.getString(2),
                    type = c.getInt(3),
                    dateMillis = c.getLong(4),
                    durationSec = c.getLong(5),
                )
            }
        }
    }
    out
}

private suspend fun loadContacts(context: Context): List<ContactItem> = withContext(Dispatchers.IO) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return@withContext emptyList()
    }
    val out = mutableListOf<ContactItem>()
    runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
        )?.use { c ->
            while (c.moveToNext() && out.size < 300) {
                val name = c.getString(1).orEmpty()
                val num = c.getString(2).orEmpty()
                if (num.isNotBlank()) {
                    out += ContactItem(c.getLong(0), name.ifBlank { num }, num)
                }
            }
        }
    }
    out
}
