package os.hyaline.messages

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.hyaline.core.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val OTP_REGEX = Regex("\\b(\\d{4,8})\\b")

internal object SmsEvents {
    private val _refreshTick = MutableStateFlow(0L)
    val refreshTick = _refreshTick.asStateFlow()

    fun notifyChanged() {
        _refreshTick.value = System.currentTimeMillis()
    }
}

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.first().displayOriginatingAddress ?: "Unknown"
        val fullBody = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val ts = messages.first().timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()

        // Insert into Android Telephony.Sms.Inbox when acting as default SMS app
        if (checkIsDefaultSms(context)) {
            runCatching {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, sender)
                    put(Telephony.Sms.BODY, fullBody)
                    put(Telephony.Sms.DATE, ts)
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.SEEN, 0)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                }
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            }
        }

        val senderName = lookupSmsContactName(context, sender) ?: sender
        postIncomingSmsNotification(context, senderName, sender, fullBody)

        // Broadcast Live Tile update to hyalineOS Launcher
        context.sendBroadcast(
            Intent(HyalinePackages.ACTION_LIVE_TILE_UPDATE).apply {
                putExtra(HyalinePackages.EXTRA_TILE_PACKAGE, context.packageName)
                putExtra(HyalinePackages.EXTRA_TILE_HEADLINE, senderName)
                putExtra(HyalinePackages.EXTRA_TILE_SUBTEXT, fullBody.take(80))
                putExtra(HyalinePackages.EXTRA_TILE_BADGE, 1)
            },
        )
        SmsEvents.notifyChanged()
    }

    private fun postIncomingSmsNotification(
        context: Context,
        senderLabel: String,
        address: String,
        body: String,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channelId = "hyaline_sms"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Incoming SMS", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val openIntent = Intent(context, MessagesActivity::class.java).apply {
            data = Uri.fromParts("smsto", address, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            context,
            address.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setContentTitle(senderLabel)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(address.hashCode(), notif)
    }
}

