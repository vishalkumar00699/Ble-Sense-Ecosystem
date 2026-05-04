package com.blesense.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.ParcelUuid
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*

import com.blesense.app.ui.theme.BleSenseColors
import kotlinx.coroutines.launch

// ── Design tokens ──────────────────────────────────────────────────────────────
private val DarkBackground  = BleSenseColors.BackgroundDark
private val DarkSurface     = BleSenseColors.SurfaceDark
private val DarkCard        = BleSenseColors.SurfaceLight
private val DarkCard2       = BleSenseColors.SurfaceDark
private val DarkDivider     = BleSenseColors.SurfaceLight
private val GreenAccent     = BleSenseColors.PrimaryGreen
private val GreenDark       = BleSenseColors.PrimaryGreenDark
private val GreenMuted      = BleSenseColors.PrimaryGreen.copy(alpha = 0.15f)
private val BlueAccent      = BleSenseColors.BluetoothBlue
private val YellowAccent    = BleSenseColors.YellowAccent
private val OrangeAccent    = BleSenseColors.OrangeAccent
private val PurpleAccent    = BleSenseColors.PurpleAccent
private val TealAccent      = BleSenseColors.PrimaryGreenLight
private val RedAccent       = BleSenseColors.ErrorRed
private val TextPrimary     = BleSenseColors.TextPrimary
private val TextSecondary   = BleSenseColors.TextSecondary

// ══════════════════════════════════════════════════════════════════════════════
// ADVERTISER DATA MODELS
// ══════════════════════════════════════════════════════════════════════════════

private enum class AdvertisingMode(val label: String) {
    LOW_LATENCY("Low Latency (100 ms)"),
    BALANCED("Balanced (250 ms)"),
    LOW_POWER("Low Power (1000 ms)")
}

private enum class TxPowerLevel(val label: String, val dbm: String) {
    ULTRA_LOW("Ultra Low", "-21 dBm"),
    LOW("Low", "-15 dBm"),
    MEDIUM("Medium", "-7 dBm"),
    HIGH("High", "+1 dBm")
}

private enum class AdvertisingEventType(val label: String) {
    ADV_IND("ADV_IND — connectable, scannable, undirected"),
    ADV_SCAN_IND("ADV_SCAN_IND — non-connectable, scannable"),
    ADV_NONCONN_IND("ADV_NONCONN_IND — non-connectable, non-scannable"),
    ADV_DIRECT_IND("ADV_DIRECT_IND — connectable, directed")
}

private enum class OwnAddressType(val label: String) {
    PUBLIC("Public"),
    RANDOM("Random Static"),
    RESOLVABLE("Resolvable Private (RPA)"),
    NON_RESOLVABLE("Non-Resolvable Private")
}

private data class ServiceUuidEntry(
    val id: String = UUID.randomUUID().toString(),
    val uuid: String = "",
    val includeInScanResponse: Boolean = false
)

private data class ServiceDataEntry(
    val id: String = UUID.randomUUID().toString(),
    val uuid: String = "",
    val data: String = ""
)

private data class ManufacturerDataEntry(
    val id: String = UUID.randomUUID().toString(),
    val companyId: String = "0x004C",
    val data: String = "01 00"
)

