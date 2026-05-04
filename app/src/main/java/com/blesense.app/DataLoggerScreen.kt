package com.blesense.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import androidx.compose.ui.text.font.FontFamily.Companion.Monospace

// ─── Theme helpers ────────────────────────────────────────────────────────────

enum class ThemeMode { LIGHT, DARK }

data class DataLoggerTheme(val isLight: Boolean) {
    val backgroundStart: Color
        get() = if (isLight) Color(0xFFF5F5F5) else com.blesense.app.ui.theme.BleSenseColors.BackgroundDark
    val backgroundEnd: Color
        get() = if (isLight) Color(0xFFE8E8E8) else com.blesense.app.ui.theme.BleSenseColors.SurfaceDark

    val primaryText: Color   get() = if (isLight) Color(0xFF1A1A1A) else Color.White
    val secondaryText: Color get() = if (isLight) Color(0xFF666666) else Color.White.copy(alpha = 0.7f)
    val hintText: Color      get() = if (isLight) Color(0xFF888888) else Color.White.copy(alpha = 0.6f)

    val cardBackground: Color get() = if (isLight) Color(0xFFFFFFFF) else Color.White.copy(alpha = 0.15f)
    val cardBorder: Color     get() = if (isLight) Color(0xFFE0E0E0) else Color.White.copy(alpha = 0.3f)

    val codeSurface: Color    get() = if (isLight) Color(0xFFF0F4F0) else Color(0xFF1A2B1A)
    val rawDataSurface: Color get() = if (isLight) Color(0xFFFAFAFA) else Color(0xFF1A1A2E)

    val indexText: Color   get() = if (isLight) Color(0xFF999999) else Color(0xFF666688)
    val accelX: Color      get() = if (isLight) Color(0xFFD32F2F) else Color(0xFFFF6B6B)
    val accelY: Color      get() = if (isLight) Color(0xFF008080) else Color(0xFF4ECDC4)
    val accelZ: Color      get() = if (isLight) Color(0xFF2E7D6E) else Color(0xFF95E1D3)
    val rawDataText: Color get() = if (isLight) Color(0xFF0D5A62) else Color(0xFF4ECDC4)

    val packetIdText: Color   get() = if (isLight) Color(0xFFE65100) else Color(0xFFFFAB40)
    val timestampText: Color  get() = if (isLight) Color(0xFF424242) else Color.White.copy(alpha = 0.7f)
    val rawLabelText: Color   get() = if (isLight) Color(0xFF4527A0) else Color(0xFFB39DDB)
    val totalBytesText: Color get() = if (isLight) Color(0xFF616161) else Color.White.copy(alpha = 0.6f)

    val receivedIdText: Color get() = if (isLight) Color(0xFF2E7D32) else Color(0xFFB2FF59)

    val scrollThumb: Color       get() = if (isLight) Color(0xFFBDBDBD) else Color.White.copy(alpha = 0.3f)
    val scrollThumbActive: Color get() = if (isLight) Color(0xFF757575) else Color.White

    val uploadSurface: Color get() = if (isLight) Color(0xFFE3F2FD) else Color(0xFF2196F3).copy(alpha = 0.2f)
    val uploadBorder: Color  get() = if (isLight) Color(0xFF1976D2) else Color(0xFF2196F3)
    val uploadText: Color    get() = if (isLight) Color(0xFF0D47A1) else Color(0xFF64B5F6)

    val getDataButtonColor: Color get() = if (isLight) Color(0xFF388E3C) else Color(0xFF4CAF50)
    val pauseButtonColor: Color   get() = if (isLight) Color(0xFFF57C00) else Color(0xFFFF9800)
    val resetButtonColor: Color   get() = if (isLight) Color(0xFFD32F2F) else Color(0xFFF44336)
    val exportButtonColor: Color  get() = if (isLight) Color(0xFF7B1FA2) else Color(0xFF9C27B0)

    // Chip / badge surfaces
    val chipSelected: Color       get() = if (isLight) Color(0xFF1565C0) else Color(0xFF1E88E5)
    val chipUnselected: Color     get() = if (isLight) Color(0xFFE3F2FD) else Color.White.copy(alpha = 0.08f)
    val chipSelectedText: Color   get() = Color.White
    val chipUnselectedText: Color get() = if (isLight) Color(0xFF1565C0) else Color.White.copy(alpha = 0.7f)
}

// ─── Parsed chunk data model ──────────────────────────────────────────────────