class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {}
}

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT || intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val recipient = intent.data?.schemeSpecificPart?.trim()
        val messageBody = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (!recipient.isNullOrEmpty() && !messageBody.isNullOrEmpty()) {
            sendSmsInternal(this, recipient, messageBody)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

private data class SmsMessageItem(
    val id: Long,
    val address: String,
    val body: String,
    val dateMillis: Long,
    val isSentByMe: Boolean,
    val isRead: Boolean,
    val otpCode: String?,
)

private data class SmsConversation(
    val address: String,
    val contactName: String?,
    val messages: List<SmsMessageItem>,
    val unreadCount: Int,
) {
    val latest: SmsMessageItem get() = messages.last()
}

class MessagesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialRecipient = intent?.data?.schemeSpecificPart.orEmpty()
        val initialDraft = intent?.getStringExtra("sms_body")
            ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: ""

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val config = remember { HyalineConfig() }
            val refreshTick by SmsEvents.refreshTick.collectAsState()

            var isDefaultSms by remember { mutableStateOf(checkIsDefaultSms(context)) }
            var conversations by remember { mutableStateOf<List<SmsConversation>>(emptyList()) }
            var openAddress by remember { mutableStateOf(initialRecipient.ifBlank { null }) }
            var draftText by remember { mutableStateOf(initialDraft) }
            var showNewDialog by remember { mutableStateOf(false) }
            var newRecipientInput by remember { mutableStateOf("") }

            fun reloadSms() {
                isDefaultSms = checkIsDefaultSms(context)
                scope.launch {
                    conversations = loadSmsConversations(context)
                    val totalUnread = conversations.sumOf { it.unreadCount }
                    if (totalUnread > 0) {
                        val newestUnread = conversations.firstOrNull { it.unreadCount > 0 }?.latest
                        context.sendBroadcast(
                            Intent(HyalinePackages.ACTION_LIVE_TILE_UPDATE).apply {
                                putExtra(HyalinePackages.EXTRA_TILE_PACKAGE, context.packageName)
                                putExtra(HyalinePackages.EXTRA_TILE_HEADLINE, "$totalUnread unread SMS")
                                putExtra(HyalinePackages.EXTRA_TILE_SUBTEXT, newestUnread?.body?.take(70).orEmpty())
                                putExtra(HyalinePackages.EXTRA_TILE_BADGE, totalUnread)
                            },
                        )
                    }
                }
            }

            LaunchedEffect(refreshTick) {
                reloadSms()
            }

            val permLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { reloadSms() }

            val roleLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { reloadSms() }

            LaunchedEffect(Unit) {
                val needed = buildList {
                    add(Manifest.permission.READ_SMS)
                    add(Manifest.permission.SEND_SMS)
                    add(Manifest.permission.RECEIVE_SMS)
                    add(Manifest.permission.READ_CONTACTS)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

                if (needed.isNotEmpty()) {
                    permLauncher.launch(needed.toTypedArray())
                } else {
                    reloadSms()
                }
            }

            fun copyToClipboard(label: String, text: String) {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText(label, text))
            }

            HyalineTheme(accentHex = "#0096c7") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(os1WallpaperBrush(config.os1.wallpaperPreset))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (openAddress == null) {
                        // CONVERSATION LIST SCREEN
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("hyaline Messages", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
                                Text(
                                    if (isDefaultSms) "Default SMS App ✓" else "Set Default to receive & persist SMS",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!isDefaultSms) {
                                    OutlinedButton(onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            val rm = context.getSystemService(RoleManager::class.java)
                                            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
                                                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
                                                return@OutlinedButton
                                            }
                                        }
                                        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                                            .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                                        roleLauncher.launch(intent)
                                    }) {
                                        Text("Set Default", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                                Button(onClick = { showNewDialog = true }) {
                                    Text("+ New")
                                }
                            }
                        }

                        if (conversations.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No SMS conversations yet — tap + New to send a message.", color = Color.White)
                            }
                        } else {
                            val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                                items(conversations, key = { it.address }) { conv ->
                                    val latestOtp = conv.latest.otpCode
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.White.copy(alpha = if (conv.unreadCount > 0) 0.28f else 0.16f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                openAddress = conv.address
                                                scope.launch {
                                                    markAddressRead(context, conv.address)
                                                    reloadSms()
                                                }
                                            },
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    conv.contactName ?: conv.address,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                )
                                                Text(
                                                    fmt.format(Date(conv.latest.dateMillis)),
                                                    color = Color.White.copy(alpha = 0.72f),
                                                    fontSize = 11.sp,
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    conv.latest.body,
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    fontSize = 13.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                if (conv.unreadCount > 0) {
                                                    Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFF0284C7)) {
                                                        Text(
                                                            "${conv.unreadCount}",
                                                            color = Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        )
                                                    }
                                                }
                                            }
                                            if (latestOtp != null) {
                                                AssistChip(
                                                    onClick = { copyToClipboard("OTP", latestOtp) },
                                                    label = { Text("📋 Copy OTP $latestOtp", color = Color.White, fontSize = 12.sp) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // ACTIVE THREAD VIEW
                        val addr = openAddress!!
                        val thread = conversations.firstOrNull { normalizePhone(it.address) == normalizePhone(addr) }
                        val title = thread?.contactName ?: addr

                        BackHandler { openAddress = null }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                if (thread?.contactName != null) {
                                    Text(addr, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                                }
                            }
                            OutlinedButton(onClick = { openAddress = null }) {
                                Text("← Threads", color = Color.White)
                            }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            items(thread?.messages.orEmpty(), key = { it.id }) { msg ->
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (msg.isSentByMe) Alignment.End else Alignment.Start,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (msg.isSentByMe) Color(0xCC0284C7) else Color.White.copy(alpha = 0.22f),
                                        modifier = Modifier
                                            .widthIn(max = 300.dp)
                                            .clickable { copyToClipboard("SMS", msg.body) },
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(msg.body, color = Color.White, fontSize = 14.sp)
                                            if (msg.otpCode != null && !msg.isSentByMe) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    "Tap to copy • OTP: ${msg.otpCode}",
                                                    color = Color(0xFFBAE6FD),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.clickable { copyToClipboard("OTP", msg.otpCode) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = draftText,
                                onValueChange = { draftText = it },
                                placeholder = { Text("Write SMS...", color = Color.White.copy(alpha = 0.7f)) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                ),
                            )
                            Button(onClick = {
                                val body = draftText.trim()
                                if (body.isNotEmpty()) {
                                    val sentOk = sendSmsInternal(context, addr, body)
                                    if (sentOk) {
                                        draftText = ""
                                        reloadSms()
                                    }
                                }
                            }) {
                                Text("Send")
                            }
                        }
                    }
                }

                if (showNewDialog) {
                    AlertDialog(
                        onDismissRequest = { showNewDialog = false },
                        containerColor = Color(0xF2082036),
                        title = { Text("New SMS Conversation", color = Color.White) },
                        text = {
                            OutlinedTextField(
                                value = newRecipientInput,
                                onValueChange = { newRecipientInput = it },
                                label = { Text("Phone number", color = Color.White) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                ),
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                val target = newRecipientInput.trim()
                                if (target.isNotEmpty()) {
                                    showNewDialog = false
                                    newRecipientInput = ""
                                    openAddress = target
                                }
                            }) { Text("Start") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNewDialog = false }) { Text("Cancel", color = Color.White) }
                        },
                    )
                }
            }
        }
    }
}

