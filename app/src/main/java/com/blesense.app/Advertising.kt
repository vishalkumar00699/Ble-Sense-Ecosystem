package com.blesense.app

import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── BleSense unified design tokens ─────────────────────────────────────────────
private val BgDark          = Color(0xFF0A0A0F)
private val SurfaceDark     = Color(0xFF141418)
private val CardDark        = Color(0xFF1A1A22)
private val CardDark2       = Color(0xFF12121A)
private val DividerDark     = Color(0xFF252530)
private val GreenAccent     = Color(0xFF00BC7D)
private val GreenDark       = Color(0xFF0D542B)
private val GreenMuted      = Color(0xFF00BC7D26)
private val BlueAccent      = Color(0xFF60A5FA)
private val YellowAccent    = Color(0xFFFBBF24)
private val OrangeAccent    = Color(0xFFFB923C)
private val PurpleAccent    = Color(0xFFA78BFA)
private val TealAccent      = Color(0xFF2DD4BF)
private val RedAccent       = Color(0xFFFF6467)
private val TextPrimary     = Color(0xFFF0F0F0)
private val TextSecondary   = Color(0xFF9F9FA9)

/**
 * Localization-ready text strings for the Advertising Data screen.
 */
data class AdvertisingText(
    val advertisingDataTitle: String = "Advertising Data",
    val deviceNameLabel: String = "Device Name",
    val nodeIdLabel: String = "Node ID",
    val downloadData: String = "DOWNLOAD DATA",
    val exportingData: String = "EXPORTING DATA...",
    val temperature: String = "Temperature",
    val humidity: String = "Humidity",
    val xAxis: String = "X-Axis",
    val yAxis: String = "Y-Axis",
    val zAxis: String = "Z-Axis",
    val nitrogen: String = "Nitrogen",
    val phosphorus: String = "Phosphorus",
    val potassium: String = "Potassium",
    val moisture: String = "Moisture",
    val electricConductivity: String = "Electric Conductivity",
    val pH: String = "pH",
    val salinity: String = "Salinity",
    val lightIntensity: String = "Light Intensity",
    val speed: String = "Speed",
    val distance: String = "Distance",
    val objectDetected: String = "Object Detected",
    val steps: String = "Steps",
    val ammonia: String = "Ammonia",
    val resetSteps: String = "RESET STEPS",
    val warningTitle: String = "Warning",
    val warningMessage: String = "The %s has exceeded the threshold of %s!",
    val dismissButton: String = "Dismiss",
    val rawData: String = "Raw Data"
)