// ══════════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ══════════════════════════════════════════════════════════════════════════════
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    bluetoothViewModel: BluetoothScanViewModel<Any?>
) {
    val bluetoothDevicesRaw by bluetoothViewModel.devices.collectAsState()
    val bluetoothDevices = remember(bluetoothDevicesRaw) {
        bluetoothDevicesRaw.filter { device ->
            // Always show devices that have sensor data, regardless of name
            if (device.sensorData != null) return@filter true
            
            val name = device.name
            !name.isNullOrBlank() && 
            name != "N/A" && 
            !name.equals("all", ignoreCase = true) &&
            !name.equals("Unknown Device", ignoreCase = true)
        }
    }
    val isScanning       by bluetoothViewModel.isScanning.collectAsState()
    val context          = LocalContext.current
    val activity         = context as ComponentActivity
    val isPermissionGranted = remember { mutableStateOf(checkBluetoothPermissions(context)) }

    var expanded         by remember { mutableStateOf(false) }
    var selectedSensor   by remember { mutableStateOf("All") }
    var showAllDevices   by remember { mutableStateOf(false) }

    val sensorTypes = listOf(
        "All", "SHT40", "LIS3DH", "Lux Sensor", "Soil Sensor",
        "Speed Distance", "Ammonia Sensor", "DataLogger", "TempLogger", "SEN66"
    )

    val isDarkMode       by ThemeManager.isDarkMode.collectAsState()
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }

    // ── Advertiser bottom sheet state ──────────────────────────────────────
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = false
    )
    val coroutineScope = rememberCoroutineScope()

    // Scanning dot pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot"
    )

    LaunchedEffect(Unit) { isPermissionGranted.value = checkBluetoothPermissions(context) }
    BluetoothPermissionHandler(onPermissionsGranted = { isPermissionGranted.value = true })

    val bluetoothStateReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON && isPermissionGranted.value && !isScanning) {
                        bluetoothViewModel.startContinuousScan(activity)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothStateReceiver, filter)
        onDispose { context.unregisterReceiver(bluetoothStateReceiver) }
    }

    DisposableEffect(isPermissionGranted.value, bluetoothAdapter?.isEnabled) {
        if (isPermissionGranted.value && bluetoothAdapter?.isEnabled == true && !isScanning) {
            bluetoothViewModel.startContinuousScan(activity)
        }
        onDispose { bluetoothViewModel.stopScan() }
    }

    // ── Wrap everything in ModalBottomSheetLayout ──────────────────────────
    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetBackgroundColor = DarkBackground,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        sheetContent = {
            AdvertiserSheetContent(
                bluetoothAdapter = bluetoothAdapter,
                onDismiss = {
                    coroutineScope.launch { sheetState.hide() }
                }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.systemBarsPadding(),
            backgroundColor = DarkBackground,
            topBar = {
                TopAppBar(
                    backgroundColor = DarkSurface,
                    elevation = 0.dp
                ) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { navController.navigate("intermediate_screen") },
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(GreenAccent, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = GreenDark,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    "BleSense",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    lineHeight = 18.sp
                                )
                                Text(
                                    "Smart Sensor Hub",
                                    fontSize = 9.sp,
                                    color = TextSecondary,
                                    lineHeight = 11.sp
                                )
                            }
                            if (isScanning) {
                                Row(
                                    modifier = Modifier
                                        .background(Color(0xFF1F2E1F), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(GreenAccent.copy(alpha = dotAlpha), CircleShape)
                                    )
                                    Text("Live", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = GreenAccent)
                                }
                            }
                        }


                    }
                }
            },
            bottomBar = {
                Surface(
                    color = DarkSurface,
                    elevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        BottomNavItem(icon = Icons.Default.Home,      label = "Home",        isSelected = true,  onClick = {})
                        BottomNavItem(icon = Icons.Default.Bluetooth,  label = "Devices",     isSelected = false, onClick = { navController.navigate("home_screen") })
                        BottomNavItem(icon = Icons.Default.Storage,    label = "Data Logger", isSelected = false, onClick = { navController.navigate("data_logger/auto_connect/DataLogger/DataLogger_1") })
                        BottomNavItem(icon = Icons.Default.Settings,   label = "Settings",    isSelected = false, onClick = { navController.navigate("settings_screen") })
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    DashboardHeroBanner(
                        deviceCount = bluetoothDevices.size,
                        isScanning = isScanning
                    )
                }
                item {
                    DashboardStatsRow(
                        connectedCount = bluetoothDevices.size,
                        uptimePct = if (isScanning) "98%" else "—",
                        dataPoints = "${bluetoothDevices.size * 312}"
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sensor Readings", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkCard, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Filter by Sensor", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Box {
                                IconButton(onClick = { expanded = true }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.MoreVert, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(DarkSurface)) {
                                    sensorTypes.forEach { sensor ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(sensor, color = TextPrimary, fontSize = 13.sp) },
                                            onClick = { selectedSensor = sensor; expanded = false }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("All", "SHT40", "LIS3DH", "Lux", "Soil", "SEN66").forEach { tag ->
                                val active = (tag == "All" && selectedSensor == "All") || selectedSensor == tag
                                Box(
                                    modifier = Modifier
                                        .background(if (active) GreenAccent else Color(0xFF252530), RoundedCornerShape(20.dp))
                                        .clickable { selectedSensor = tag }
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(text = tag, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                        color = if (active) GreenDark else TextSecondary)
                                }
                            }
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(DarkCard, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.size(36.dp).background(GreenMuted, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Bluetooth, null, tint = GreenAccent, modifier = Modifier.size(16.dp))
                            }
                            Column {
                                Text("Status", fontSize = 9.sp, color = TextSecondary)
                                Text(if (bluetoothAdapter?.isEnabled == true) "Connected" else "Disconnected",
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(DarkCard, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Bluetooth, null, tint = BlueAccent, modifier = Modifier.size(15.dp))
                                Text("Bluetooth", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                            Switch(
                                checked = bluetoothAdapter?.isEnabled == true,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                    } else {
                                        bluetoothAdapter?.disable()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White, checkedTrackColor = GreenAccent,
                                    uncheckedThumbColor = Color.Gray, uncheckedTrackColor = DarkDivider
                                )
                            )
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Nearby Devices (${bluetoothDevices.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        IconButton(onClick = {
                            if (isPermissionGranted.value && !isScanning) {
                                if (bluetoothAdapter?.isEnabled == true) bluetoothViewModel.startContinuousScan(activity)
                                else context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }
                        }) {
                            Icon(Icons.Default.Refresh, null, tint = GreenAccent)
                        }
                    }
                }
                item {
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = DarkCard, elevation = 0.dp) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            when {
                                !isPermissionGranted.value -> Text("Bluetooth permissions required", textAlign = TextAlign.Center, color = TextPrimary, modifier = Modifier.fillMaxWidth())
                                bluetoothAdapter?.isEnabled != true -> Text("Please enable Bluetooth", textAlign = TextAlign.Center, color = TextPrimary, modifier = Modifier.fillMaxWidth())
                                bluetoothDevices.isEmpty() -> {
                                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (isScanning) {
                                            CircularProgressIndicator(color = GreenAccent)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Scanning for devices...", color = TextPrimary)
                                        } else {
                                            Text("No devices found", color = TextPrimary)
                                        }
                                    }
                                }
                                else -> {
                                    val sht40Devices = bluetoothDevices.filter { it.sensorData is BluetoothScanViewModel.SensorData.SHT40Data }
                                    val otherDevices = bluetoothDevices.filterNot { it.sensorData is BluetoothScanViewModel.SensorData.SHT40Data }
                                    if (sht40Devices.isNotEmpty()) {
                                        SHT40GroupItem(sht40Devices = sht40Devices, navController = navController, isDarkMode = isDarkMode)
                                        Divider(color = DarkDivider, thickness = 0.5.dp)
                                    }
                                    val devicesToShow = if (showAllDevices) otherDevices else otherDevices.take(6)
                                    devicesToShow.forEach { device ->
                                        BluetoothDeviceItem(device = device, navController = navController, isDarkMode = isDarkMode)
                                        Divider(color = DarkDivider, thickness = 0.5.dp)
                                    }
                                    if (otherDevices.size > 6) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp)
                                                .clickable { showAllDevices = !showAllDevices }
                                                .background(Color(0xFF252530), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (showAllDevices) "Show Fewer" else "Show More (${otherDevices.size - 6} hidden)",
                                                modifier = Modifier.padding(12.dp),
                                                color = GreenAccent,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Quick Actions — Advertise now opens bottom sheet ────────
                item {
                    DashboardQuickActions(
                        navController = navController,
                        onAdvertiseClick = {
                            coroutineScope.launch { sheetState.show() }
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ADVERTISER BOTTOM SHEET — full nRF Connect feature set
// ══════════════════════════════════════════════════════════════════════════════
@SuppressLint("MissingPermission")
@Composable
private fun AdvertiserSheetContent(
    bluetoothAdapter: BluetoothAdapter?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // ── BLE state ──────────────────────────────────────────────────────────
    var advertiser          by remember { mutableStateOf<BluetoothLeAdvertiser?>(null) }
    var isAdvertising       by remember { mutableStateOf(false) }
    var advertisingError    by remember { mutableStateOf<String?>(null) }

    // ── Advertising Parameters ─────────────────────────────────────────────
    var advMode             by remember { mutableStateOf(AdvertisingMode.LOW_LATENCY) }
    var txPower             by remember { mutableStateOf(TxPowerLevel.MEDIUM) }
    var eventType           by remember { mutableStateOf(AdvertisingEventType.ADV_IND) }
    var ownAddressType      by remember { mutableStateOf(OwnAddressType.PUBLIC) }
    var isConnectable       by remember { mutableStateOf(true) }
    var isScannable         by remember { mutableStateOf(true) }
    var isLegacy            by remember { mutableStateOf(true) }
    var advInterval         by remember { mutableStateOf("160") }
    var primaryPhy          by remember { mutableStateOf("LE 1M") }

    // ── Advertising Data ───────────────────────────────────────────────────
    var includeDeviceName   by remember { mutableStateOf(true) }
    var includeTxPower      by remember { mutableStateOf(false) }
    var localName           by remember { mutableStateOf("BleSense") }
    var serviceUuids        by remember { mutableStateOf(listOf<ServiceUuidEntry>()) }
    var serviceDataEntries  by remember { mutableStateOf(listOf<ServiceDataEntry>()) }
    var mfrDataEntries      by remember { mutableStateOf(listOf<ManufacturerDataEntry>()) }

    // ── Scan Response Data ─────────────────────────────────────────────────
    var includeScanResp     by remember { mutableStateOf(false) }
    var srDeviceName        by remember { mutableStateOf(false) }
    var srTxPower           by remember { mutableStateOf(false) }
    var srServiceUuids      by remember { mutableStateOf(listOf<ServiceUuidEntry>()) }
    var srMfrData           by remember { mutableStateOf(listOf<ManufacturerDataEntry>()) }

    // ── UI ─────────────────────────────────────────────────────────────────
    var expandedSection     by remember { mutableStateOf<String?>("params") }
    var showModeMenu        by remember { mutableStateOf(false) }
    var showTxMenu          by remember { mutableStateOf(false) }
    var showEventMenu       by remember { mutableStateOf(false) }
    var showAddrMenu        by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "adv_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "adv_dot"
    )

    val advertiseCallback = remember {
        object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                isAdvertising = true; advertisingError = null
            }
            override fun onStartFailure(errorCode: Int) {
                isAdvertising = false
                advertisingError = when (errorCode) {
                    ADVERTISE_FAILED_ALREADY_STARTED      -> "Already started"
                    ADVERTISE_FAILED_DATA_TOO_LARGE       -> "Data too large — max 31 bytes"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED  -> "Feature not supported on this device"
                    ADVERTISE_FAILED_INTERNAL_ERROR       -> "Internal error"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers active"
                    else                                  -> "Unknown error ($errorCode)"
                }
            }
        }
    }

    fun hasAdvPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
    else true

    fun startAdvertising() {
        if (!hasAdvPermission()) { advertisingError = "BLUETOOTH_ADVERTISE permission required"; return }
        if (bluetoothAdapter?.isEnabled != true) { advertisingError = "Bluetooth is disabled"; return }
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) { advertisingError = "BLE advertising not supported on this device"; return }

        val mode = when (advMode) {
            AdvertisingMode.LOW_LATENCY -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
            AdvertisingMode.BALANCED    -> AdvertiseSettings.ADVERTISE_MODE_BALANCED
            AdvertisingMode.LOW_POWER   -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
        }
        val txPwr = when (txPower) {
            TxPowerLevel.ULTRA_LOW -> AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW
            TxPowerLevel.LOW       -> AdvertiseSettings.ADVERTISE_TX_POWER_LOW
            TxPowerLevel.MEDIUM    -> AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
            TxPowerLevel.HIGH      -> AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(mode)
            .setTxPowerLevel(txPwr)
            .setConnectable(isConnectable)
            .setTimeout(0)
            .build()

        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(includeDeviceName)
            .setIncludeTxPowerLevel(includeTxPower)

        serviceUuids.forEach { e ->
            runCatching { dataBuilder.addServiceUuid(ParcelUuid(UUID.fromString(expandUuid(e.uuid)))) }
        }
        serviceDataEntries.forEach { e ->
            runCatching {
                dataBuilder.addServiceData(
                    ParcelUuid(UUID.fromString(expandUuid(e.uuid))),
                    hexToBytes(e.data)
                )
            }
        }
        mfrDataEntries.forEach { e ->
            runCatching {
                dataBuilder.addManufacturerData(e.companyId.removePrefix("0x").toInt(16), hexToBytes(e.data))
            }
        }

        if (includeScanResp) {
            val srBuilder = AdvertiseData.Builder()
                .setIncludeDeviceName(srDeviceName)
                .setIncludeTxPowerLevel(srTxPower)
            srServiceUuids.forEach { e ->
                runCatching { srBuilder.addServiceUuid(ParcelUuid(UUID.fromString(expandUuid(e.uuid)))) }
            }
            srMfrData.forEach { e ->
                runCatching { srBuilder.addManufacturerData(e.companyId.removePrefix("0x").toInt(16), hexToBytes(e.data)) }
            }
            advertiser?.startAdvertising(settings, dataBuilder.build(), srBuilder.build(), advertiseCallback)
        } else {
            advertiser?.startAdvertising(settings, dataBuilder.build(), advertiseCallback)
        }
    }

    fun stopAdvertising() {
        if (hasAdvPermission()) advertiser?.stopAdvertising(advertiseCallback)
        isAdvertising = false; advertisingError = null
    }

    // ── Sheet UI ───────────────────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxWidth()) {

        // Handle bar
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .width(40.dp)
                .height(4.dp)
                .background(Color(0xFF444455), RoundedCornerShape(2.dp))
                .align(Alignment.CenterHorizontally)
        )

        // Sheet header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isAdvertising) GreenAccent else PurpleAccent,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = if (isAdvertising) GreenDark else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("BLE Advertiser", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                Text(
                    if (isAdvertising) "Broadcasting active" else "Configure & broadcast BLE packets",
                    fontSize = 11.sp, color = if (isAdvertising) GreenAccent else TextSecondary
                )
            }
            if (isAdvertising) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFF1F2E1F), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(GreenAccent.copy(alpha = pulseAlpha), CircleShape))
                    Text("LIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GreenAccent)
                }
            }
            IconButton(onClick = { stopAdvertising(); onDismiss() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        Divider(color = DarkCard, thickness = 0.5.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ── Start / Stop button ────────────────────────────────────────
            item {
                Button(
                    onClick = { if (isAdvertising) stopAdvertising() else startAdvertising() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isAdvertising) RedAccent else GreenAccent,
                        contentColor    = if (isAdvertising) Color.White else GreenDark
                    ),
                    elevation = ButtonDefaults.elevation(0.dp)
                ) {
                    Icon(
                        imageVector = if (isAdvertising) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isAdvertising) "Stop Advertising" else "Start Advertising",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }
            }

            // ── Error banner ───────────────────────────────────────────────
            if (advertisingError != null) {
                item {
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = RedAccent.copy(alpha = 0.12f)) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = RedAccent, modifier = Modifier.size(16.dp))
                            Text(advertisingError ?: "", color = RedAccent, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { advertisingError = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, null, tint = RedAccent, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════
            // SECTION 1 — Advertising Parameters
            // ══════════════════════════════════════════════════════════════
            item {
                AdvSection(
                    title = "Advertising Parameters",
                    icon = Icons.Default.Settings,
                    iconTint = BlueAccent,
                    isExpanded = expandedSection == "params",
                    onToggle = { expandedSection = if (expandedSection == "params") null else "params" }
                ) {
                    // Advertising Mode
                    AdvDropdownField("Advertising Mode", advMode.label, showModeMenu, { showModeMenu = it }) {
                        AdvertisingMode.values().forEach { m ->
                            DropdownMenuItem(onClick = { advMode = m; showModeMenu = false }) {
                                Text(m.label, color = TextPrimary, fontSize = 13.sp)
                            }
                        }
                    }

                    // TX Power
                    AdvDropdownField("TX Power Level", "${txPower.label}  (${txPower.dbm})", showTxMenu, { showTxMenu = it }) {
                        TxPowerLevel.values().forEach { t ->
                            DropdownMenuItem(onClick = { txPower = t; showTxMenu = false }) {
                                Column {
                                    Text(t.label, color = TextPrimary, fontSize = 13.sp)
                                    Text(t.dbm, color = TextSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Advertising Event Type
                    AdvDropdownField("Event Type", eventType.name, showEventMenu, { showEventMenu = it }) {
                        AdvertisingEventType.values().forEach { et ->
                            DropdownMenuItem(onClick = { eventType = et; showEventMenu = false }) {
                                Column {
                                    Text(et.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(et.label.substringAfter("—").trim(), color = TextSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Own Address Type
                    AdvDropdownField("Own Address Type", ownAddressType.label, showAddrMenu, { showAddrMenu = it }) {
                        OwnAddressType.values().forEach { at ->
                            DropdownMenuItem(onClick = { ownAddressType = at; showAddrMenu = false }) {
                                Text(at.label, color = TextPrimary, fontSize = 13.sp)
                            }
                        }
                    }

                    // Advertising Interval
                    AdvTextField(
                        label = "Advertising Interval (×0.625 ms)",
                        value = advInterval,
                        onValueChange = { advInterval = it },
                        placeholder = "160",
                        keyboardType = KeyboardType.Number,
                        trailingLabel = "${(advInterval.toIntOrNull() ?: 160) * 0.625} ms"
                    )

                    // Primary PHY
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Primary PHY", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("LE 1M", "LE 2M", "LE Coded").forEach { phy ->
                                AdvPhyChip(phy, primaryPhy == phy) { primaryPhy = phy }
                            }
                        }
                    }

                    Divider(color = DarkCard2, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                    AdvToggleRow("Legacy Advertising PDU", isLegacy) { isLegacy = it }
                    AdvToggleRow("Connectable", isConnectable) { isConnectable = it }
                    AdvToggleRow("Scannable", isScannable) { isScannable = it }
                }
            }

            // ══════════════════════════════════════════════════════════════
            // SECTION 2 — Advertising Data
            // ══════════════════════════════════════════════════════════════
            item {
                AdvSection(
                    title = "Advertising Data",
                    icon = Icons.Default.DataObject,
                    iconTint = GreenAccent,
                    isExpanded = expandedSection == "adv_data",
                    onToggle = { expandedSection = if (expandedSection == "adv_data") null else "adv_data" }
                ) {
                    // Device name
                    AdvToggleRow("Include Device Name", includeDeviceName) { includeDeviceName = it }
                    AnimatedVisibility(visible = includeDeviceName) {
                        AdvTextField("Local Name", localName, { localName = it }, "BleSense")
                    }
                    AdvToggleRow("Include TX Power Level", includeTxPower) { includeTxPower = it }

                    Divider(color = DarkCard2, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                    // Service UUIDs
                    AdvSubHeader("Service UUIDs", GreenAccent) { serviceUuids = serviceUuids + ServiceUuidEntry() }
                    serviceUuids.forEachIndexed { i, entry ->
                        AdvServiceUuidCard(
                            entry = entry,
                            onUuidChange = { serviceUuids = serviceUuids.toMutableList().also { l -> l[i] = entry.copy(uuid = it) } },
                            onScanRespChange = { serviceUuids = serviceUuids.toMutableList().also { l -> l[i] = entry.copy(includeInScanResponse = it) } },
                            onRemove = { serviceUuids = serviceUuids.toMutableList().also { l -> l.removeAt(i) } }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Divider(color = DarkCard2, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                    // Service Data
                    AdvSubHeader("Service Data", BlueAccent) { serviceDataEntries = serviceDataEntries + ServiceDataEntry() }
                    serviceDataEntries.forEachIndexed { i, entry ->
                        AdvServiceDataCard(
                            entry = entry,
                            onUuidChange = { serviceDataEntries = serviceDataEntries.toMutableList().also { l -> l[i] = entry.copy(uuid = it) } },
                            onDataChange = { serviceDataEntries = serviceDataEntries.toMutableList().also { l -> l[i] = entry.copy(data = it) } },
                            onRemove = { serviceDataEntries = serviceDataEntries.toMutableList().also { l -> l.removeAt(i) } }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Divider(color = DarkCard2, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                    // Manufacturer Specific Data
                    AdvSubHeader("Manufacturer Specific Data", OrangeAccent) { mfrDataEntries = mfrDataEntries + ManufacturerDataEntry() }
                    mfrDataEntries.forEachIndexed { i, entry ->
                        AdvManufacturerCard(
                            entry = entry,
                            onCompanyIdChange = { mfrDataEntries = mfrDataEntries.toMutableList().also { l -> l[i] = entry.copy(companyId = it) } },
                            onDataChange = { mfrDataEntries = mfrDataEntries.toMutableList().also { l -> l[i] = entry.copy(data = it) } },
                            onRemove = { mfrDataEntries = mfrDataEntries.toMutableList().also { l -> l.removeAt(i) } }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Payload size bar
                    val estSize = estimatePayloadSize(includeDeviceName, localName, includeTxPower, serviceUuids, serviceDataEntries, mfrDataEntries)
                    AdvPayloadSizeBar(estSize, 31)
                }
            }

            // ══════════════════════════════════════════════════════════════
            // SECTION 3 — Scan Response
            // ══════════════════════════════════════════════════════════════
            item {
                AdvSection(
                    title = "Scan Response Data",
                    icon = Icons.Default.FindInPage,
                    iconTint = YellowAccent,
                    isExpanded = expandedSection == "scan_resp",
                    onToggle = { expandedSection = if (expandedSection == "scan_resp") null else "scan_resp" }
                ) {
                    AdvToggleRow("Include Scan Response", includeScanResp) { includeScanResp = it }
                    AnimatedVisibility(visible = includeScanResp, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Spacer(modifier = Modifier.height(4.dp))
                            AdvToggleRow("Include Device Name", srDeviceName) { srDeviceName = it }
                            AdvToggleRow("Include TX Power Level", srTxPower) { srTxPower = it }

                            AdvSubHeader("Service UUIDs", GreenAccent) { srServiceUuids = srServiceUuids + ServiceUuidEntry() }
                            srServiceUuids.forEachIndexed { i, entry ->
                                AdvServiceUuidCard(
                                    entry = entry, showScanRespToggle = false,
                                    onUuidChange = { srServiceUuids = srServiceUuids.toMutableList().also { l -> l[i] = entry.copy(uuid = it) } },
                                    onRemove = { srServiceUuids = srServiceUuids.toMutableList().also { l -> l.removeAt(i) } }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            AdvSubHeader("Manufacturer Specific Data", OrangeAccent) { srMfrData = srMfrData + ManufacturerDataEntry() }
                            srMfrData.forEachIndexed { i, entry ->
                                AdvManufacturerCard(
                                    entry = entry,
                                    onCompanyIdChange = { srMfrData = srMfrData.toMutableList().also { l -> l[i] = entry.copy(companyId = it) } },
                                    onDataChange = { srMfrData = srMfrData.toMutableList().also { l -> l[i] = entry.copy(data = it) } },
                                    onRemove = { srMfrData = srMfrData.toMutableList().also { l -> l.removeAt(i) } }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            val srSize = estimatePayloadSize(srDeviceName, localName, srTxPower, srServiceUuids, emptyList(), srMfrData)
                            AdvPayloadSizeBar(srSize, 31)
                        }
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════
            // SECTION 4 — Packet Preview
            // ══════════════════════════════════════════════════════════════
            item {
                AdvSection(
                    title = "Packet Preview",
                    icon = Icons.Default.Preview,
                    iconTint = PurpleAccent,
                    isExpanded = expandedSection == "preview",
                    onToggle = { expandedSection = if (expandedSection == "preview") null else "preview" }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdvPreviewRow("0x01", "Flags", "02 01 06")
                        if (includeDeviceName) AdvPreviewRow("0x09", "Complete Local Name", localName.encodeToByteArray().joinToString(" ") { "%02X".format(it) })
                        if (includeTxPower) AdvPreviewRow("0x0A", "TX Power Level", txPower.dbm)
                        serviceUuids.forEachIndexed { i, e -> if (e.uuid.isNotBlank()) AdvPreviewRow("0x07", "128-bit UUID #${i+1}", e.uuid) }
                        serviceDataEntries.forEachIndexed { i, e -> if (e.uuid.isNotBlank()) AdvPreviewRow("0x16", "Service Data #${i+1}", "${e.uuid}: ${e.data}") }
                        mfrDataEntries.forEachIndexed { i, e -> AdvPreviewRow("0xFF", "Manufacturer Data #${i+1}", "${e.companyId} · ${e.data}") }
                        Divider(color = DarkCard2, thickness = 0.5.dp)
                        AdvPreviewKv("Advertising Mode", advMode.label)
                        AdvPreviewKv("TX Power", txPower.label + " (${txPower.dbm})")
                        AdvPreviewKv("Event Type", eventType.name)
                        AdvPreviewKv("Address Type", ownAddressType.label)
                        AdvPreviewKv("PHY", primaryPhy)
                        AdvPreviewKv("Interval", "${(advInterval.toIntOrNull() ?: 160) * 0.625} ms")
                        AdvPreviewKv("Connectable", if (isConnectable) "Yes" else "No")
                        AdvPreviewKv("Scannable", if (isScannable) "Yes" else "No")
                        AdvPreviewKv("Legacy PDU", if (isLegacy) "Yes" else "No")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ADVERTISER SUB-COMPONENTS (private, scoped to this file)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AdvSection(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = DarkCard, elevation = 0.dp) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.size(32.dp).background(iconTint.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp)) }
                Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = TextSecondary, modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun AdvDropdownField(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
        Box {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onExpandChange(true) },
                shape = RoundedCornerShape(9.dp),
                color = DarkSurface,
                elevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(value, fontSize = 12.sp, color = TextPrimary, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis, maxLines = 1)
                    Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { onExpandChange(false) }, modifier = Modifier.background(DarkSurface)) {
                content()
            }
        }
    }
}

@Composable
private fun AdvTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingLabel: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = TextSecondary, fontSize = 11.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = if (trailingLabel != null) {{
                Text(trailingLabel, fontSize = 9.sp, color = GreenAccent, modifier = Modifier.padding(end = 6.dp))
            }} else null,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = TextPrimary, backgroundColor = DarkSurface,
                focusedBorderColor = GreenAccent, unfocusedBorderColor = Color.Transparent, cursorColor = GreenAccent
            ),
            shape = RoundedCornerShape(9.dp),
            singleLine = true
        )
    }
}

@Composable
private fun AdvToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = TextPrimary)
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = GreenAccent,
                uncheckedThumbColor = Color.Gray, uncheckedTrackColor = DarkSurface
            )
        )
    }
}

@Composable
private fun AdvPhyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) BlueAccent.copy(0.2f) else DarkSurface, RoundedCornerShape(7.dp))
            .border(1.dp, if (selected) BlueAccent else Color.Transparent, RoundedCornerShape(7.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = 10.sp, color = if (selected) BlueAccent else TextSecondary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AdvSubHeader(title: String, color: Color, onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
        IconButton(onClick = onAdd, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Default.AddCircle, null, tint = color, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AdvServiceUuidCard(
    entry: ServiceUuidEntry,
    showScanRespToggle: Boolean = true,
    onUuidChange: (String) -> Unit,
    onScanRespChange: ((Boolean) -> Unit)? = null,
    onRemove: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(9.dp), color = DarkSurface) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Service UUID", fontSize = 9.sp, color = TextSecondary)
                IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.RemoveCircle, null, tint = RedAccent, modifier = Modifier.size(14.dp))
                }
            }
            OutlinedTextField(
                value = entry.uuid, onValueChange = onUuidChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("1800  or  0000180A-0000-1000-8000-00805f9b34fb", color = TextSecondary, fontSize = 9.sp) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = TextPrimary, backgroundColor = DarkCard,
                    focusedBorderColor = GreenAccent, unfocusedBorderColor = Color.Transparent, cursorColor = GreenAccent
                ),
                shape = RoundedCornerShape(7.dp), singleLine = true
            )
            if (showScanRespToggle && onScanRespChange != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Also in Scan Response", fontSize = 10.sp, color = TextSecondary)
                    Switch(
                        checked = entry.includeInScanResponse, onCheckedChange = onScanRespChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GreenAccent, uncheckedThumbColor = Color.Gray, uncheckedTrackColor = DarkCard)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvServiceDataCard(
    entry: ServiceDataEntry,
    onUuidChange: (String) -> Unit,
    onDataChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(9.dp), color = DarkSurface) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Service Data", fontSize = 9.sp, color = TextSecondary)
                IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.RemoveCircle, null, tint = RedAccent, modifier = Modifier.size(14.dp))
                }
            }
            OutlinedTextField(
                value = entry.uuid, onValueChange = onUuidChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Service UUID", fontSize = 9.sp, color = TextSecondary) },
                placeholder = { Text("1800", color = TextSecondary, fontSize = 10.sp) },
                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = TextPrimary, backgroundColor = DarkCard, focusedBorderColor = BlueAccent, unfocusedBorderColor = Color.Transparent, cursorColor = BlueAccent),
                shape = RoundedCornerShape(7.dp), singleLine = true
            )
            OutlinedTextField(
                value = entry.data, onValueChange = onDataChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Data (hex, space-separated)", fontSize = 9.sp, color = TextSecondary) },
                placeholder = { Text("01 02 AB FF", color = TextSecondary, fontSize = 10.sp) },
                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = TextPrimary, backgroundColor = DarkCard, focusedBorderColor = BlueAccent, unfocusedBorderColor = Color.Transparent, cursorColor = BlueAccent),
                shape = RoundedCornerShape(7.dp), singleLine = true
            )
            Text("${entry.data.split(" ").count { it.isNotBlank() }} byte(s)", fontSize = 9.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun AdvManufacturerCard(
    entry: ManufacturerDataEntry,
    onCompanyIdChange: (String) -> Unit,
    onDataChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(9.dp), color = DarkSurface) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Manufacturer Specific Data", fontSize = 9.sp, color = TextSecondary)
                IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.RemoveCircle, null, tint = RedAccent, modifier = Modifier.size(14.dp))
                }
            }
            OutlinedTextField(
                value = entry.companyId, onValueChange = onCompanyIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Company ID  (e.g. 0x004C = Apple)", fontSize = 9.sp, color = TextSecondary) },
                placeholder = { Text("0x004C", color = TextSecondary, fontSize = 10.sp) },
                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = TextPrimary, backgroundColor = DarkCard, focusedBorderColor = OrangeAccent, unfocusedBorderColor = Color.Transparent, cursorColor = OrangeAccent),
                shape = RoundedCornerShape(7.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
            OutlinedTextField(
                value = entry.data, onValueChange = onDataChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Data (hex, space-separated)", fontSize = 9.sp, color = TextSecondary) },
                placeholder = { Text("01 00 DE AD BE EF", color = TextSecondary, fontSize = 10.sp) },
                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = TextPrimary, backgroundColor = DarkCard, focusedBorderColor = OrangeAccent, unfocusedBorderColor = Color.Transparent, cursorColor = OrangeAccent),
                shape = RoundedCornerShape(7.dp), singleLine = true
            )
            val knownVendor = knownVendor(entry.companyId)
            Text(
                "${entry.data.split(" ").count { it.isNotBlank() }} byte(s)  •  $knownVendor",
                fontSize = 9.sp, color = if (knownVendor != "Unknown") OrangeAccent else TextSecondary
            )
        }
    }
}

@Composable
private fun AdvPayloadSizeBar(current: Int, max: Int) {
    val pct   = (current.toFloat() / max).coerceIn(0f, 1f)
    val color = when { pct < 0.7f -> GreenAccent; pct < 0.9f -> YellowAccent; else -> RedAccent }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Estimated payload", fontSize = 9.sp, color = TextSecondary)
            Text("$current / $max bytes", fontSize = 9.sp, color = color, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = pct,
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = color, backgroundColor = DarkSurface
        )
    }
}

@Composable
private fun AdvPreviewRow(adType: String, name: String, value: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(7.dp), color = DarkSurface) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Text(adType, fontSize = 9.sp, color = PurpleAccent, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 9.sp, color = TextSecondary)
                Text(value, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium, overflow = TextOverflow.Ellipsis, maxLines = 2)
            }
        }
    }
}

@Composable
private fun AdvPreviewKv(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, fontSize = 10.sp, color = TextSecondary)
        Text(value, fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DASHBOARD SUB-COMPONENTS (unchanged from original)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DashboardHeroBanner(deviceCount: Int, isScanning: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(20.dp))) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF0D2B1D), Color(0xFF0A1A14)))))
        Box(modifier = Modifier.size(200.dp).align(Alignment.CenterEnd).offset(x = 60.dp).background(GreenAccent.copy(alpha = 0.06f), CircleShape))
        Box(modifier = Modifier.size(120.dp).align(Alignment.TopEnd).offset(x = 20.dp, y = (-20).dp).background(GreenAccent.copy(alpha = 0.04f), CircleShape))
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text("CONNECTED & MONITORING", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GreenAccent, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Your Sensor\nNetwork", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 26.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("$deviceCount devices active · ${deviceCount * 3100} readings", fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun DashboardStatsRow(connectedCount: Int, uptimePct: String, dataPoints: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardStatCard(icon = { Icon(Icons.Default.Wifi, null, tint = GreenAccent, modifier = Modifier.size(20.dp)) }, iconBg = GreenMuted, value = "$connectedCount", label = "Connected", modifier = Modifier.weight(1f))
        DashboardStatCard(icon = { Icon(Icons.Default.Sensors, null, tint = YellowAccent, modifier = Modifier.size(20.dp)) }, iconBg = YellowAccent.copy(alpha = 0.15f), value = uptimePct, label = "Uptime", modifier = Modifier.weight(1f))
        DashboardStatCard(icon = { Icon(Icons.Default.Storage, null, tint = BlueAccent, modifier = Modifier.size(20.dp)) }, iconBg = BlueAccent.copy(alpha = 0.15f), value = dataPoints, label = "Data Pts", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DashboardStatCard(icon: @Composable () -> Unit, iconBg: Color, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = DarkCard, elevation = 0.dp) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(38.dp).background(iconBg, CircleShape), contentAlignment = Alignment.Center) { icon() }
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(label, fontSize = 9.sp, color = TextSecondary)
        }
    }
}