internal fun checkIsDefaultSms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = context.getSystemService(RoleManager::class.java)
        if (rm != null) return rm.isRoleHeld(RoleManager.ROLE_SMS)
    }
    return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
}

internal fun sendSmsInternal(context: Context, destination: String, body: String): Boolean {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
        return false
    }
    @Suppress("DEPRECATION")
    val smsManager = context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault() ?: return false
    return runCatching {
        val parts = smsManager.divideMessage(body)
        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(destination, null, parts, null, null)
        } else {
            smsManager.sendTextMessage(destination, null, body, null, null)
        }
        // Persist in Sent box when acting as default SMS app (non-default apps have Sent written by OS automatically)
        if (checkIsDefaultSms(context)) {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, destination)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        }
        SmsEvents.notifyChanged()
        true
    }.getOrDefault(false)
}

private fun normalizePhone(raw: String): String =
    raw.filter { it.isDigit() || it == '+' }.takeLast(10)

private suspend fun markAddressRead(context: Context, address: String) = withContext(Dispatchers.IO) {
    if (!checkIsDefaultSms(context)) return@withContext
    runCatching {
        val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
        context.contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0",
            arrayOf(address),
        )
    }
}

private suspend fun loadSmsConversations(context: Context): List<SmsConversation> = withContext(Dispatchers.IO) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
        return@withContext emptyList()
    }
    val grouped = linkedMapOf<String, MutableList<SmsMessageItem>>()
    val canonicalAddr = mutableMapOf<String, String>()

    runCatching {
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            var count = 0
            while (c.moveToNext() && count < 400) {
                count++
                val id = c.getLong(0)
                val addr = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val body = c.getString(2).orEmpty()
                val date = c.getLong(3)
                val type = c.getInt(4)
                val isRead = c.getInt(5) != 0
                val key = normalizePhone(addr).ifBlank { addr }
                canonicalAddr.putIfAbsent(key, addr)
                val otp = OTP_REGEX.find(body)?.groupValues?.getOrNull(1)
                grouped.getOrPut(key) { mutableListOf() }.add(
                    SmsMessageItem(
                        id = id,
                        address = addr,
                        body = body,
                        dateMillis = date,
                        isSentByMe = type != Telephony.Sms.MESSAGE_TYPE_INBOX,
                        isRead = isRead,
                        otpCode = otp,
                    ),
                )
            }
        }
    }

    grouped.map { (key, listDesc) ->
        val rawAddr = canonicalAddr[key] ?: key
        val chronological = listDesc.sortedBy { it.dateMillis }
        SmsConversation(
            address = rawAddr,
            contactName = lookupSmsContactName(context, rawAddr),
            messages = chronological,
            unreadCount = chronological.count { !it.isRead && !it.isSentByMe },
        )
    }.sortedByDescending { it.latest.dateMillis }
}

internal fun lookupSmsContactName(context: Context, phoneNumber: String): String? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return null
    }
    return runCatching {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber),
        )
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}
