package os.hyaline.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import os.hyaline.core.privilege.PrivilegeStatus
import os.hyaline.core.theme.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Os1LauncherPager(
    pagerState: PagerState,
    config: HyalineConfig,
    allApps: List<InstalledAppInfo>,
    liveTiles: Map<String, LiveTileUpdate>,
    customLiveTiles: Map<String, LiveTileUpdate>,
    notes: List<NoteEntry>,
    repo: LauncherRepository,
    privilegeStatus: PrivilegeStatus,
    secretPinCode: String,
    onUpdateConfig: (HyalineConfig) -> Unit,
    onLaunchTarget: (String) -> Unit,
    onTriggerOs2Door: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var editingTileId by remember { mutableStateOf<String?>(null) }
    var contextMenuApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var showLetterJumpOverlay by remember { mutableStateOf(false) }
    var drawerSearch by remember { mutableStateOf("") }

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var pulse by remember { mutableStateOf(repo.readSystemPulse()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000L)
            now = LocalDateTime.now()
            pulse = repo.readSystemPulse()
        }
    }

    // Back handler inside OS 1: close overlays/editors/drawer first; never exit home launcher on page 0!
    BackHandler(enabled = true) {
        when {
            showLetterJumpOverlay -> showLetterJumpOverlay = false
            editingTileId != null -> editingTileId = null
            contextMenuApp != null -> contextMenuApp = null
            drawerSearch.isNotEmpty() -> drawerSearch = ""
            pagerState.currentPage > 0 -> scope.launch { pagerState.animateScrollToPage(0) }
            else -> Unit // Root home screen consumes Back press
        }
    }

    val appsByPkg = remember(allApps) { allApps.associateBy { it.packageName } }
    val pinnedNote = remember(notes) { notes.firstOrNull { it.pinnedToTile } ?: notes.firstOrNull() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(os1WallpaperBrush(config.os1.wallpaperPreset))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            if (page == 0) {
                Os1StartPage(
                    config = config,
                    now = now,
                    pulse = pulse,
                    appsByPkg = appsByPkg,
                    liveTiles = liveTiles,
                    customLiveTiles = customLiveTiles,
                    pinnedNote = pinnedNote,
                    editingTileId = editingTileId,
                    onSelectEditingTile = { editingTileId = it },
                    onUpdateConfig = onUpdateConfig,
                    onLaunchTarget = onLaunchTarget,
                    onOpenDrawer = { scope.launch { pagerState.animateScrollToPage(1) } },
                    onTriggerOs2Door = onTriggerOs2Door,
                    onOpenSettings = onOpenSettings,
                    onOpenAppInfo = { repo.openAppInfo(it) },
                    onExpandShade = { repo.expandNotificationShade() },
                )
            } else {
                Wp8AppDrawerPage(
                    allApps = allApps,
                    pinnedPackages = config.os1.tiles.map { it.packageName }.toSet(),
                    searchQuery = drawerSearch,
                    showLetterJumpOverlay = showLetterJumpOverlay,
                    onSearchChange = { q ->
                        drawerSearch = q
                        if (config.os2.doorTrigger == DoorTrigger.SECRET_DRAWER_CODE &&
                            (q == "#$secretPinCode" || q == "#1337")
                        ) {
                            drawerSearch = ""
                            onTriggerOs2Door()
                        }
                    },
                    onToggleLetterOverlay = { showLetterJumpOverlay = it },
                    onBackToStart = { scope.launch { pagerState.animateScrollToPage(0) } },
                    onLaunchApp = { pkg ->
                        drawerSearch = ""
                        onLaunchTarget(pkg)
                    },
                    onLongPressApp = { app -> contextMenuApp = app },
                )
            }
        }

        // App Drawer Long-Press Context Sheet
        if (contextMenuApp != null) {
            val app = contextMenuApp!!
            val isPinned = config.os1.tiles.any { it.packageName == app.packageName }
            val isIsolatedOs2 = config.os2.isolatedPackages.contains(app.packageName)

            ModalBottomSheet(
                onDismissRequest = { contextMenuApp = null },
                containerColor = Color(0xEE0B2238),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (app.icon != null) {
                            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(42.dp))
                        } else {
                            Text(app.fallbackGlyph, fontSize = 28.sp)
                        }
                        Column {
                            Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(app.packageName, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                        }
                    }

                    if (!isPinned) {
                        Text("Pin to Start:", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TileSize.entries.forEach { sz ->
                                Button(
                                    onClick = {
                                        val newTile = TileConfig(
                                            id = "tile-${app.packageName}-${System.currentTimeMillis() % 10000}",
                                            label = app.label,
                                            packageName = app.packageName,
                                            icon = app.fallbackGlyph,
                                            size = sz,
                                        )
                                        onUpdateConfig(config.copy(os1 = config.os1.copy(tiles = config.os1.tiles + newTile)))
                                        contextMenuApp = null
                                        scope.launch { pagerState.animateScrollToPage(0) }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Pin ${sz.wireId}")
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                onUpdateConfig(
                                    config.copy(
                                        os1 = config.os1.copy(
                                            tiles = config.os1.tiles.filterNot { it.packageName == app.packageName },
                                        ),
                                    ),
                                )
                                contextMenuApp = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Unpin from Start", color = Color.White)
                        }
                    }

                    if (privilegeStatus != PrivilegeStatus.NONE && !app.isInternalTool) {
                        OutlinedButton(
                            onClick = {
                                val nextList = if (isIsolatedOs2) {
                                    config.os2.isolatedPackages - app.packageName
                                } else {
                                    (config.os2.isolatedPackages + app.packageName).distinct()
                                }
                                onUpdateConfig(config.copy(os2 = config.os2.copy(isolatedPackages = nextList)))
                                contextMenuApp = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (isIsolatedOs2) "Remove from OS 2 Vault List" else "Assign to OS 2 Vault / Freezer",
                                color = Color(0xFF7DD3FC),
                            )
                        }
                    }

                    if (!app.isInternalTool) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    repo.openAppInfo(app.packageName)
                                    contextMenuApp = null
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("App Info", color = Color.White)
                            }
                            if (!app.isSystemApp) {
                                OutlinedButton(
                                    onClick = {
                                        repo.requestUninstall(app.packageName)
                                        contextMenuApp = null
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Uninstall", color = Color(0xFFFCA5A5))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Os1StartPage(
    config: HyalineConfig,
    now: LocalDateTime,
    pulse: SystemPulseSnapshot,
    appsByPkg: Map<String, InstalledAppInfo>,
    liveTiles: Map<String, LiveTileUpdate>,
    customLiveTiles: Map<String, LiveTileUpdate>,
    pinnedNote: NoteEntry?,
    editingTileId: String?,
    onSelectEditingTile: (String?) -> Unit,
    onUpdateConfig: (HyalineConfig) -> Unit,
    onLaunchTarget: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onTriggerOs2Door: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onExpandShade: () -> Unit,
) {
    val timeStr = remember(now) { now.format(DateTimeFormatter.ofPattern("HH:mm")) }
    val dateStr = remember(now) { now.format(DateTimeFormatter.ofPattern("EEEE, MMM d")) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumY = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dragAccumY += dragAmount
                        if (dragAccumY > 110f) {
                            dragAccumY = -999f
                            onExpandShade()
                        }
                    },
                )
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        // Top Clock + Date + Quick Drawer Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = { onLaunchTarget(HyalinePackages.INTERNAL_CLOCK) },
                        onLongClick = { onTriggerOs2Door() },
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(timeStr, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Light, lineHeight = 38.sp)
                Text(dateStr, color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editingTileId != null) {
                    Button(onClick = { onSelectEditingTile(null) }) {
                        Text("Done")
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.clickable(onClick = onOpenDrawer),
                    ) {
                        Text(
                            "All Apps →",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }

        // Vista / 7 Frutiger Aero Gadgets Strip (when Metro + Aero Gadgets style is active)
        if (config.os1.style == Os1Style.METRO_AERO_GADGETS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.24f),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onLaunchTarget(HyalinePackages.INTERNAL_PULSE) },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("BATTERY ORB", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${pulse.batteryPct}% ${if (pulse.isCharging) "⚡" else ""}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.24f),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onLaunchTarget(HyalinePackages.INTERNAL_PULSE) },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("MEMORY PULSE", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${"%.1f".format(pulse.availRamGb)} GB Free",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // 4-Column Metro x Frutiger Aero Live Tile Grid
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
                val appInfo = appsByPkg[tile.packageName]
                val tint = parseHexColor(tile.accentOverride ?: config.os1.accentHex)
                val fg = if (config.os1.style == Os1Style.MINIMAL_FROSTED_AERO) Color.White else contrastingForeground(tint)
                val live = customLiveTiles[tile.packageName] ?: liveTiles[tile.packageName]
                val isSelected = editingTileId == tile.id
                val heightDp = if (tile.size == TileSize.SMALL) 82.dp else 160.dp

                val dynamicSubtext = when {
                    live != null -> live.text.ifBlank { live.title }
                    tile.packageName == HyalinePackages.INTERNAL_NOTES && pinnedNote != null ->
                        pinnedNote.body.ifBlank { pinnedNote.title }
                    tile.packageName == HyalinePackages.INTERNAL_PULSE ->
                        "Battery ${pulse.batteryPct}% • ${"%.1f".format(pulse.availRamGb)} GB free RAM"
                    tile.packageName == HyalinePackages.INTERNAL_CLOCK ->
                        "$timeStr • Tap for Stopwatch & Focus"
                    tile.packageName == HyalinePackages.PULSEMESH ->
                        if (appInfo != null) "Off-grid E2EE Mesh • Tap to launch" else "PulseMesh not installed yet"
                    else -> null
                }

                Box(
                    modifier = Modifier
                        .height(heightDp)
                        .hyalineGlassTile(
                            style = config.os1.style,
                            tint = tint,
                            glassOpacity = config.os1.glassOpacity,
                            cornerRadiusDp = config.os1.tileRadiusPx,
                        )
                        .then(
                            if (isSelected) {
                                Modifier.border(2.5.dp, Color.White, RoundedCornerShape(config.os1.tileRadiusPx.dp))
                            } else {
                                Modifier
                            },
                        )
                        .combinedClickable(
                            onClick = {
                                if (editingTileId != null) {
                                    onSelectEditingTile(tile.id)
                                } else {
                                    onLaunchTarget(tile.packageName)
                                }
                            },
                            onLongClick = { onSelectEditingTile(tile.id) },
                        )
                        .padding(10.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            if (appInfo?.icon != null) {
                                Image(
                                    bitmap = appInfo.icon,
                                    contentDescription = tile.label,
                                    modifier = Modifier
                                        .size(if (tile.size == TileSize.SMALL) 34.dp else 40.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            } else {
                                Text(
                                    text = tile.icon.ifBlank { appInfo?.fallbackGlyph ?: "📱" },
                                    fontSize = if (tile.size == TileSize.SMALL) 24.sp else 26.sp,
                                )
                            }

                            if (live != null && live.count > 0) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.Black.copy(alpha = 0.32f),
                                ) {
                                    Text(
                                        text = "${live.count}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        if (tile.size != TileSize.SMALL && !dynamicSubtext.isNullOrBlank()) {
                            Text(
                                text = dynamicSubtext,
                                color = fg.copy(alpha = 0.92f),
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                maxLines = if (tile.size == TileSize.WIDE) 3 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Text(
                            text = appInfo?.label ?: tile.label,
                            color = fg,
                            fontSize = if (tile.size == TileSize.SMALL) 11.sp else 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // WP8 Tile Edit Mode Inspector Bar (when user long-presses a tile)
        val editingTile = config.os1.tiles.firstOrNull { it.id == editingTileId }
        if (editingTile != null) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xEE081B2E),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Edit: ${editingTile.label}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        TextButton(onClick = { onSelectEditingTile(null) }) {
                            Text("Done", color = Color(0xFF7DD3FC))
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                val nextTiles = config.os1.tiles.map {
                                    if (it.id == editingTile.id) it.copy(size = it.size.next()) else it
                                }
                                onUpdateConfig(config.copy(os1 = config.os1.copy(tiles = nextTiles)))
                            },
                        ) {
                            Text("Size: ${editingTile.size.wireId}", color = Color.White, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val list = config.os1.tiles.toMutableList()
                                val idx = list.indexOfFirst { it.id == editingTile.id }
                                if (idx > 0) {
                                    val item = list.removeAt(idx)
                                    list.add(idx - 1, item)
                                    onUpdateConfig(config.copy(os1 = config.os1.copy(tiles = list)))
                                }
                            },
                        ) { Text("←", color = Color.White) }
                        OutlinedButton(
                            onClick = {
                                val list = config.os1.tiles.toMutableList()
                                val idx = list.indexOfFirst { it.id == editingTile.id }
                                if (idx >= 0 && idx < list.lastIndex) {
                                    val item = list.removeAt(idx)
                                    list.add(idx + 1, item)
                                    onUpdateConfig(config.copy(os1 = config.os1.copy(tiles = list)))
                                }
                            },
                        ) { Text("→", color = Color.White) }
                        OutlinedButton(
                            onClick = {
                                val nextTiles = config.os1.tiles.filterNot { it.id == editingTile.id }
                                onUpdateConfig(config.copy(os1 = config.os1.copy(tiles = nextTiles)))
                                onSelectEditingTile(null)
                            },
                        ) { Text("Unpin", color = Color(0xFFFCA5A5), fontSize = 12.sp) }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        HyalineConfig.accentPalette.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(hex))
                                    .clickable {
                                        val nextTiles = config.os1.tiles.map {
                                            if (it.id == editingTile.id) it.copy(accentOverride = hex) else it
                                        }
                                        onUpdateConfig(config.copy(os1 = config.os1.copy(tiles = nextTiles)))
                                    },
                            )
                        }
                        if (!editingTile.packageName.startsWith("hyaline://")) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onOpenAppInfo(editingTile.packageName) }) {
                                Text("App Info", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else if (config.os1.style == Os1Style.METRO_AERO_GADGETS ||
            config.os2.doorTrigger == DoorTrigger.VISIBLE_DOCK_PILL
        ) {
            // Frosted Aero Bottom Dock
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        val phoneTile = config.os1.tiles.firstOrNull { it.id == "phone" }
                        onLaunchTarget(phoneTile?.packageName ?: HyalinePackages.PHONE)
                    }) { Text("📞 Phone", color = Color.White) }
                    TextButton(onClick = {
                        val smsTile = config.os1.tiles.firstOrNull { it.id == "messages" }
                        onLaunchTarget(smsTile?.packageName ?: HyalinePackages.MESSAGES)
                    }) { Text("💬 SMS", color = Color.White) }
                    TextButton(onClick = onOpenDrawer) { Text("🔎 Apps", color = Color.White) }
                    if (config.os2.doorTrigger == DoorTrigger.VISIBLE_DOCK_PILL) {
                        TextButton(onClick = onTriggerOs2Door) { Text("🔐 OS 2", color = Color.White) }
                    } else {
                        TextButton(onClick = onOpenSettings) { Text("⚙️ Studio", color = Color.White) }
                    }
                }
            }
        }
    }
}

private sealed interface DrawerRow {
    data class Header(val letter: String) : DrawerRow
    data class AppItem(val app: InstalledAppInfo) : DrawerRow
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Wp8AppDrawerPage(
    allApps: List<InstalledAppInfo>,
    pinnedPackages: Set<String>,
    searchQuery: String,
    showLetterJumpOverlay: Boolean,
    onSearchChange: (String) -> Unit,
    onToggleLetterOverlay: (Boolean) -> Unit,
    onBackToStart: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onLongPressApp: (InstalledAppInfo) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val filtered = remember(allApps, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
            }
        }
    }

    val rows = remember(filtered, searchQuery) {
        if (searchQuery.isNotBlank()) {
            filtered.map { DrawerRow.AppItem(it) }
        } else {
            val out = mutableListOf<DrawerRow>()
            var lastLetter: String? = null
            for (app in filtered) {
                val sec = app.sectionLetter
                if (sec != lastLetter) {
                    out += DrawerRow.Header(sec)
                    lastLetter = sec
                }
                out += DrawerRow.AppItem(app)
            }
            out
        }
    }

    val availableLetters = remember(rows) {
        rows.mapIndexedNotNull { idx, r -> (r as? DrawerRow.Header)?.letter?.let { it to idx } }.toMap()
    }

    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.clickable(onClick = onBackToStart),
                ) {
                    Text(
                        "← Start",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search ${allApps.size} apps...", color = Color.White.copy(alpha = 0.7f)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                    ),
                )

                if (searchQuery.isNotEmpty()) {
                    TextButton(onClick = { onSearchChange("") }) {
                        Text("✕", color = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(rows) { _, row ->
                    when (row) {
                        is DrawerRow.Header -> Box(
                            modifier = Modifier
                                .padding(top = 8.dp, bottom = 2.dp)
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.24f))
                                .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                .clickable { onToggleLetterOverlay(true) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(row.letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }

                        is DrawerRow.AppItem -> {
                            val app = row.app
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onLaunchApp(app.packageName) },
                                        onLongClick = { onLongPressApp(app) },
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (app.icon != null) {
                                        Image(
                                            bitmap = app.icon,
                                            contentDescription = app.label,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.22f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(app.fallbackGlyph, fontSize = 20.sp)
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.label,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (app.isInternalTool) {
                                            Text("hyalineOS Built-in Tool", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                        }
                                    }

                                    if (pinnedPackages.contains(app.packageName)) {
                                        Text("★", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Classic Windows Phone 8 Fast-Jump Letter Grid Overlay
        if (showLetterJumpOverlay) {
            val alphabet = listOf("#") + ('A'..'Z').map { it.toString() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD061626))
                    .clickable { onToggleLetterOverlay(false) }
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(alphabet) { letter ->
                        val jumpIdx = availableLetters[letter]
                        val enabled = jumpIdx != null
                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (enabled) Color(0xFF0EA5E9).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                                )
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = if (enabled) 0.65f else 0.16f),
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable(enabled = enabled) {
                                    onToggleLetterOverlay(false)
                                    if (jumpIdx != null) {
                                        scope.launch { listState.scrollToItem(jumpIdx) }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = letter,
                                color = if (enabled) Color.White else Color.White.copy(alpha = 0.32f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