// ── Quick Actions — now takes onAdvertiseClick ─────────────────────────────
@Composable
private fun DashboardQuickActions(navController: NavHostController, onAdvertiseClick: () -> Unit) {
    Column {
        Text("Quick Actions", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(bottom = 12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickActionItem(icon = Icons.Default.Bluetooth, label = "Scan", tint = GreenAccent, bg = GreenMuted,
                onClick = { navController.navigate("home_screen") }, modifier = Modifier.weight(1f))
            QuickActionItem(icon = Icons.Default.SmartToy, label = "Robot", tint = PurpleAccent, bg = PurpleAccent.copy(alpha = 0.15f),
                onClick = { navController.navigate("robot_screen") }, modifier = Modifier.weight(1f))
            // ↓ ADVERTISE — now opens bottom sheet
            QuickActionItem(icon = Icons.Default.GraphicEq, label = "Advertise", tint = YellowAccent, bg = YellowAccent.copy(alpha = 0.15f),
                onClick = onAdvertiseClick, modifier = Modifier.weight(1f))
            QuickActionItem(icon = Icons.Default.BarChart, label = "Analytics", tint = OrangeAccent, bg = OrangeAccent.copy(alpha = 0.15f),
                onClick = { navController.navigate("analytics_screen") }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickActionItem(icon: ImageVector, label: String, tint: Color, bg: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable { onClick() }, shape = RoundedCornerShape(14.dp), color = DarkCard, elevation = 0.dp) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(40.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Text(label, fontSize = 9.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun BottomNavItem(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (isSelected) GreenMuted else Color.Transparent)
            .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (isSelected) GreenAccent else TextSecondary, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal, color = if (isSelected) GreenAccent else TextSecondary)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// HELPERS (unchanged from original + new ones for advertiser)
// ══════════════════════════════════════════════════════════════════════════════

private fun checkBluetoothPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
            .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    } else {
        listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
            .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }
}

private fun expandUuid(short: String): String = when {
    short.length == 4 -> "0000${short}-0000-1000-8000-00805f9b34fb"
    short.length == 8 -> "${short}-0000-1000-8000-00805f9b34fb"
    else              -> short
}

private fun hexToBytes(hex: String): ByteArray =
    hex.trim().split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

private fun estimatePayloadSize(
    includeDeviceName: Boolean, localName: String, includeTxPower: Boolean,
    serviceUuids: List<ServiceUuidEntry>, serviceDataEntries: List<ServiceDataEntry>,
    mfrDataEntries: List<ManufacturerDataEntry>
): Int {
    var size = 0
    if (includeDeviceName) size += 2 + localName.length
    if (includeTxPower) size += 3
    serviceUuids.forEach { size += 18 }
    serviceDataEntries.forEach { e -> size += 4 + e.data.split(" ").count { it.isNotBlank() } }
    mfrDataEntries.forEach { e -> size += 4 + e.data.split(" ").count { it.isNotBlank() } }
    return size
}

private fun knownVendor(hex: String): String = when (hex.uppercase().removePrefix("0X")) {
    "004C" -> "Apple Inc."
    "0006" -> "Microsoft"
    "000F" -> "Broadcom"
    "0059" -> "Nordic Semiconductor"
    "00E0" -> "Google"
    "0499" -> "Ruuvi Innovations"
    "0157" -> "Polar Electro"
    "0138" -> "Garmin"
    "0075" -> "Samsung"
    else   -> "Unknown"
}

// ══════════════════════════════════════════════════════════════════════════════
// EXISTING COMPOSABLES (BluetoothDeviceItem, SHT40GroupItem etc. — unchanged)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun BluetoothDeviceItem(device: BluetoothScanViewModel.BluetoothDevice, navController: NavHostController, isDarkMode: Boolean) {
    val context = LocalContext.current
    val (icon, iconTint, sensorLabel) = when (device.sensorData) {
        is BluetoothScanViewModel.SensorData.SHT40Data         -> Triple(Icons.Filled.Thermostat, Color(0xFF1976D2), "SHT40")
        is BluetoothScanViewModel.SensorData.TempLoggerData    -> Triple(Icons.Filled.Save,        Color(0xFF4CAF50), "TempLogger")
        is BluetoothScanViewModel.SensorData.LIS2DHData        -> Triple(Icons.Filled.Sensors,     OrangeAccent,     "Accel")
        is BluetoothScanViewModel.SensorData.LuxSensorData     -> Triple(Icons.Filled.WbSunny,     YellowAccent,     "Lux")
        is BluetoothScanViewModel.SensorData.SoilSensorData    -> Triple(Icons.Filled.Eco,         OrangeAccent,     "Soil")
        is BluetoothScanViewModel.SensorData.SDTData           -> Triple(Icons.Filled.Speed,       Color(0xFFE91E63), "Speed")
        is BluetoothScanViewModel.SensorData.AmmoniaSensorData -> Triple(Icons.Filled.Science,     Color(0xFFF44336), "NH₃")
        is BluetoothScanViewModel.SensorData.DataLoggerData    -> Triple(Icons.Filled.Storage,     PurpleAccent,     "Logger")
        is BluetoothScanViewModel.SensorData.Sen66Data         -> Triple(Icons.Filled.Eco,         TealAccent,       "SEN66")
        else                                                    -> Triple(Icons.Filled.Bluetooth,   Color.Gray,       "Unknown")
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable {
            try {
                val safeName     = Uri.encode(device.name ?: "Unknown")
                val safeAddress  = Uri.encode(device.address)
                val safeDeviceId = Uri.encode(device.deviceId ?: "Unknown")
                val sensorType   = when (device.sensorData) {
                    is BluetoothScanViewModel.SensorData.SHT40Data         -> "SHT40"
                    is BluetoothScanViewModel.SensorData.TempLoggerData    -> "TempLogger"
                    is BluetoothScanViewModel.SensorData.LIS2DHData        -> "LIS2DH"
                    is BluetoothScanViewModel.SensorData.LuxSensorData     -> "Lux Sensor"
                    is BluetoothScanViewModel.SensorData.SoilSensorData    -> "Soil Sensor"
                    is BluetoothScanViewModel.SensorData.SDTData           -> "Speed Distance"
                    is BluetoothScanViewModel.SensorData.AmmoniaSensorData -> "Ammonia Sensor"
                    is BluetoothScanViewModel.SensorData.DataLoggerData    -> "DataLogger"
                    is BluetoothScanViewModel.SensorData.Sen66Data         -> "sen66"
                    else                                                    -> "Unknown"
                }
                navController.navigate("advertising/$safeName/$safeAddress/${Uri.encode(sensorType)}/$safeDeviceId")
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open device details", Toast.LENGTH_SHORT).show()
            }
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp).background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = sensorLabel, tint = iconTint, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = device.name ?: "Unknown Device", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Surface(shape = RoundedCornerShape(12.dp), color = iconTint.copy(alpha = 0.15f)) {
                    Text(text = sensorLabel, color = iconTint, style = MaterialTheme.typography.caption, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Text(device.address, style = MaterialTheme.typography.caption, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("RSSI: ${device.rssi} dBm", style = MaterialTheme.typography.caption, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            val preview = when (val data = device.sensorData) {
                is BluetoothScanViewModel.SensorData.SHT40Data         -> "Temp: ${data.temperature} °C  •  Hum: ${data.humidity}%"
                is BluetoothScanViewModel.SensorData.TempLoggerData    -> "Temp: ${data.temperature} °C  •  Hum: ${data.humidity}%"
                is BluetoothScanViewModel.SensorData.LIS2DHData        -> "X: ${data.x}  Y: ${data.y}  Z: ${data.z}"
                is BluetoothScanViewModel.SensorData.LuxSensorData     -> "Lux: ${data.lux}"
                is BluetoothScanViewModel.SensorData.SoilSensorData    -> "N:${data.nitrogen} P:${data.phosphorus} K:${data.potassium}  •  ${data.moisture}%"
                is BluetoothScanViewModel.SensorData.SDTData           -> "Speed: ${data.speed} m/s  •  Dist: ${data.distance} m"
                is BluetoothScanViewModel.SensorData.AmmoniaSensorData -> "Ammonia: ${data.ammonia}"
                is BluetoothScanViewModel.SensorData.DataLoggerData    -> {
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(data.timestamp))
                    "Total: ${data.currentPacketId} • ID:${data.lastPacketId} • ${data.payloadAccel.size} pts • $time"
                }
                is BluetoothScanViewModel.SensorData.Sen66Data -> {
                    val aqi = when {
                        data.pm25.toDoubleOrNull()?.let { it <= 12.0 }  == true -> "Good"
                        data.pm25.toDoubleOrNull()?.let { it <= 35.4 }  == true -> "Moderate"
                        data.pm25.toDoubleOrNull()?.let { it <= 55.4 }  == true -> "Unhealthy"
                        else -> "Poor"
                    }
                    "PM2.5: ${data.pm25} μg/m³ • CO₂: ${data.co2} ppm • ${data.temperature}°C • $aqi"
                }
                else -> "No recent data"
            }
            Text(
                text = preview,
                style = MaterialTheme.typography.body2.copy(
                    fontSize = 13.sp,
                    fontWeight = if (device.sensorData is BluetoothScanViewModel.SensorData.SHT40Data ||
                        device.sensorData is BluetoothScanViewModel.SensorData.Sen66Data) FontWeight.Bold else FontWeight.Normal
                ),
                color = iconTint, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun DataLoggerPreview(rawData: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.heightIn(min = 100.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = DarkCard, elevation = 0.dp) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DataLogger Live", style = MaterialTheme.typography.subtitle1, color = GreenAccent, fontWeight = FontWeight.Bold)
            Text(rawData, style = MaterialTheme.typography.body1, color = TextPrimary, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            Text("Packet Size: ${rawData.split(" ").size} bytes", style = MaterialTheme.typography.caption, color = TextSecondary)
        }
    }
}

@Composable
fun SHT40GroupItem(sht40Devices: List<BluetoothScanViewModel.BluetoothDevice>, navController: NavHostController, isDarkMode: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable { navController.navigate("sht40_group") },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp).background(Color(0xFF1976D2).copy(alpha = 0.15f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(id = R.drawable.ic_thermometer), contentDescription = "SHT40 Group", tint = Color(0xFF1976D2), modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SHT40 Sensors", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1976D2).copy(0.15f)) {
                    Text("${sht40Devices.size}", color = Color(0xFF1976D2), style = MaterialTheme.typography.caption, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Text(sht40Devices.joinToString(" • ") { it.address.takeLast(5) }, style = MaterialTheme.typography.caption, color = TextSecondary)
            sht40Devices.forEach { dev ->
                val data = dev.sensorData as? BluetoothScanViewModel.SensorData.SHT40Data
                Text("${dev.address.takeLast(8)} → ${data?.temperature ?: "--"}°C / ${data?.humidity ?: "--"}%", fontSize = 13.sp, color = TextPrimary.copy(alpha = 0.8f))
            }
        }
        Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SHT40GroupScreen(viewModel: BluetoothScanViewModel<Any?>, navController: NavController) {
    val devices   by viewModel.devices.collectAsState()
    val sht40List  = devices.filter { it.sensorData is BluetoothScanViewModel.SensorData.SHT40Data }
    Scaffold(
        backgroundColor = DarkBackground,
        topBar = {
            TopAppBar(backgroundColor = DarkSurface, elevation = 0.dp,
                title = { Text("SHT40 Sensors (${sht40List.size})", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                }
            )
        }
    ) { padding ->
        if (sht40List.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No SHT40 sensors connected", color = TextSecondary) }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(sht40List) { device ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkCard), elevation = CardDefaults.cardElevation(0.dp), shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Sensor: ${device.address}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Spacer(Modifier.height(12.dp))
                        val data = device.sensorData as BluetoothScanViewModel.SensorData.SHT40Data
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            DataCard(label = "Temperature", value = "${data.temperature}°C", cardBackground = Color(0xFF1976D2), advertisingText = AdvertisingText(), textColor = Color.White)
                            DataCard(label = "Humidity",    value = "${data.humidity}%",     cardBackground = Color(0xFF1976D2), advertisingText = AdvertisingText(), textColor = Color.White)
                        }
                    }
                }
            }
        }
    }
}