// ══════════════════════════════════════════════════════════════════════════════
// ADVERTISING DATA SCREEN
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun AdvertisingDataScreen(
    deviceAddress: String,
    deviceName: String,
    navController: NavController,
    deviceId: String,
    viewModel: BluetoothScanViewModel<Any?>
) {
    val context  = LocalContext.current
    val activity = context as? Activity

    val viewModel: BluetoothScanViewModel<Any> = viewModel(
        factory = remember { BluetoothScanViewModelFactory(context) }
    )

    LaunchedEffect(activity) {
        activity?.let { viewModel.startScan(it) }
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(Unit) {
        mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply { isLooping = true }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.let { p -> if (p.isPlaying) p.stop(); p.reset(); p.release() }
            mediaPlayer = null
        }
    }

    val isDarkMode   by ThemeManager.isDarkMode.collectAsState()
    val devices      by viewModel.devices.collectAsState()

    val currentDevice by remember(devices, deviceAddress) {
        derivedStateOf { devices.find { it.address == deviceAddress } }
    }

    var thresholdValue   by remember { mutableStateOf("") }
    var isAlarmActive    by remember { mutableStateOf(false) }
    var showAlertDialog  by remember { mutableStateOf(false) }
    var parameterType    by remember { mutableStateOf("Temperature") }
    var isThresholdSet   by remember { mutableStateOf(false) }

    val blinkAlpha by animateFloatAsState(
        targetValue = if (isAlarmActive) 0.5f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    val ammoniaValue by remember(currentDevice?.sensorData) {
        derivedStateOf {
            (currentDevice?.sensorData as? BluetoothScanViewModel.SensorData.AmmoniaSensorData)
                ?.ammonia?.replace(" ppm", "")?.toFloatOrNull() ?: 0f
        }
    }

    var displayedAmmoniaValue by remember { mutableStateOf(0f) }
    LaunchedEffect(ammoniaValue) { displayedAmmoniaValue = ammoniaValue }

    val luxValue by remember(currentDevice?.sensorData) {
        derivedStateOf {
            (currentDevice?.sensorData as? BluetoothScanViewModel.SensorData.LuxSensorData)
                ?.lux?.toFloatOrNull() ?: 0f
        }
    }

    // Threshold monitoring
    LaunchedEffect(currentDevice, thresholdValue, parameterType, isThresholdSet) {
        delay(500L)
        if (isThresholdSet) {
            val threshold = thresholdValue.toFloatOrNull()
            if (threshold != null) {
                when (val sd = currentDevice?.sensorData) {
                    is BluetoothScanViewModel.SensorData.SHT40Data -> {
                        val v = when (parameterType) {
                            "Temperature" -> sd.temperature.toFloatOrNull()
                            "Humidity"    -> sd.humidity.toFloatOrNull()
                            else -> null
                        }
                        isAlarmActive = v != null && v > threshold
                    }
                    is BluetoothScanViewModel.SensorData.AmmoniaSensorData ->
                        isAlarmActive = parameterType == "Ammonia" && ammoniaValue > threshold
                    else -> isAlarmActive = false
                }
                if (isAlarmActive) {
                    showAlertDialog = true
                    mediaPlayer?.let { p -> if (!p.isPlaying) try { p.start() } catch (_: IllegalStateException) {
                        mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply { isLooping = true; start() }
                    }}
                } else {
                    mediaPlayer?.let { p -> try { if (p.isPlaying) { p.stop(); p.prepare() } } catch (_: IllegalStateException) {
                        mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply { isLooping = true }
                    }}
                    showAlertDialog = false
                }
            } else {
                isAlarmActive = false; showAlertDialog = false
                mediaPlayer?.let { p -> try { if (p.isPlaying) { p.stop(); p.prepare() } } catch (_: IllegalStateException) {
                    mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply { isLooping = true }
                }}
            }
        } else {
            isAlarmActive = false; showAlertDialog = false
            mediaPlayer?.let { p -> try { if (p.isPlaying) { p.stop(); p.prepare() } } catch (_: IllegalStateException) {
                mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply { isLooping = true }
            }}
        }
    }

    val advertisingText = AdvertisingText()

    val displayData by remember(currentDevice?.sensorData, advertisingText) {
        derivedStateOf {
            when (val sd = currentDevice?.sensorData) {
                is BluetoothScanViewModel.SensorData.SHT40Data -> listOf(
                    "Device ID" to sd.deviceId,
                    advertisingText.temperature to "${sd.temperature.ifEmpty { "0" }}°C",
                    advertisingText.humidity    to "${sd.humidity.ifEmpty { "0" }}%"
                )
                is BluetoothScanViewModel.SensorData.SDTData -> listOf(
                    "Device ID" to sd.deviceId,
                    advertisingText.speed    to "${sd.speed.ifEmpty { "0" }} m/s",
                    advertisingText.distance to "${sd.distance.ifEmpty { "0" }} m"
                )
                is BluetoothScanViewModel.SensorData.LIS2DHData -> listOf(
                    "Device ID" to sd.deviceId,
                    advertisingText.xAxis to "${sd.x.ifEmpty { "0" }} m/s²",
                    advertisingText.yAxis to "${sd.y.ifEmpty { "0" }} m/s²",
                    advertisingText.zAxis to "${sd.z.ifEmpty { "0" }} m/s²"
                )
                is BluetoothScanViewModel.SensorData.SoilSensorData -> listOf(
                    "Device ID" to sd.deviceId,
                    advertisingText.nitrogen            to "${sd.nitrogen.ifEmpty { "0" }} mg/kg",
                    advertisingText.phosphorus          to "${sd.phosphorus.ifEmpty { "0" }} mg/kg",
                    advertisingText.potassium           to "${sd.potassium.ifEmpty { "0" }} mg/kg",
                    advertisingText.moisture            to "${sd.moisture.ifEmpty { "0" }}%",
                    advertisingText.temperature         to "${sd.temperature.ifEmpty { "0" }}°C",
                    advertisingText.electricConductivity to "${sd.ec.ifEmpty { "0" }} µS/cm",
                    advertisingText.pH                  to sd.pH.ifEmpty { "0" },
                    advertisingText.salinity            to "${sd.salinity.ifEmpty { "0" }} mg/L"
                )
                is BluetoothScanViewModel.SensorData.TempLoggerData -> listOf(
                    "Device ID" to sd.deviceId,
                    advertisingText.temperature to "${sd.temperature}°C",
                    advertisingText.humidity    to "${sd.humidity}%",
                    advertisingText.rawData     to sd.rawData
                )
                is BluetoothScanViewModel.SensorData.AmmoniaSensorData -> listOf(
                    "Device ID" to sd.deviceId,
                    advertisingText.ammonia to sd.ammonia,
                    advertisingText.rawData to sd.rawData
                )
                is BluetoothScanViewModel.SensorData.DataLoggerData -> listOf(
                    "Device ID"                      to sd.deviceId,
                    "Total Stored Packets"           to "${sd.currentPacketId}",
                    "Current Received Packet ID"     to "${sd.lastPacketId}",
                    "Accel Points in Packet"         to "${sd.payloadAccel.size}",
                    "Packet Receive Time"            to SimpleDateFormat("yyyy-MM-dd\nHH:mm:ss", Locale.getDefault()).format(Date(sd.timestamp)),
                    advertisingText.rawData          to sd.rawData
                )
                is BluetoothScanViewModel.SensorData.Sen66Data -> listOf(
                    "Device ID" to sd.deviceId,
                    "PM1.0"     to "${sd.pm1.ifEmpty { "0" }} μg/m³",
                    "PM2.5"     to "${sd.pm25.ifEmpty { "0" }} μg/m³",
                    "PM4.0"     to "${sd.pm4.ifEmpty { "0" }} μg/m³",
                    "PM10"      to "${sd.pm10.ifEmpty { "0" }} μg/m³",
                    advertisingText.temperature to "${sd.temperature.ifEmpty { "0" }}°C",
                    advertisingText.humidity    to "${sd.humidity.ifEmpty { "0" }}%",
                    "CO₂"       to "${sd.co2.ifEmpty { "0" }} ppm",
                    "VOC"       to sd.voc.ifEmpty { "0" },
                    "NOx"       to sd.nox.ifEmpty { "0" },
                    "Air Quality" to sd.airQualityIndex.ifEmpty { "0" }
                )
                else -> emptyList()
            }
        }
    }

    DisposableEffect(navController) {
        onDispose { viewModel.stopScan(); viewModel.clearDevices() }
    }

    // ── Root layout ────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .systemBarsPadding()
    ) {
        // Alarm blink overlay
        if (isAlarmActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RedAccent.copy(alpha = blinkAlpha * 0.4f))
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top App Bar ────────────────────────────────────────────────
            AdvertisingTopBar(
                deviceName   = deviceName,
                deviceAddress = deviceAddress,
                isAlarmActive = isAlarmActive,
                navController = navController,
                viewModel     = viewModel,
                advertisingText = advertisingText
            )

            // ── Scrollable body ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Device info cards
                AdvertisingDeviceInfoSection(
                    deviceName    = deviceName,
                    deviceAddress = deviceAddress,
                    deviceId      = deviceId,
                    advertisingText = advertisingText
                )

                // DataLogger specific
                if (currentDevice?.sensorData is BluetoothScanViewModel.SensorData.DataLoggerData) {
                    val latestPacket = viewModel.latestDataLoggerPacket.collectAsState().value

                    if (latestPacket != null) {
                        // ── 4 info cards in a 2×2 grid (original layout, new colours) ──
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DataLoggerInfoCard(
                                    label = "Total Stored Packets",
                                    value = "${latestPacket.currentPacketId}",
                                    accentColor = GreenAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                DataLoggerInfoCard(
                                    label = "Current Received Packet ID",
                                    value = "${latestPacket.lastPacketId}",
                                    accentColor = BlueAccent,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DataLoggerInfoCard(
                                    label = "Accel Points in Packet",
                                    value = "${latestPacket.payloadAccel.size}",
                                    accentColor = PurpleAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                DataLoggerInfoCard(
                                    label = "Packet Receive Time",
                                    value = SimpleDateFormat(
                                        "HH:mm:ss",
                                        Locale.getDefault()
                                    ).format(Date(latestPacket.timestamp)),
                                    accentColor = TealAccent,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // ── Latest XYZ packet card (original DataLoggerXYZCard layout) ──
                        DataLoggerXYZCard(packet = latestPacket)
                    }

                    // ── Full history with packet list ──────────────────────────
                    DataLoggerDisplay(viewModel = viewModel)
                }

                // SEN66 specific
                if (currentDevice?.sensorData is BluetoothScanViewModel.SensorData.Sen66Data) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = CardDark,
                        tonalElevation = 0.dp
                    ) {
                        Sen66SensorDisplay(
                            sensorData = currentDevice?.sensorData as BluetoothScanViewModel.SensorData.Sen66Data
                        )
                    }
                }

                // General sensor data cards
                ResponsiveDataCards(
                    data            = displayData,
                    cardBackground  = CardDark,
                    advertisingText = advertisingText,
                    textColor       = TextPrimary
                )

                // TempLogger specific
                if (currentDevice?.sensorData is BluetoothScanViewModel.SensorData.TempLoggerData) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(600.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = CardDark,
                        tonalElevation = 0.dp
                    ) {
                        TempLoggerDisplay(
                            viewModel     = viewModel,
                            deviceAddress = deviceAddress,
                            deviceId      = deviceId,
                            deviceName    = deviceName
                        )
                    }
                }

                // Threshold section
                if (currentDevice?.sensorData is BluetoothScanViewModel.SensorData.SHT40Data ||
                    currentDevice?.sensorData is BluetoothScanViewModel.SensorData.AmmoniaSensorData
                ) {
                    ThresholdInputSection(
                        thresholdValue    = thresholdValue,
                        onThresholdChange = { thresholdValue = it },
                        parameterType     = parameterType,
                        onParameterChange = { parameterType = it },
                        isDarkMode        = true,
                        sensorData        = currentDevice?.sensorData,
                        onConfirmThreshold = {
                            if (thresholdValue.toFloatOrNull() != null) isThresholdSet = true
                        }
                    )
                }

                // Download button
                AdvertisingDownloadButton(
                    viewModel     = viewModel,
                    deviceAddress = deviceAddress,
                    deviceName    = deviceName,
                    deviceId      = deviceId,
                    advertisingText = advertisingText
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Alarm dialog
        if (showAlertDialog) {
            AlertDialog(
                onDismissRequest = { dismissAlarm(showAlertDialog = { showAlertDialog = false }, isAlarmActive = { isAlarmActive = false }, isThresholdSet = { isThresholdSet = false }, mediaPlayer = mediaPlayer, context = context) },
                containerColor   = CardDark,
                title = { Text(advertisingText.warningTitle, color = TextPrimary, fontWeight = FontWeight.Bold) },
                text  = { Text(advertisingText.warningMessage.format(parameterType, thresholdValue), color = TextSecondary) },
                confirmButton = {
                    Button(
                        onClick = { dismissAlarm(showAlertDialog = { showAlertDialog = false }, isAlarmActive = { isAlarmActive = false }, isThresholdSet = { isThresholdSet = false }, mediaPlayer = mediaPlayer, context = context) },
                        colors  = ButtonDefaults.buttonColors(containerColor = GreenAccent)
                    ) {
                        Text(advertisingText.dismissButton, color = GreenDark, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Top App Bar
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvertisingTopBar(
    deviceName: String,
    deviceAddress: String,
    isAlarmActive: Boolean,
    navController: NavController,
    viewModel: BluetoothScanViewModel<Any>,
    advertisingText: AdvertisingText
) {
    Surface(
        color     = SurfaceDark,
        tonalElevation = 0.dp,
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = { viewModel.stopScan(); navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Device icon + title
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(GreenMuted, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Sensors, null, tint = GreenAccent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deviceName.ifEmpty { "Unknown Device" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = deviceAddress,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Alarm indicator
            if (isAlarmActive) {
                Box(
                    modifier = Modifier
                        .background(RedAccent.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = RedAccent, modifier = Modifier.size(12.dp))
                        Text("ALARM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RedAccent)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Graph button
            IconButton(onClick = { navController.navigate("chart_screen/$deviceAddress") }) {
                Icon(Icons.Default.BarChart, contentDescription = "Graph", tint = GreenAccent)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Device info section
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun AdvertisingDeviceInfoSection(
    deviceName: String,
    deviceAddress: String,
    deviceId: String,
    advertisingText: AdvertisingText
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = CardDark,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(BlueAccent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bluetooth, null, tint = BlueAccent, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("${advertisingText.deviceNameLabel}", fontSize = 10.sp, color = TextSecondary)
                    Text(
                        "$deviceName  ($deviceAddress)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Divider(color = DividerDark, thickness = 0.5.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(GreenMuted, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tag, null, tint = GreenAccent, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("${advertisingText.nodeIdLabel}", fontSize = 10.sp, color = TextSecondary)
                    Text(deviceId, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            }
        }
    }
}

// ── DataLogger info card — tall card matching original InfoCard layout ─────────
@Composable
private fun DataLoggerInfoCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(90.dp),
        shape    = RoundedCornerShape(16.dp),
        color    = accentColor.copy(alpha = 0.08f),
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.14f),
                            accentColor.copy(alpha = 0.04f)
                        )
                    ),
                    RoundedCornerShape(16.dp)
                )
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text       = label,
                    fontSize   = 10.sp,
                    color      = accentColor.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium,
                    textAlign  = TextAlign.Center,
                    maxLines   = 2,
                    lineHeight = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text       = value,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = accentColor,
                    textAlign  = TextAlign.Center
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Threshold Input Section
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ThresholdInputSection(
    thresholdValue: String,
    onThresholdChange: (String) -> Unit,
    parameterType: String,
    onParameterChange: (String) -> Unit,
    isDarkMode: Boolean,
    sensorData: BluetoothScanViewModel.SensorData?,
    onConfirmThreshold: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = CardDark,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(RedAccent.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.NotificationsActive, null, tint = RedAccent, modifier = Modifier.size(16.dp))
                }
                Text("Threshold Alarm", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }

            // Parameter selector chips
            val parameters = when (sensorData) {
                is BluetoothScanViewModel.SensorData.SHT40Data         -> listOf("Temperature", "Humidity")
                is BluetoothScanViewModel.SensorData.AmmoniaSensorData -> listOf("Ammonia")
                else -> emptyList()
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                parameters.forEach { type ->
                    val selected = parameterType == type
                    Box(
                        modifier = Modifier
                            .background(
                                if (selected) GreenAccent else DividerDark,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { onParameterChange(type) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            type,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) GreenDark else TextSecondary
                        )
                    }
                }
            }

            // Threshold input
            OutlinedTextField(
                value         = thresholdValue,
                onValueChange = onThresholdChange,
                label         = { Text("Enter $parameterType Threshold", color = TextSecondary, fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier      = Modifier.fillMaxWidth(),
                isError       = thresholdValue.isNotEmpty() && thresholdValue.toFloatOrNull() == null,
                singleLine    = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = GreenAccent,
                    unfocusedBorderColor = DividerDark,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary,
                    errorBorderColor     = RedAccent,
                    cursorColor          = GreenAccent
                ),
                supportingText = {
                    if (thresholdValue.isNotEmpty() && thresholdValue.toFloatOrNull() == null) {
                        Text("Please enter a valid number", color = RedAccent, fontSize = 11.sp)
                    }
                }
            )

            // Confirm button
            Button(
                onClick  = onConfirmThreshold,
                enabled  = thresholdValue.isNotEmpty() && thresholdValue.toFloatOrNull() != null,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = GreenAccent,
                    disabledContainerColor = DividerDark
                )
            ) {
                Text("Set Alarm Threshold", fontWeight = FontWeight.Bold, color = GreenDark)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Download Button
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun AdvertisingDownloadButton(
    viewModel: BluetoothScanViewModel<Any>,
    deviceAddress: String,
    deviceName: String,
    deviceId: String,
    advertisingText: AdvertisingText
) {
    val context     = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            exportDataToCSV(context, uri, viewModel, deviceAddress, deviceName, deviceId) {
                isExporting = false
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = {
                val ts  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                createDocumentLauncher.launch("sensor_data_${deviceId}_$ts.csv")
            },
            modifier = Modifier.weight(1f).height(48.dp),
            shape    = RoundedCornerShape(12.dp),
            enabled  = !isExporting,
            colors   = ButtonDefaults.buttonColors(containerColor = GreenAccent)
        ) {
            Icon(Icons.Default.Download, null, tint = GreenDark, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (isExporting) advertisingText.exportingData else advertisingText.downloadData,
                color      = GreenDark,
                fontWeight = FontWeight.Bold,
                fontSize   = 12.sp
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Responsive Data Cards
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ResponsiveDataCards(
    data: List<Pair<String, String>>,
    cardBackground: Color,
    advertisingText: AdvertisingText,
    textColor: Color
) {
    val ammoniaData = data.find { it.first.contains("Ammonia", ignoreCase = true) }
    val rawDataItem = data.find { it.first.contains("Raw Data", ignoreCase = true) }
    val otherData   = data.filterNot {
        it.first.contains("Ammonia", ignoreCase = true) ||
                it.first.contains("Raw Data", ignoreCase = true) ||
                listOf("PM1.0","PM2.5","PM4.0","PM10","CO₂","VOC","NOx","Air Quality")
                    .any { kw -> it.first.contains(kw, ignoreCase = true) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Ammonia ring
        ammoniaData?.let { (label, value) ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                color    = CardDark,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    AmmoniaRingAnimation(ammoniaValue = value.replace(" ppm", "").toFloatOrNull() ?: 0f)
                }
            }
        }

        // Raw data
        rawDataItem?.let { (_, value) ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                color    = CardDark,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Code, null, tint = PurpleAccent, modifier = Modifier.size(16.dp))
                        Text("Raw Sensor Data", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CardDark2
                    ) {
                        Text(
                            text     = value,
                            fontSize = 11.sp,
                            color    = PurpleAccent,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Sensor value cards
        if (otherData.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                color    = CardDark,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (otherData.size) {
                        1 -> DataCard(
                            label = otherData[0].first,
                            value = otherData[0].second,
                            cardBackground = cardBackground,
                            advertisingText = advertisingText,
                            textColor = textColor
                        )
                        else -> {
                            otherData.chunked(2).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    row.forEach { (label, value) ->
                                        DataCard(
                                            label  = label,
                                            value  = value,
                                            cardBackground  = cardBackground,
                                            advertisingText = advertisingText,
                                            textColor = textColor,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Individual Data Card
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun DataCard(
    label: String,
    value: String,
    cardBackground: Color,
    advertisingText: AdvertisingText,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val numericValue = value.replace("[^0-9.]".toRegex(), "").toFloatOrNull() ?: 0f

    val accentColor = when {
        label == advertisingText.temperature -> when {
            numericValue <= 15f -> BlueAccent
            numericValue <= 30f -> GreenAccent
            else               -> RedAccent
        }
        label == advertisingText.humidity -> when {
            numericValue <= 40f -> BlueAccent
            numericValue <= 70f -> GreenAccent
            else               -> RedAccent
        }
        label.contains("pH", ignoreCase = true)     -> TealAccent
        label.contains("lux", ignoreCase = true)    -> YellowAccent
        label.contains("speed", ignoreCase = true)  -> PurpleAccent
        label.contains("nitrogen", ignoreCase = true) ||
                label.contains("phosphorus", ignoreCase = true) ||
                label.contains("potassium", ignoreCase = true) -> OrangeAccent
        else -> GreenAccent
    }

    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        color    = accentColor.copy(alpha = 0.10f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text      = label,
                fontSize  = 10.sp,
                color     = TextSecondary,
                fontWeight = FontWeight.Medium,
                textAlign  = TextAlign.Center
            )
            Text(
                text      = value,
                fontSize  = 18.sp,
                color     = accentColor,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                maxLines   = 2,
                lineHeight = 22.sp
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Ammonia Ring Animation (unchanged logic, colours updated)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun AmmoniaRingAnimation(ammoniaValue: Float, modifier: Modifier = Modifier) {
    val animatedFill by animateFloatAsState(
        targetValue = (ammoniaValue / 100f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 100f),
        label = "ammoniaFill"
    )
    val liquidColor by animateColorAsState(
        targetValue = when {
            ammoniaValue <= 25 -> GreenAccent
            ammoniaValue <= 50 -> YellowAccent
            else               -> RedAccent
        },
        animationSpec = tween(300),
        label = "liquidColor"
    )
    Box(
        modifier = modifier.size(220.dp).padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center    = Offset(size.width / 2, size.height / 2)
            val radius    = size.minDimension * 0.4f
            val ringWidth = size.minDimension * 0.1f
            drawArc(color = DividerDark, startAngle = 270f, sweepAngle = 360f, useCenter = false,
                size = Size(radius*2, radius*2), topLeft = Offset(center.x-radius, center.y-radius),
                style = Stroke(width = ringWidth))
            drawArc(color = liquidColor.copy(alpha = 0.8f), startAngle = 270f, sweepAngle = -360f * animatedFill,
                useCenter = false, size = Size(radius*2, radius*2), topLeft = Offset(center.x-radius, center.y-radius),
                style = Stroke(width = ringWidth, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%.1f".format(ammoniaValue), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("ppm", fontSize = 14.sp, color = TextSecondary)
        }
    }
}

@Composable
fun AmmoniaSensorDisplay(ammoniaValue: Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AmmoniaRingAnimation(ammoniaValue = ammoniaValue)
        Text("%.1f ppm".format(ammoniaValue), style = MaterialTheme.typography.displayMedium, color = when {
            ammoniaValue > 50 -> RedAccent
            ammoniaValue > 25 -> YellowAccent
            else              -> GreenAccent
        })
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Lux Ring Animation (colours updated)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun LuxRingAnimation(luxValue: Float, modifier: Modifier = Modifier) {
    val animatedFill by animateFloatAsState(
        targetValue = (luxValue / 20000f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 100f),
        label = "luxFill"
    )
    val lightColor by animateColorAsState(
        targetValue = when {
            luxValue > 10000 -> RedAccent
            luxValue > 5000  -> YellowAccent
            else             -> GreenAccent
        },
        animationSpec = tween(300),
        label = "lightColor"
    )
    Box(modifier = modifier.size(220.dp).padding(16.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width/2, size.height/2)
            val radius = size.minDimension * 0.4f
            val ring   = size.minDimension * 0.1f
            drawArc(color = DividerDark, startAngle = 270f, sweepAngle = 360f, useCenter = false,
                size = Size(radius*2, radius*2), topLeft = Offset(center.x-radius, center.y-radius),
                style = Stroke(width = ring))
            drawArc(color = lightColor.copy(alpha = 0.8f), startAngle = 270f, sweepAngle = -360f * animatedFill,
                useCenter = false, size = Size(radius*2, radius*2), topLeft = Offset(center.x-radius, center.y-radius),
                style = Stroke(width = ring, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%.0f".format(luxValue), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("LUX", fontSize = 14.sp, color = TextSecondary)
        }
    }
}

@Composable
fun LuxSensorDisplay(luxValue: Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LuxRingAnimation(luxValue = luxValue)
        Text("%.0f LUX".format(luxValue), style = MaterialTheme.typography.displayMedium, color = when {
            luxValue > 10000 -> RedAccent
            luxValue > 5000  -> YellowAccent
            else             -> GreenAccent
        })
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SEN66 display (colors updated to BleSense palette)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun Sen66SensorDisplay(sensorData: BluetoothScanViewModel.SensorData.Sen66Data, modifier: Modifier = Modifier) {
    val aqi      = sensorData.airQualityIndex.toIntOrNull() ?: 0
    val aqiColor = when {
        aqi <= 50  -> GreenAccent
        aqi <= 100 -> YellowAccent
        aqi <= 150 -> OrangeAccent
        aqi <= 200 -> RedAccent
        else       -> PurpleAccent
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // AQI banner
        Surface(shape = RoundedCornerShape(14.dp), color = aqiColor.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🌿 Air Quality Index", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(aqi.toString(), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = aqiColor)
                Text(when { aqi<=50->"Good"; aqi<=100->"Moderate"; aqi<=150->"Unhealthy for Sensitive"; aqi<=200->"Unhealthy"; else->"Very Unhealthy" }, fontSize = 12.sp, color = TextSecondary)
            }
        }
        // PM grid
        Surface(shape = RoundedCornerShape(14.dp), color = CardDark2, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Particulate Matter", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PMCard("PM1.0",  sensorData.pm1,  "μg/m³", BlueAccent)
                    PMCard("PM2.5",  sensorData.pm25, "μg/m³", GreenAccent)
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PMCard("PM4.0",  sensorData.pm4,  "μg/m³", YellowAccent)
                    PMCard("PM10",   sensorData.pm10, "μg/m³", OrangeAccent)
                }
            }
        }
        // Gas sensors
        Surface(shape = RoundedCornerShape(14.dp), color = CardDark2, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Gas Sensors", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GasCard("Temperature", sensorData.temperature, "°C",   "🌡️", RedAccent)
                    GasCard("Humidity",    sensorData.humidity,    "%",    "💧", BlueAccent)
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GasCard("CO₂", sensorData.co2, "ppm", "🫧", TealAccent)
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GasCard("VOC", sensorData.voc, "", "🧪", PurpleAccent)
                    GasCard("NOx", sensorData.nox, "", "⚠️", YellowAccent)
                }
            }
        }
    }
}

@Composable
fun PMCard(label: String, value: String, unit: String, color: Color) {
    val num = value.toFloatOrNull() ?: 0f
    val valueColor = if (label == "PM2.5") when { num<=12->GreenAccent; num<=35.4f->YellowAccent; num<=55.4f->OrangeAccent; else->RedAccent }
    else color
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth(0.48f)) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(if (value.isNotEmpty()) value else "0", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Text(unit, fontSize = 9.sp, color = TextSecondary)
        }
    }
}

@Composable
fun GasCard(label: String, value: String, unit: String, icon: String, color: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth(0.48f)) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$icon $label", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(if (value.isNotEmpty()) value else "0", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            if (unit.isNotEmpty()) Text(unit, fontSize = 9.sp, color = TextSecondary)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// TempLogger display (unchanged logic, dark theme applied)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun TempLoggerDisplay(viewModel: BluetoothScanViewModel<Any>, deviceAddress: String, deviceId: String, deviceName: String) {
    val uniqueDeviceId = deviceAddress
    LaunchedEffect(Unit) {
        println("🔍 TEMPLOGGER DEBUG: Device=$deviceName Address=$deviceAddress")
    }
    val allPacketsMap       by viewModel.tempLoggerPacketHistory.collectAsState()
    val allLatestPacketsMap by viewModel.latestTempLoggerPacket.collectAsState()

    val deviceSpecificPackets = remember(allPacketsMap, uniqueDeviceId) { allPacketsMap[uniqueDeviceId] ?: emptyList() }
    val latestPacketForThis   = remember(allLatestPacketsMap, uniqueDeviceId) { allLatestPacketsMap[uniqueDeviceId] }

    val largePackets = remember(deviceSpecificPackets) {
        deviceSpecificPackets.filter { p ->
            p.rawData.split(" ").filter { it.isNotBlank() }.count { it.isNotEmpty() && it != " " } >= 224
        }
    }
    val latestLargePacket = remember(latestPacketForThis) {
        latestPacketForThis?.takeIf { p ->
            p.rawData.split(" ").filter { it.isNotBlank() }.count { it.isNotEmpty() && it != " " } >= 224
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Device ${deviceAddress.takeLast(8)} Large Packets (${largePackets.size})", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (latestLargePacket != null) {
                Box(modifier = Modifier.background(GreenMuted, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("LIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GreenAccent)
                }
            }
        }
        Text("ID: $deviceId | ${deviceAddress.takeLast(8)}", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))

        if (largePackets.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text("No large packets (224 bytes) for ${deviceAddress.takeLast(8)}", color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            return
        }

        val tempVals = largePackets.map { it.temperature.toFloatOrNull() ?: 0f }
        val humVals  = largePackets.map { it.humidity.toFloatOrNull()    ?: 0f }
        if (tempVals.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(12.dp), color = CardDark2, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Statistics (${largePackets.size} packets)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("Avg Temp",  "${String.format("%.1f", tempVals.average())}°C", GreenAccent)
                        StatItem("Min Temp",  "${String.format("%.1f", tempVals.minOrNull()?:0f)}°C", BlueAccent)
                        StatItem("Max Temp",  "${String.format("%.1f", tempVals.maxOrNull()?:0f)}°C", RedAccent)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("Avg Hum",   "${String.format("%.1f", humVals.average())}%",  GreenAccent)
                        StatItem("Min Hum",   "${String.format("%.1f", humVals.minOrNull()?:0f)}%",  BlueAccent)
                        StatItem("Max Hum",   "${String.format("%.1f", humVals.maxOrNull()?:0f)}%",  RedAccent)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(largePackets.reversed()) { packet ->
                TempLoggerPacketCard(packet = packet, index = largePackets.indexOf(packet)+1, isLatest = packet == latestLargePacket, deviceName = deviceName)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = TextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun TempLoggerPacketCard(packet: BluetoothScanViewModel.SensorData.TempLoggerData, index: Int, isLatest: Boolean, deviceName: String) {
    var expanded       by remember { mutableStateOf(false) }
    var showByteGroups by remember { mutableStateOf(true) }

    val actualByteCount = remember(packet.rawData) {
        packet.rawData.split(" ").filter { it.isNotBlank() }.count { it.isNotEmpty() && it != " " }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        color    = if (isLatest) CardDark else CardDark2,
        border   = if (isLatest) BorderStroke(1.dp, GreenAccent.copy(alpha = 0.4f)) else null,
        tonalElevation = if (isLatest) 6.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("$deviceName · Packet #$index", color = BlueAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (isLatest) {
                        Box(modifier = Modifier.background(GreenMuted, RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("LATEST", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = GreenAccent)
                        }
                    }
                }
                Text("Device ${packet.deviceId}", color = TextSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                shape    = RoundedCornerShape(10.dp),
                color    = CardDark2
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (expanded) "Hide Raw Data" else "Show Raw Data", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(if (actualByteCount >= 224) "224 bytes (7×32)" else "$actualByteCount bytes", color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                    if (expanded) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !showByteGroups, onClick = { showByteGroups = false }, label = { Text("Raw Hex", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BlueAccent, selectedLabelColor = Color.White))
                            FilterChip(selected = showByteGroups, onClick = { showByteGroups = true }, label = { Text("32-Byte Groups", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GreenAccent, selectedLabelColor = GreenDark))
                        }
                        Spacer(Modifier.height(10.dp))
                        if (!showByteGroups) {
                            Text(packet.rawData, color = PurpleAccent, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
                        } else {
                            val groups = parseTempLoggerRawDataIntoByteGroups(packet.rawData)
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(groups) { idx, group ->
                                    TempLoggerByteGroupItem(groupNumber = idx+1, bytes = group, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// TempLogger byte group item (dark theme applied)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun TempLoggerByteGroupItem(groupNumber: Int, bytes: List<String>, modifier: Modifier = Modifier) {
    val displayBytes    = bytes.take(32)
    val hasValidData    = displayBytes.any { it != "00" && it != "--" }
    val isEmptyGroup    = displayBytes.all { it == "--" }
    val (temperature, humidity) = remember(displayBytes) { extractTempHumidityFromGroup(displayBytes) }

    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp),
        color  = if (hasValidData) CardDark else CardDark2,
        border = BorderStroke(1.dp, if (hasValidData) DividerDark else CardDark2)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (isEmptyGroup) "Group $groupNumber (Empty)" else "Group $groupNumber",
                        color = if (hasValidData) BlueAccent else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (hasValidData && temperature != "--" && humidity != "--") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🌡️ $temperature", color = RedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("💧 $humidity",    color = BlueAccent,  fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (!isEmptyGroup) {
                        Text("Bytes ${(groupNumber-1)*32+1}–${groupNumber*32}", color = TextSecondary, fontSize = 9.sp)
                    }
                }
                val seqNum = displayBytes.getOrNull(31)?.toIntOrNull(16) ?: groupNumber
                if (!isEmptyGroup) {
                    Box(modifier = Modifier.background(
                        if (seqNum == groupNumber) GreenAccent.copy(0.15f) else YellowAccent.copy(0.15f),
                        CircleShape
                    ).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Seq: ${String.format("%02X", seqNum)}",
                            color = if (seqNum == groupNumber) GreenAccent else YellowAccent,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (hasValidData) {
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = Modifier.height(140.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(displayBytes) { index, byte ->
                        val isHeader = index < 4 && byte != "00" && byte != "--"
                        val isSeq    = index == 31
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .background(when { isHeader->BlueAccent.copy(0.1f); isSeq->GreenAccent.copy(0.1f); else->Color.Transparent }, RoundedCornerShape(4.dp))
                                .border(1.dp, when { isHeader->BlueAccent.copy(0.3f); isSeq->GreenAccent.copy(0.3f); else->DividerDark.copy(0.3f) }, RoundedCornerShape(4.dp))
                                .padding(4.dp)
                        ) {
                            Text("B${index+1}", color = when { isHeader->BlueAccent; isSeq->GreenAccent; else->TextSecondary.copy(0.6f) }, fontSize = 7.sp)
                            Text(byte, color = when { byte=="--"->TextSecondary.copy(0.4f); isHeader->BlueAccent; isSeq->GreenAccent; byte=="00"->TextSecondary.copy(0.5f); else->GreenAccent }, fontSize = 11.sp, fontWeight = if (isHeader||isSeq) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    Text("No data in this group", color = TextSecondary, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DataLogger display — full packet history with XYZ rows (original layout)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun DataLoggerDisplay(viewModel: BluetoothScanViewModel<Any>) {
    val packetHistory by viewModel.dataLoggerPacketHistory.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Packets History (${packetHistory.size})",
                color      = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp
            )
            Box(
                modifier = Modifier
                    .background(GreenMuted, RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Live", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenAccent)
            }
        }

        if (packetHistory.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No packets received yet", color = TextSecondary, fontSize = 13.sp)
                }
            }
            return
        }

        // Show only the latest packet (matches original logic)
        val packet = packetHistory.last()
        val accel  = packet.payloadAccel

        Surface(
            shape  = RoundedCornerShape(16.dp),
            color  = CardDark,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Packet ID header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(TealAccent, RoundedCornerShape(2.dp))
                        )
                        Text(
                            "Packet ID: ${packet.lastPacketId}",
                            color      = TealAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 13.sp
                        )
                    }
                    Text(
                        "${accel.size} points",
                        color    = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (accel.isEmpty()) {
                    Text("No accelerometer data", color = TextSecondary, fontSize = 12.sp)
                    return@Column
                }

                // Column headers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardDark2, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("#",    color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.width(36.dp))
                    Text("X",   color = RedAccent.copy(alpha = 0.7f),   fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("Y",   color = GreenAccent.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("Z",   color = BlueAccent.copy(alpha = 0.7f),  fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // XYZ data rows (first 20 points, original logic)
                accel.take(20).forEachIndexed { index, triple ->
                    val x = triple.first.toInt()  and 0xFF
                    val y = triple.second.toInt() and 0xFF
                    val z = triple.third.toInt()  and 0xFF
                    val invalid = x == 255 && y == 255 && z == 255

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "#${index + 1}",
                            color      = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 11.sp,
                            modifier   = Modifier.width(36.dp)
                        )
                        Text(
                            if (invalid) "--" else "$x",
                            color      = if (invalid) TextSecondary else RedAccent,
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 12.sp,
                            fontWeight = if (invalid) FontWeight.Normal else FontWeight.SemiBold
                        )
                        Text(
                            if (invalid) "--" else "$y",
                            color      = if (invalid) TextSecondary else GreenAccent,
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 12.sp,
                            fontWeight = if (invalid) FontWeight.Normal else FontWeight.SemiBold
                        )
                        Text(
                            if (invalid) "--" else "$z",
                            color      = if (invalid) TextSecondary else BlueAccent,
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 12.sp,
                            fontWeight = if (invalid) FontWeight.Normal else FontWeight.SemiBold
                        )
                    }
                    // Subtle divider between rows
                    if (index < minOf(19, accel.size - 1)) {
                        Divider(
                            color     = DividerDark.copy(alpha = 0.5f),
                            thickness = 0.4.dp,
                            modifier  = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                // "More points" indicator
                if (accel.size > 20) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DividerDark, RoundedCornerShape(8.dp))
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "+ ${accel.size - 20} more points",
                            color    = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DataLogger XYZ Card — full 80-point monospace table (original layout)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun DataLoggerXYZCard(packet: BluetoothScanViewModel.SensorData.DataLoggerData) {
    // Safe processing of acceleration data (unsigned + FF handling + limit to 80 points)
    val xyzPoints = remember(packet.payloadAccel) {
        val raw = packet.payloadAccel

        if (raw.isEmpty()) {
            List(80) { Triple("--", "--", "--") }
        } else {
            val fixed = if (raw.size >= 80) {
                raw.take(80)
            } else {
                val filled = raw.toMutableList()
                val last = raw.last()
                repeat(80 - raw.size) { filled.add(last) }
                filled
            }

            fixed.map { (xRaw, yRaw, zRaw) ->
                val x = xRaw and 0xFF
                val y = yRaw and 0xFF
                val z = zRaw and 0xFF

                if (x == 255 && y == 255 && z == 255) {
                    Triple("--", "--", "--")  // Invalid data marker
                } else {
                    Triple(x.toString(), y.toString(), z.toString())
                }
            }
        }
    }

    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = CardDark,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(TealAccent, RoundedCornerShape(2.dp))
                    )
                    Text(
                        "Packet ID: ${packet.lastPacketId}",
                        color      = TealAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp
                    )
                }
                Text(
                    "${xyzPoints.size} pts",
                    color    = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Column headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardDark2, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("#",  color = TextSecondary,              fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                Text("X",  color = RedAccent.copy(0.7f),       fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(60.dp))
                Text("Y",  color = GreenAccent.copy(0.7f),     fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(60.dp))
                Text("Z",  color = BlueAccent.copy(0.7f),      fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(60.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // All 80 XYZ points (original logic, new colours)
            xyzPoints.forEachIndexed { i, (x, y, z) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "#${i + 1}",
                        color      = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 11.sp,
                        modifier   = Modifier.width(40.dp)
                    )
                    Text(
                        x.padStart(4, ' '),
                        color      = if (x == "--") TextSecondary else RedAccent,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp,
                        fontWeight = if (x == "--") FontWeight.Normal else FontWeight.SemiBold,
                        modifier   = Modifier.width(60.dp)
                    )
                    Text(
                        y.padStart(4, ' '),
                        color      = if (y == "--") TextSecondary else GreenAccent,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp,
                        fontWeight = if (y == "--") FontWeight.Normal else FontWeight.SemiBold,
                        modifier   = Modifier.width(60.dp)
                    )
                    Text(
                        z.padStart(4, ' '),
                        color      = if (z == "--") TextSecondary else BlueAccent,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp,
                        fontWeight = if (z == "--") FontWeight.Normal else FontWeight.SemiBold,
                        modifier   = Modifier.width(60.dp)
                    )
                }
                if (i < xyzPoints.size - 1) {
                    Divider(
                        color     = DividerDark.copy(alpha = 0.4f),
                        thickness = 0.4.dp,
                        modifier  = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Helpers (logic identical to original)
// ══════════════════════════════════════════════════════════════════════════════

private fun dismissAlarm(
    showAlertDialog: () -> Unit,
    isAlarmActive: () -> Unit,
    isThresholdSet: () -> Unit,
    mediaPlayer: MediaPlayer?,
    context: Context
) {
    showAlertDialog(); isAlarmActive(); isThresholdSet()
    try { mediaPlayer?.stop(); mediaPlayer?.prepare() }
    catch (_: IllegalStateException) { mediaPlayer?.reset() }
}

private fun parseTempLoggerRawDataIntoByteGroups(rawData: String?): List<List<String>> {
    if (rawData.isNullOrBlank()) return createEmptyGroupsWithDashes()
    try {
        val bytes  = rawData.split(" ").filter { it.isNotBlank() }.map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<List<String>>()
        for (chunk in bytes.chunked(32)) {
            if (chunk.all { it.equals("FF", ignoreCase = true) }) continue
            val hasReal = chunk.any { it != "00" && !it.equals("FF", ignoreCase = true) && it.isNotEmpty() }
            if (hasReal) {
                val padded = chunk.toMutableList().apply { while (size < 32) add("00") }
                result.add(padded.take(32))
            } else {
                result.add(List(32) { i -> if (i < chunk.size) { val b = chunk[i]; if (b.equals("FF", ignoreCase = true)) "--" else b } else "--" })
            }
            if (result.size >= 7) break
        }
        if (result.isEmpty()) return createEmptyGroupsWithDashes()
        while (result.size < 7) result.add(List(32) { "--" })
        return result.take(7)
    } catch (_: Exception) { return createEmptyGroupsWithDashes() }
}

private fun createEmptyGroupsWithDashes(): List<List<String>> = List(7) { List(32) { "--" } }

private fun extractTempHumidityFromGroup(bytes: List<String>): Pair<String, String> {
    if (bytes.size < 4 || bytes.any { it == "--" }) return "--" to "--"
    return try {
        val b1 = bytes[0].toIntOrNull(16) ?: 0; val b2 = bytes[1].toIntOrNull(16) ?: 0
        val b3 = bytes[2].toIntOrNull(16) ?: 0; val b4 = bytes[3].toIntOrNull(16) ?: 0
        val b2f = if (b2 == 0 && bytes[1] != "00") bytes[1].toIntOrNull(16) ?: 0 else b2
        val b4f = if (b4 == 0 && bytes[3] != "00") bytes[3].toIntOrNull(16) ?: 0 else b4
        "${String.format("%.2f", b1 + b2f / 100.0)}°C" to "${String.format("%.2f", b3 + b4f / 100.0)}%"
    } catch (_: Exception) { "--" to "--" }
}

private fun exportDataToCSV(context: Context, uri: Uri, viewModel: BluetoothScanViewModel<Any>, deviceAddress: String, deviceName: String, deviceId: String, onComplete: () -> Unit) {
    MainScope().launch {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    var history = viewModel.getHistoricalDataForDevice(deviceAddress).toMutableList()
                    if (history.isEmpty()) {
                        viewModel.devices.value.find { it.address == deviceAddress }?.sensorData?.let {
                            history.add(BluetoothScanViewModel.HistoricalDataEntry(System.currentTimeMillis(), it))
                        }
                    }
                    if (history.isEmpty()) return@use
                    val header = StringBuilder("Timestamp,Device Name,Device Address,Node ID,")
                    when (history.first().sensorData) {
                        is BluetoothScanViewModel.SensorData.SHT40Data         -> header.append("Temperature (°C),Humidity (%)")
                        is BluetoothScanViewModel.SensorData.LIS2DHData        -> header.append("X-Axis (m/s²),Y-Axis (m/s²),Z-Axis (m/s²)")
                        is BluetoothScanViewModel.SensorData.SoilSensorData    -> header.append("Nitrogen,Phosphorus,Potassium,Moisture (%),Temperature (°C),EC (mS/cm),pH,Salinity (mg/L)")
                        is BluetoothScanViewModel.SensorData.LuxSensorData     -> header.append("Light Intensity (LUX)")
                        is BluetoothScanViewModel.SensorData.SDTData           -> header.append("Speed (m/s),Distance (m)")
                        is BluetoothScanViewModel.SensorData.AmmoniaSensorData -> header.append("Ammonia (ppm)")
                        is BluetoothScanViewModel.SensorData.Sen66Data         -> header.append("PM1.0,PM2.5,PM4.0,PM10,Temperature,Humidity,CO₂,VOC,NOx,Air Quality")
                        is BluetoothScanViewModel.SensorData.DataLoggerData    -> header.append("Total Stored Packets,First Packet ID,Accel Points,Timestamp,Raw Data")
                        else -> {}
                    }
                    header.append("\n"); os.write(header.toString().toByteArray())
                    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    history.forEachIndexed { i, entry ->
                        val row = StringBuilder("${df.format(Date(entry.timestamp))},$deviceName,$deviceAddress,$deviceId,")
                        when (val sd = entry.sensorData) {
                            is BluetoothScanViewModel.SensorData.SHT40Data         -> row.append("${sd.temperature},${sd.humidity}")
                            is BluetoothScanViewModel.SensorData.LIS2DHData        -> row.append("${sd.x},${sd.y},${sd.z}")
                            is BluetoothScanViewModel.SensorData.SoilSensorData    -> row.append("${sd.nitrogen},${sd.phosphorus},${sd.potassium},${sd.moisture},${sd.temperature},${sd.ec},${sd.pH},${sd.salinity}")
                            is BluetoothScanViewModel.SensorData.LuxSensorData     -> row.append("${sd.lux}")
                            is BluetoothScanViewModel.SensorData.SDTData           -> row.append("${sd.speed},${sd.distance}")
                            is BluetoothScanViewModel.SensorData.AmmoniaSensorData -> row.append("${sd.ammonia}")
                            is BluetoothScanViewModel.SensorData.Sen66Data         -> row.append("${sd.pm1},${sd.pm25},${sd.pm4},${sd.pm10},${sd.temperature},${sd.humidity},${sd.co2},${sd.voc},${sd.nox},${sd.airQualityIndex}")
                            is BluetoothScanViewModel.SensorData.DataLoggerData    -> row.append("${sd.currentPacketId},${sd.lastPacketId},${sd.payloadAccel.size},${df.format(Date(sd.timestamp))},\"${sd.rawData.replace("\"", "\"\"")}\"")
                            else -> {}
                        }
                        row.append("\n"); os.write(row.toString().toByteArray())
                        if (i % 100 == 0) os.flush()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { withContext(Dispatchers.Main) { onComplete() } }
        }
    }
}