/**
 * One 246-byte chunk parsed from the raw advertisement payload.
 *
 * Layout (per chunk, 0-based byte indices):
 *   [0]       = deviceId          (1 byte,  unsigned)
 *   [1..240]  = XYZ payload       (240 bytes → 80 points × 3 bytes each)
 *   [241..242]= packetId          (2 bytes, big-endian unsigned)
 *   [243..244]= lastSavedPacketId (2 bytes, big-endian unsigned)
 *   [245]     = footer            (1 byte)
 *   ─────────────────────────────────────────────────────
 *   Total = 246 bytes
 */
data class ParsedChunk(
    val chunkIndex: Int,
    val deviceId: Int,
    val packetId: Int,
    val lastSavedPacketId: Int,
    val footer: Int,
    val points: List<Triple<Int, Int, Int>>,  // 80 (X, Y, Z) triples, raw unsigned byte values
    val rawHex: String                         // space-separated hex of the full 246 bytes
)

/**
 * Parse the rawData string (space-separated hex bytes, as stored by File 1's BLE reception
 * logic) into a list of ParsedChunks.  If rawData contains exactly N×246 bytes the result
 * has N chunks.
 */
fun parseRawDataToChunks(rawData: String): List<ParsedChunk> {
    // ── Convert hex string → byte array (File 1's raw storage format) ──────
    val bytes = rawData.trim().split("\\s+".toRegex())
        .filter { it.isNotEmpty() }
        .mapNotNull { hex ->
            try { hex.toInt(16).toByte() } catch (_: Exception) { null }
        }
        .toByteArray()

    val chunkSize = 246
    val numChunks = bytes.size / chunkSize
    if (numChunks == 0) return emptyList()

    return (0 until numChunks).map { chunkIdx ->
        val offset = chunkIdx * chunkSize
        val chunk  = bytes.sliceArray(offset until (offset + chunkSize))

        // Byte 0 → device ID
        val deviceId = chunk[0].toInt() and 0xFF

        // Bytes 1..240 → 80 XYZ points (3 bytes each)
        val points = mutableListOf<Triple<Int, Int, Int>>()
        for (i in 1..238 step 3) {          // 1,4,7,…,238 → 80 iterations
            val x = chunk[i].toInt()     and 0xFF
            val y = chunk[i + 1].toInt() and 0xFF
            val z = chunk[i + 2].toInt() and 0xFF
            points.add(Triple(x, y, z))
        }

        // Bytes 241..242 → packetId (little-endian, matching File 1's BLE reception)
        val packetId = (chunk[241].toInt() and 0xFF) or ((chunk[242].toInt() and 0xFF) shl 8)

        // Bytes 243..244 → lastSavedPacketId (little-endian)
        val lastSavedPacketId = (chunk[243].toInt() and 0xFF) or ((chunk[244].toInt() and 0xFF) shl 8)

        // Byte 245 → footer
        val footer = chunk[245].toInt() and 0xFF

        // Raw hex of this chunk (space-separated uppercase)
        val rawHex = chunk.joinToString(" ") { "%02X".format(it) }

        ParsedChunk(
            chunkIndex        = chunkIdx,
            deviceId          = deviceId,
            packetId          = packetId,
            lastSavedPacketId = lastSavedPacketId,
            footer            = footer,
            points            = points,
            rawHex            = rawHex
        )
    }
}

// ─── BleCommandSender  (from File 1 — deviceAddress: String? fix preserved) ──

class BleCommandSender(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter?         = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser

    private val companyId = 0x0059
    private var currentCallback: AdvertiseCallback? = null

    private fun hasAdvertisePermission(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) ==
                    PackageManager.PERMISSION_GRANTED
        else true

    // deviceAddress is String? so callers can pass the String from File 1 directly
    fun sendCommand(command: ByteArray, deviceAddress: String? = null, durationMs: Long = 5000) {
        if (!hasAdvertisePermission() || advertiser == null || command.isEmpty()) return
        stopAdvertising()

        val finalPayload = if (deviceAddress != null) {
            try {
                val macBytes = deviceAddress.split(":").map { it.toInt(16).toByte() }.toByteArray()
                command + macBytes
            } catch (_: Exception) { command }
        } else command

        val data = AdvertiseData.Builder()
            .addManufacturerData(companyId, finalPayload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        currentCallback = object : AdvertiseCallback() {}

        try {
            advertiser.startAdvertising(settings, data, currentCallback)
            MainScope().launch {
                delay(durationMs)
                stopAdvertising()
            }
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    fun stopAdvertising() {
        if (!hasAdvertisePermission()) return
        try {
            currentCallback?.let {
                advertiser?.stopAdvertising(it)
                currentCallback = null
            }
        } catch (e: SecurityException) { e.printStackTrace() }
    }
}

// ─── DataLoggerScreen ─────────────────────────────────────────────────────────
//
// State / scan / command logic  →  File 1
// Layout / UI chrome            →  File 2

@SuppressLint("MissingPermission")
@Composable
fun DataLoggerScreen(
    deviceAddress: String,
    deviceName: String,
    navController: NavController,
    deviceId: String,
    viewModel: BluetoothScanViewModel<Any> = viewModel(factory = BluetoothScanViewModelFactory(LocalContext.current))
) {
    val context      = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val commandSender  = remember { BleCommandSender(context) }

    var themeMode by remember { mutableStateOf(ThemeMode.DARK) }
    val theme = remember(themeMode) { DataLoggerTheme(isLight = themeMode == ThemeMode.LIGHT) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    // ── Permission + scan start (File 1 logic) ───────────────────────────────
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        }
        val act = context as? Activity
        if (act != null && !act.isFinishing && !act.isDestroyed) {
            viewModel.startContinuousScan(act)
        }
    }

    // ── State (File 1) ───────────────────────────────────────────────────────
    var connectedDevice        by remember { mutableStateOf<BluetoothScanViewModel.BluetoothDevice?>(null) }
    var isRefreshing           by remember { mutableStateOf(false) }
    var isGettingData          by remember { mutableStateOf(false) }
    var isResetting            by remember { mutableStateOf(false) }
    var isPausing              by remember { mutableStateOf(false) }
    var lastPacketCount        by remember { mutableIntStateOf(0) }
    var isUploadingToDashboard by remember { mutableStateOf(false) }
    val activeTimeoutJob        = remember { mutableStateOf<Job?>(null) }
    var pauseRequestedAt       by remember { mutableIntStateOf(-1) }

    val packetHistory by viewModel.dataLoggerPacketHistory.collectAsState()
    val devices       by viewModel.devices.collectAsState()

    val currentDevice by remember(devices, deviceAddress) {
        derivedStateOf {
            devices.find { it.address == deviceAddress }
                ?: devices.find {
                    it.name.contains("DataLogger", ignoreCase = true) ||
                            it.name.contains("Data Logger", ignoreCase = true)
                }
        }
    }

    // ── Side-effects (File 1 logic, unchanged) ───────────────────────────────
    LaunchedEffect(devices) {
        devices.find {
            it.name.contains("DataLogger", ignoreCase = true) ||
                    it.name.contains("Data Logger", ignoreCase = true)
        }?.let { connectedDevice = it }
    }

    DisposableEffect(navController) {
        onDispose {
            commandSender.stopAdvertising()
            viewModel.stopScan()
        }
    }

    LaunchedEffect(packetHistory.size) {
        if (packetHistory.size > lastPacketCount) {
            commandSender.stopAdvertising()
            activeTimeoutJob.value?.cancel()
            activeTimeoutJob.value = null
            isGettingData = false
            isResetting   = false
        }
    }

    LaunchedEffect(packetHistory.isEmpty()) {
        if (packetHistory.isEmpty()) { isResetting = false; isPausing = false; pauseRequestedAt = -1 }
    }

    LaunchedEffect(isGettingData) {
        if (isGettingData) {
            activeTimeoutJob.value?.cancel()
            activeTimeoutJob.value = coroutineScope.launch { delay(40000); isGettingData = false }
        }
    }
    LaunchedEffect(isResetting) {
        if (isResetting) {
            activeTimeoutJob.value?.cancel()
            activeTimeoutJob.value = coroutineScope.launch { delay(40000); isResetting = false }
        }
    }
    LaunchedEffect(isPausing) {
        if (isPausing) {
            activeTimeoutJob.value?.cancel()
            activeTimeoutJob.value = coroutineScope.launch {
                delay(40000); isPausing = false; pauseRequestedAt = -1
            }
        }
    }

    // Pause-at-boundary logic (File 1)
    LaunchedEffect(packetHistory.size) {
        if (pauseRequestedAt >= 0 && isPausing) {
            val nextBoundary = ((pauseRequestedAt / 100) + 1) * 100
            if (packetHistory.size >= nextBoundary) {
                commandSender.sendCommand(
                    byteArrayOf(0x59, 0x00, 0xAA.toByte(), 0xAA.toByte()),
                    deviceAddress,   // String — matches String? param
                    10000
                )
                pauseRequestedAt = -1
                isPausing = false
                activeTimeoutJob.value?.cancel()
                activeTimeoutJob.value = null
            }
        }
    }

    // ── UI (File 2 layout) ───────────────────────────────────────────────────
    val backgroundGradient = Brush.verticalGradient(listOf(theme.backgroundStart, theme.backgroundEnd))

    Box(Modifier.fillMaxSize().background(backgroundGradient), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement   = Arrangement.Top,
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.stopScan(); navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = theme.primaryText)
                }
                Text(
                    "Data Logger",
                    fontFamily  = Monospace,
                    fontSize    = 22.sp,
                    fontWeight  = FontWeight.Bold,
                    color       = theme.primaryText
                )
                Row {
                    IconButton(onClick = {
                        themeMode = if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
                    }) {
                        Icon(
                            if (themeMode == ThemeMode.DARK) Icons.Default.LightMode else Icons.Default.DarkMode,
                            "Toggle theme", tint = theme.primaryText
                        )
                    }
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                if (isRefreshing) return@launch
                                isRefreshing = true
                                try {
                                    viewModel.stopScan()
                                    delay(1000)
                                    val act = context as? Activity
                                    if (act != null && !act.isFinishing && !act.isDestroyed) {
                                        viewModel.startScan(act)
                                    }
                                    delay(15000)
                                } catch (e: Exception) {
                                    println("Refresh error: ${e.message}")
                                } finally { isRefreshing = false }
                            }
                        },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing)
                            CircularProgressIndicator(Modifier.size(24.dp), color = theme.primaryText, strokeWidth = 2.dp)
                        else
                            Icon(Icons.Default.Refresh, "Refresh", tint = theme.primaryText)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Device info row ──────────────────────────────────────────────
            val displayDevice = connectedDevice ?: currentDevice
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Device: ${displayDevice?.address ?: deviceAddress}",
                        fontSize = 12.sp, color = theme.secondaryText, fontFamily = Monospace
                    )
                    Text("Node ID: Data Logger", fontSize = 12.sp, color = theme.secondaryText)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isConnected = connectedDevice != null
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(
                                if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isConnected) "Connected" else "Scanning…",
                        color      = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (isUploadingToDashboard) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    color  = theme.uploadSurface,
                    shape  = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, theme.uploadBorder)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(10.dp), color = theme.uploadText, strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Uploading to Dashboard…", color = theme.uploadText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Action buttons (File 1 commands, File 2 layout) ──────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        activeTimeoutJob.value?.cancel(); activeTimeoutJob.value = null
                        commandSender.stopAdvertising()
                        isResetting = false; isPausing = false; isGettingData = true
                        pauseRequestedAt = -1; lastPacketCount = packetHistory.size
                        // File 1 command bytes
                        commandSender.sendCommand(
                            byteArrayOf(0xBB.toByte(), 0xCC.toByte()),
                            deviceAddress,
                            10000
                        )
                    },
                    colors   = ButtonDefaults.buttonColors(containerColor = theme.getDataButtonColor),
                    modifier = Modifier.weight(1f),
                    enabled  = !isGettingData
                ) {
                    if (isGettingData)
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else
                        Text("Get Data", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        activeTimeoutJob.value?.cancel(); activeTimeoutJob.value = null
                        commandSender.stopAdvertising()
                        isGettingData = false; isResetting = false; isPausing = true
                        pauseRequestedAt = packetHistory.size
                    },
                    colors   = ButtonDefaults.buttonColors(containerColor = theme.pauseButtonColor),
                    modifier = Modifier.weight(1f),
                    enabled  = !isPausing
                ) {
                    if (isPausing) {
                        val next = ((pauseRequestedAt / 100) + 1) * 100
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            Text("→$next", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Pause", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = {
                        activeTimeoutJob.value?.cancel(); activeTimeoutJob.value = null
                        commandSender.stopAdvertising()
                        isGettingData = false; isPausing = false; isResetting = true
                        pauseRequestedAt = -1
                        // File 1 reset command bytes
                        commandSender.sendCommand(
                            byteArrayOf(0xFF.toByte(), 0xFF.toByte()),
                            deviceAddress,
                            10000
                        )
                    },
                    colors   = ButtonDefaults.buttonColors(containerColor = theme.resetButtonColor),
                    modifier = Modifier.weight(1f),
                    enabled  = !isResetting
                ) {
                    if (isResetting)
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else
                        Text("Reset", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Stats card (File 2 layout) ────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = theme.cardBackground),
                border   = BorderStroke(1.dp, theme.cardBorder)
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Packets", color = theme.secondaryText, fontSize = 11.sp)
                            Text(
                                "${packetHistory.size}",
                                color      = theme.primaryText,
                                fontSize   = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = Monospace
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            // Derive chunk + point counts from File 1's raw hex data
                            val totalChunks = packetHistory.sumOf { pkt ->
                                val byteCount = pkt.rawData.trim()
                                    .split("\\s+".toRegex())
                                    .filter { it.isNotEmpty() }.size
                                byteCount / 246
                            }
                            val totalPoints = totalChunks * 80
                            Text("Total Chunks", color = theme.secondaryText, fontSize = 11.sp)
                            Text(
                                "$totalChunks chunks • $totalPoints pts",
                                color      = theme.getDataButtonColor,
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Monospace
                            )
                        }
                    }
                    if (packetHistory.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        val receivedIds = packetHistory
                            .filterIsInstance<BluetoothScanViewModel.SensorData.DataLoggerData>()
                            .map { it.lastPacketId }.distinct().sorted()
                        Text(
                            "Received IDs: ${receivedIds.joinToString(", ")}",
                            color    = theme.receivedIdText,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isPausing && pauseRequestedAt >= 0) {
                            val nextBoundary = ((pauseRequestedAt / 100) + 1) * 100
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "⏸ Pausing at packet #$nextBoundary (${packetHistory.size}/$nextBoundary received)",
                                color      = theme.pauseButtonColor,
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Export button (File 2 UI + File 2's richer CSV using parseRawDataToChunks) ──
            var isExporting by remember { mutableStateOf(false) }
            val createDocumentLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/csv")
            ) { uri: Uri? ->
                if (uri != null && packetHistory.isNotEmpty()) {
                    isExporting = true
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                context.contentResolver.openOutputStream(uri)?.use { os ->
                                    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                                    os.write(
                                        "Timestamp,Packet_Index,Chunk_Index,Device_ID,Packet_ID,Last_Saved_Packet_ID,Footer,Point_Start,Point_End,X_Values,Y_Values,Z_Values,Raw_Hex\n"
                                            .toByteArray()
                                    )
                                    // packetHistory stores raw hex bytes exactly as received (File 1)
                                    packetHistory.reversed().forEachIndexed { pktIdx, packet ->
                                        val chunks = parseRawDataToChunks(packet.rawData)
                                        chunks.forEach { chunk ->
                                            val pointStart = chunk.chunkIndex * 80 + 1
                                            val pointEnd   = pointStart + 79
                                            val xVals = chunk.points.joinToString(";") { it.first.toString() }
                                            val yVals = chunk.points.joinToString(";") { it.second.toString() }
                                            val zVals = chunk.points.joinToString(";") { it.third.toString() }
                                            val line = buildString {
                                                append(df.format(Date(packet.timestamp))); append(",")
                                                append(pktIdx);                            append(",")
                                                append(chunk.chunkIndex);                  append(",")
                                                append(chunk.deviceId);                    append(",")
                                                append(chunk.packetId);                    append(",")
                                                append(chunk.lastSavedPacketId);           append(",")
                                                append("0x%02X".format(chunk.footer));     append(",")
                                                append(pointStart);                        append(",")
                                                append(pointEnd);                          append(",")
                                                append("\"$xVals\"");                      append(",")
                                                append("\"$yVals\"");                      append(",")
                                                append("\"$zVals\"");                      append(",")
                                                append("\"${chunk.rawHex}\"");             append("\n")
                                            }
                                            os.write(line.toByteArray())
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                withContext(Dispatchers.Main) { isExporting = false }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (packetHistory.isEmpty()) return@Button
                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    createDocumentLauncher.launch("DataLogger_${deviceId}_$ts.csv")
                },
                enabled  = packetHistory.isNotEmpty() && !isExporting,
                colors   = ButtonDefaults.buttonColors(containerColor = theme.exportButtonColor),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(8.dp)
            ) {
                if (isExporting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Exporting…", color = Color.White, fontSize = 12.sp)
                    }
                } else {
                    Text("Export CSV", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Packet list (File 2 UI) ──────────────────────────────────────
            if (packetHistory.isNotEmpty()) {
                val listState = rememberLazyListState()

                LaunchedEffect(packetHistory.size) {
                    if (packetHistory.isNotEmpty()) listState.animateScrollToItem(0)
                }

                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state          = listState,
                        modifier       = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(
                            items = packetHistory.reversed(),
                            key   = { idx, _ -> idx }
                        ) { idx, packet ->
                            DataLoggerPacketCard(
                                packetIndex = packetHistory.size - 1 - idx,
                                packet      = packet,
                                theme       = theme
                            )
                        }
                    }
                    DraggableScrollbar(
                        state    = listState,
                        theme    = theme,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 2.dp, top = 8.dp, bottom = 8.dp)
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Device type: Data Logger", color = theme.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(color = theme.primaryText, strokeWidth = 2.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Waiting for data packets…", color = theme.secondaryText, fontSize = 12.sp)
                    Text("Found ${devices.size} BLE devices", color = theme.hintText, fontSize = 11.sp)
                }
            }
        }
    }
}

// ─── DataLoggerPacketCard ─────────────────────────────────────────────────────
//
// One card per received packet (which may contain 1-6 chunks of 246 bytes each).
// Chunks are parsed live from rawData using parseRawDataToChunks() which
// handles the raw hex bytes stored by File 1's BLE reception logic.

@Composable
fun DataLoggerPacketCard(
    packetIndex: Int,
    packet: BluetoothScanViewModel.SensorData.DataLoggerData,
    theme: DataLoggerTheme
) {
    val context = LocalContext.current
    val totalRawBytes = packet.rawData.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size

    Card(
        modifier         = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        backgroundColor  = theme.cardBackground,
        border           = BorderStroke(1.dp, theme.cardBorder),
        shape            = RoundedCornerShape(16.dp),
        elevation        = 2.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .background(theme.getDataButtonColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "#${packetIndex + 1}",
                            color      = Color.White,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Monospace
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Packet ID: ${packet.lastPacketId}",
                            color      = theme.primaryText,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Total Expected: ${packet.currentPacketId}",
                            color    = theme.secondaryText,
                            fontSize = 10.sp
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(packet.timestamp)),
                        color      = theme.hintText,
                        fontSize   = 10.sp,
                        fontFamily = Monospace
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Raw Data Header & Copy
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "RAW DATA  ($totalRawBytes bytes)",
                    color         = theme.rawLabelText,
                    fontSize      = 9.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                )
                IconButton(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("Raw Data", packet.rawData)
                            )
                            android.widget.Toast.makeText(context, "Copied raw data", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Copy failed", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy raw data",
                        tint               = theme.rawDataText,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // Raw Data Hex View
            Surface(color = theme.rawDataSurface, shape = RoundedCornerShape(8.dp)) {
                val allHexRows = packet.rawData.trim()
                    .split("\\s+".toRegex())
                    .filter { it.isNotEmpty() }
                    .chunked(16)
                    .mapIndexed { rowIdx, row ->
                        "%04d  ".format(rowIdx * 16) + row.joinToString(" ")
                    }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    allHexRows.forEach { row ->
                        Text(
                            row,
                            color      = theme.rawDataText,
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 9.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}



// ─── DraggableScrollbar ───────────────────────────────────────────────────────

@Composable
fun DraggableScrollbar(
    state: LazyListState,
    theme: DataLoggerTheme,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(50.dp)) {
        val maxHeightPx  = constraints.maxHeight.toFloat()
        val totalItems   = state.layoutInfo.totalItemsCount
        val visibleItems = state.layoutInfo.visibleItemsInfo

        if (totalItems > visibleItems.size && visibleItems.isNotEmpty()) {
            val thumbHeightPx = max(80f, maxHeightPx * (visibleItems.size.toFloat() / totalItems))
            val trackHeightPx = maxHeightPx - thumbHeightPx
            val scrollOffset  = state.firstVisibleItemIndex.toFloat() /
                    (totalItems - visibleItems.size).coerceAtLeast(1)
            val thumbOffsetY  = with(LocalDensity.current) { (scrollOffset * trackHeightPx).toDp() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(totalItems, maxHeightPx) {
                        detectVerticalDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd   = { isDragging = false },
                            onVerticalDrag = { change, _ ->
                                val ratio = (change.position.y / maxHeightPx).coerceIn(0f, 1f)
                                val item  = (ratio * totalItems).toInt().coerceIn(0, totalItems - 1)
                                coroutineScope.launch { state.scrollToItem(item) }
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = thumbOffsetY)
                        .align(Alignment.TopEnd)
                        .width(6.dp)
                        .height(with(LocalDensity.current) { thumbHeightPx.toDp() })
                        .background(
                            if (isDragging) theme.scrollThumbActive else theme.scrollThumb,
                            RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}