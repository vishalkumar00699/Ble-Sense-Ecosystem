package com.blesense.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────
// Protocol constants
// ─────────────────────────────────────────────────────────────
// Manufacturer data byte sent for each mode (single-byte payload)
const val SECRET_KEY_SOLID     = 0xAA.toByte()   // Solid colors  → data = 0xAA
const val SECRET_KEY_ANIMATION = 0xBB.toByte()   // Animations    → data = 0xBB
const val SECRET_KEY_SLIDER    = 0xCC.toByte()   // Slider        → data = 0xCC + value byte

// ─────────────────────────────────────────────────────────────
// LED Commands
// companyId is used as the BLE Manufacturer Company ID:
//   Solid     → 0x0001 … 0x0009  (data always 0xAA)
//   Animation → 0x0001 … 0x0005  (data always 0xBB)
//   Slider    → 0x0001 fixed      (data = 0xCC + sliderByte)
// ─────────────────────────────────────────────────────────────
enum class LEDCommand(
    val id: Byte,
    val displayName: String,
    val color: Color,
    val description: String,
    val mode: String,
    val companyId: Int          // BLE Manufacturer Company ID for this command
) {
    // ── Solid Colors (Company ID 0x0001 – 0x0009, data = 0xAA) ──
    SOLID_MAGENTA      (1, "Magenta",     Color(0xFFFF00FF), "Solid Magenta Color",          "Solid",     0x0001),
    SOLID_YELLOW       (2, "Yellow",      Color(0xFFFFFF00), "Solid Yellow Color",            "Solid",     0x0002),
    SOLID_WHITE        (3, "White",       Color(0xFFFFFFFF), "Solid White Color",             "Solid",     0x0003),
    SOLID_RED          (4, "Red",         Color(0xFFFF0000), "Solid Red Color",               "Solid",     0x0004),
    SOLID_GREEN        (5, "Green",       Color(0xFF00FF00), "Solid Green Color",             "Solid",     0x0005),
    SOLID_BLUE         (6, "Blue",        Color(0xFF0000FF), "Solid Blue Color",              "Solid",     0x0006),
    SOLID_CYAN         (7, "Cyan",        Color(0xFF00FFFF), "Solid Cyan Color",              "Solid",     0x0007),
    INDIAN_FLAG        (8, "Indian Flag", Color(0xFFFF9933), "Saffron-White-Green Flag",      "Solid",     0x0008),
    SOLID_ORANGE       (9, "Orange",      Color(0xFFFF6600), "Solid Orange Color",            "Solid",     0x0009),

    // ── Animations (Company ID 0x0001 – 0x0005, data = 0xBB) ──
    ANIM_CYLON         (1, "Cylon Scanner",   Color(0xFFFF0000), "Red scanning back and forth",  "Animation", 0x0001),
    ANIM_POLICE        (2, "Police Strobe",   Color(0xFF0000FF), "Alternating red/blue flash",   "Animation", 0x0002),
    ANIM_BREATHER      (3, "Zen Breather",    Color(0xFF800080), "Pulsing purple breath",         "Animation", 0x0003),
    ANIM_RAINBOW       (4, "Spinning Rainbow",Color(0xFFFF69B4), "Rainbow colors cycling",        "Animation", 0x0004),
    ANIM_SHOOTING_STAR (5, "Shooting Star",   Color(0xFF87CEEB), "White comet with blue trail",   "Animation", 0x0005),
}

// ─────────────────────────────────────────────────────────────
// Remote types
// ─────────────────────────────────────────────────────────────
enum class RemoteType(
    val displayName: String,
    val color: Color,
    val description: String
) {
    REMOTE_1("LED Remote - Colors",     Color(0xFFE91E63), "Controls solid colors   • Company ID varies 0x0001–0x0009 • Data = 0xAA"),
    REMOTE_2("LED Remote - Animations", Color(0xFFFFEB3B), "Controls LED animations • Company ID varies 0x0001–0x0005 • Data = 0xBB"),
    REMOTE_3("LED Remote - Slider",     Color(0xFF9C27B0), "Slider control          • Company ID = 0x0001 fixed       • Data = 0xCC + value")
}

// ─────────────────────────────────────────────────────────────
// History entry
// ─────────────────────────────────────────────────────────────
data class AdvertisingHistory(
    val label: String,
    val timestamp: Long,
    val remoteType: RemoteType,
    val companyId: Int,
    val dataHex: String
)

// ─────────────────────────────────────────────────────────────
// Main screen
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvertisingScreen(navController: NavHostController) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    
    val bgColor = if (isDarkMode) Color(0xFF0A0A0F) else Color.White
    val surfaceColor = if (isDarkMode) Color(0xFF1C1C24) else Color(0xFFF5F5F5)
    val cardColor = if (isDarkMode) Color(0xFF1C1C24) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color(0xFF9F9FA9) else Color.Gray


    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var isAdvertising       by remember { mutableStateOf(false) }
    var hasPermissions      by remember { mutableStateOf(checkAdvertisingPermissions(context)) }
    var selectedRemote      by remember { mutableStateOf(RemoteType.REMOTE_1) }
    var selectedCommand     by remember { mutableStateOf(LEDCommand.SOLID_RED) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var advertisingTime     by remember { mutableStateOf(0) }
    var sliderValue         by remember { mutableStateOf(128) }

    val advertisingHistory = remember { mutableStateListOf<AdvertisingHistory>() }

    val bluetoothAdvertiser = remember {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bm.adapter?.bluetoothLeAdvertiser
    }
    var currentCallback by remember { mutableStateOf<AdvertiseCallback?>(null) }

    val minutes    = advertisingTime / 60
    val seconds    = advertisingTime % 60
    val timeString = String.format("%02d:%02d", minutes, seconds)

    val availableCommands = remember(selectedRemote) {
        when (selectedRemote) {
            RemoteType.REMOTE_1 -> LEDCommand.values().filter { it.mode == "Solid" }
            RemoteType.REMOTE_2 -> LEDCommand.values().filter { it.mode == "Animation" }
            RemoteType.REMOTE_3 -> emptyList() // slider only – no command grid
        }
    }

    LaunchedEffect(selectedRemote) {
        if (availableCommands.isNotEmpty()) selectedCommand = availableCommands.first()
    }

    // ── Scaffold ──────────────────────────────────────────────
    Scaffold(
        modifier = Modifier.systemBarsPadding(),
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = { Text("LED Remote Control", color = textColor) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
                    }
                },
                actions = {
                    IconButton(onClick = { advertisingHistory.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear History", tint = textColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDarkMode) Color(0xFF1C1C24) else selectedRemote.color.copy(alpha = 0.2f)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Protocol info card ─────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkMode) Color(0xFF1B2E1D) else Color(0xFFE8F5E9)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📡 LED Control Protocol",
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, 
                            color = if (isDarkMode) Color(0xFF81C784) else Color(0xFF2E7D32))
                        Spacer(Modifier.height(8.dp))
                        Text("Solid     → Company ID 0x0001–0x0009 | Data = 0xAA",
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace, 
                            color = if (isDarkMode) Color(0xFF81C784) else Color(0xFF2E7D32))
                        Text("Animation → Company ID 0x0001–0x0005 | Data = 0xBB",
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace, 
                            color = if (isDarkMode) Color(0xFF81C784) else Color(0xFF2E7D32))
                        Text("Slider    → Company ID 0x0001 fixed  | Data = 0xCC + 0x00–0xFF",
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace, 
                            color = if (isDarkMode) Color(0xFF81C784) else Color(0xFF2E7D32))
                    }
                }
            }

            // ── Remote selection ───────────────────────────────
            item {
                Text("Select Remote Type",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.fillMaxWidth())
            }

            item {
                RemoteSelectionCard(RemoteType.REMOTE_1,
                    isSelected = selectedRemote == RemoteType.REMOTE_1,
                    onSelect   = { if (!isAdvertising) selectedRemote = RemoteType.REMOTE_1 },
                    isEnabled  = !isAdvertising)
            }
            item {
                RemoteSelectionCard(RemoteType.REMOTE_2,
                    isSelected = selectedRemote == RemoteType.REMOTE_2,
                    onSelect   = { if (!isAdvertising) selectedRemote = RemoteType.REMOTE_2 },
                    isEnabled  = !isAdvertising)
            }
            item {
                RemoteSelectionCard(RemoteType.REMOTE_3,
                    isSelected = selectedRemote == RemoteType.REMOTE_3,
                    onSelect   = { if (!isAdvertising) selectedRemote = RemoteType.REMOTE_3 },
                    isEnabled  = !isAdvertising)
            }

            // ── Command grid (Solid / Animation) ──────────────
            if (availableCommands.isNotEmpty()) {
                item {
                    Text(
                        text = when (selectedRemote) {
                            RemoteType.REMOTE_1 -> "Select Solid Color Command"
                            RemoteType.REMOTE_2 -> "Select Animation Command"
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                val chunks = availableCommands.chunked(2)
                items(chunks.size) { idx ->
                    val pair = chunks[idx]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { cmd ->
                            CommandCard(
                                modifier  = Modifier.weight(1f),
                                command   = cmd,
                                isSelected = selectedCommand == cmd,
                                onSelect  = { selectedCommand = cmd },
                                isEnabled = !isAdvertising
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // ── Slider (Remote 3 only) ─────────────────────────
            if (selectedRemote == RemoteType.REMOTE_3) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(16.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = selectedRemote.color.copy(alpha = 0.1f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("🎚️ Slider Control (0x00 – 0xFF)",
                                fontWeight = FontWeight.Bold, color = textColor)
                            Spacer(Modifier.height(4.dp))
                            Text("Company ID: 0x0001 (fixed)",
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                color = selectedRemote.color)
                            Text("Data: 0xCC + 0x${sliderValue.toString(16).padStart(2,'0').uppercase()}",
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                color = selectedRemote.color)
                            Spacer(Modifier.height(8.dp))
                            Slider(
                                value         = sliderValue.toFloat(),
                                onValueChange = { sliderValue = it.toInt() },
                                valueRange    = 0f..255f,
                                steps         = 254,
                                colors        = SliderDefaults.colors(
                                    thumbColor       = selectedRemote.color,
                                    activeTrackColor = selectedRemote.color)
                            )
                            Text(
                                text = "Value: $sliderValue  →  0xCC${sliderValue.toString(16).padStart(2,'0').uppercase()}",
                                fontFamily = FontFamily.Monospace,
                                color = textColor,
                                modifier   = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (!hasPermissions) { showPermissionDialog = true; return@Button }
                                    if (isAdvertising) {
                                        currentCallback?.let { stopAdvertising(bluetoothAdvertiser, it) }
                                        isAdvertising   = false
                                        advertisingTime = 0
                                        currentCallback = null
                                    } else {
                                        val cb = createAdvertiseCallback(
                                            onSuccess = {
                                                isAdvertising = true
                                                scope.launch {
                                                    while (isAdvertising) { delay(1000); advertisingTime++ }
                                                }
                                                addToHistory(
                                                    history    = advertisingHistory,
                                                    label      = "Slider: $sliderValue",
                                                    remoteType = selectedRemote,
                                                    companyId  = 0x0001,
                                                    dataHex    = "CC ${sliderValue.toString(16).padStart(2,'0').uppercase()}"
                                                )
                                            },
                                            onFailure = { isAdvertising = false; advertisingTime = 0 }
                                        )
                                        currentCallback = cb
                                        sendSliderCommand(
                                            context      = context,
                                            advertiser   = bluetoothAdvertiser,
                                            callback     = cb,
                                            sliderValue  = sliderValue
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = if (isAdvertising) Color(0xFFEF5350)
                                    else selectedRemote.color)
                            ) {
                                Text(
                                    if (isAdvertising) "STOP ADVERTISING" else "SEND SLIDER VALUE",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ── Status card ────────────────────────────────────
            item {
                AdvertisingStatusCard(
                    isAdvertising   = isAdvertising,
                    advertisingTime = timeString,
                    selectedRemote  = selectedRemote,
                    selectedCommand = if (selectedRemote != RemoteType.REMOTE_3) selectedCommand else null,
                    sliderValue     = if (selectedRemote == RemoteType.REMOTE_3) sliderValue else null
                )
            }

            // ── Main send button (Solid / Animation) ──────────
            if (selectedRemote != RemoteType.REMOTE_3) {
                item {
                    Button(
                        onClick = {
                            if (!hasPermissions) { showPermissionDialog = true; return@Button }

                            if (isAdvertising) {
                                currentCallback?.let { stopAdvertising(bluetoothAdvertiser, it) }
                                isAdvertising   = false
                                advertisingTime = 0
                                currentCallback = null
                            } else {
                                val cb = createAdvertiseCallback(
                                    onSuccess = {
                                        isAdvertising = true
                                        scope.launch {
                                            while (isAdvertising) { delay(1000); advertisingTime++ }
                                        }
                                        val secretKey = if (selectedRemote == RemoteType.REMOTE_1)
                                            SECRET_KEY_SOLID else SECRET_KEY_ANIMATION
                                        addToHistory(
                                            history    = advertisingHistory,
                                            label      = selectedCommand.displayName,
                                            remoteType = selectedRemote,
                                            companyId  = selectedCommand.companyId,
                                            dataHex    = secretKey.toInt().and(0xFF)
                                                .toString(16).padStart(2,'0').uppercase()
                                        )
                                    },
                                    onFailure = { isAdvertising = false; advertisingTime = 0 }
                                )
                                currentCallback = cb
                                startAdvertising(
                                    context    = context,
                                    advertiser = bluetoothAdvertiser,
                                    callback   = cb,
                                    remote     = selectedRemote,
                                    command    = selectedCommand
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape  = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAdvertising) Color(0xFFEF5350)
                            else selectedRemote.color)
                    ) {
                        Text(
                            text = if (isAdvertising) "STOP ADVERTISING" else "SEND COMMAND",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── History ────────────────────────────────────────
            if (advertisingHistory.isNotEmpty()) {
                item { HistoryCard(history = advertisingHistory.toList()) }
            }

            // ── Permissions warning ────────────────────────────
            if (!hasPermissions) {
                item { PermissionsWarningCard() }
            }
        }
    }

    if (showPermissionDialog) {
        PermissionDialog(
            onDismiss = { showPermissionDialog = false },
            onConfirm = {
                showPermissionDialog = false
                if (context is androidx.activity.ComponentActivity) {
                    ActivityCompat.requestPermissions(
                        context, getAdvertisingPermissions(), 1001)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Composable helpers
// ─────────────────────────────────────────────────────────────

@Composable
fun CommandCard(
    modifier: Modifier = Modifier,
    command: LEDCommand,
    isSelected: Boolean,
    onSelect: (LEDCommand) -> Unit,
    isEnabled: Boolean
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val textColor = if (isDarkMode) Color.White else Color.Black

    Card(
        modifier = modifier.clickable(enabled = isEnabled) { onSelect(command) },
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (isSelected) command.color.copy(alpha = 0.2f)
            else if (isDarkMode) Color(0xFF23232B) else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(command.color)
            )
            Spacer(Modifier.height(8.dp))
            Text(command.displayName,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp, maxLines = 2, color = textColor)
            Text(
                text = "ID: 0x${command.companyId.toString(16).padStart(4,'0').uppercase()}",
                fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = command.color)
            if (isSelected)
                Icon(Icons.Default.Check, null,
                    modifier = Modifier.size(16.dp), tint = command.color)
        }
    }
}

@Composable
fun AdvertisingStatusCard(
    isAdvertising: Boolean,
    advertisingTime: String,
    selectedRemote: RemoteType,
    selectedCommand: LEDCommand?,
    sliderValue: Int?
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val secondaryTextColor = if (isDarkMode) Color(0xFF9F9FA9) else Color.Gray

    // Compute what will actually be sent
    val companyIdHex = when {
        sliderValue != null -> "0x0001"
        selectedCommand != null ->
            "0x${selectedCommand.companyId.toString(16).padStart(4,'0').uppercase()}"
        else -> "—"
    }
    val dataHex = when {
        sliderValue != null ->
            "CC ${sliderValue.toString(16).padStart(2,'0').uppercase()}"
        selectedRemote == RemoteType.REMOTE_1 -> "AA"
        selectedRemote == RemoteType.REMOTE_2 -> "BB"
        else -> "—"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (isAdvertising) selectedRemote.color.copy(alpha = 0.15f)
            else if (isDarkMode) Color(0xFF1C1C24) else Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (isAdvertising) selectedRemote.color else Color.Gray.copy(0.3f)),
                contentAlignment = Alignment.Center
            ) {
                if (isAdvertising)
                    CircularProgressIndicator(color = Color.White,
                        modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
                else
                    Icon(painterResource(id = R.drawable.bluetooth), null,
                        modifier = Modifier.size(48.dp), tint = Color.Gray)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (isAdvertising)
                    "SENDING: ${selectedCommand?.displayName ?: "Slider $sliderValue"}"
                else "READY",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isAdvertising) selectedRemote.color else secondaryTextColor
            )

            if (isAdvertising) {
                Text(advertisingTime,
                    fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, color = selectedRemote.color,
                    modifier = Modifier.padding(top = 8.dp))

                Spacer(Modifier.height(12.dp))

                // Company ID row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Company ID:", fontSize = 13.sp, color = secondaryTextColor)
                    Text(companyIdHex,
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, color = selectedRemote.color)
                }
                // Data row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Manufacturer Data:", fontSize = 13.sp, color = secondaryTextColor)
                    Text("0x$dataHex",
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, color = selectedRemote.color)
                }
            }
        }
    }
}

@Composable
fun HistoryCard(history: List<AdvertisingHistory>) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val textColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color(0xFF9F9FA9) else Color.Gray

    Card(
        Modifier.fillMaxWidth(), 
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF1C1C24) else Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("📋 Command History", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
            Spacer(Modifier.height(12.dp))
            history.take(5).forEachIndexed { i, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(entry.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textColor)
                        Text(
                            text = "${SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                .format(Date(entry.timestamp))}  " +
                                    "CompID: 0x${entry.companyId.toString(16).padStart(4,'0').uppercase()}  " +
                                    "Data: 0x${entry.dataHex}",
                            fontSize = 10.sp, color = secondaryTextColor, fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(entry.remoteType.color)
                    )
                }
                if (i < history.take(5).lastIndex)
                    Divider(Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
fun RemoteSelectionCard(
    remote: RemoteType,
    isSelected: Boolean,
    onSelect: () -> Unit,
    isEnabled: Boolean
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val textColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color(0xFF9F9FA9) else Color.Gray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled) { onSelect() },
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isSelected) remote.color.copy(alpha = 0.2f)
            else if (isDarkMode) Color(0xFF23232B) else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(if (isSelected) 8.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(remote.color))
                    Spacer(Modifier.size(12.dp))
                    Text(remote.displayName,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 16.sp, color = textColor)
                }
                Text(remote.description,
                    fontSize = 12.sp, color = secondaryTextColor,
                    modifier = Modifier.padding(start = 36.dp, top = 4.dp))
            }
            if (isSelected)
                Icon(Icons.Default.Check, "Selected", tint = remote.color)
        }
    }
}

@Composable
fun PermissionDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Permissions Required") },
        text    = { Text("Bluetooth permissions are required to control your LED device.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PermissionsWarningCard() {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val bgColor = if (isDarkMode) Color(0xFF2D1616) else Color(0xFFFFEBEE)
    val contentColor = if (isDarkMode) Color(0xFFFF8A80) else Color(0xFFB71C1C)

    Card(Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = contentColor)
            Spacer(Modifier.size(8.dp))
            Text("Bluetooth permissions required",
                color = contentColor, fontWeight = FontWeight.Medium)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// BLE advertising logic
// ─────────────────────────────────────────────────────────────

/**
 * Solid / Animation advertising
 *
 * Solid:     companyId = command.companyId (0x0001–0x0009)   data = [0xAA]
 * Animation: companyId = command.companyId (0x0001–0x0005)   data = [0xBB]
 */
private fun startAdvertising(
    context: Context,
    advertiser: BluetoothLeAdvertiser?,
    callback: AdvertiseCallback,
    remote: RemoteType,
    command: LEDCommand
) {
    try {
        if (advertiser == null) return

        val companyId = command.companyId
        val data      = if (remote == RemoteType.REMOTE_1)
            byteArrayOf(SECRET_KEY_SOLID)       // 0xAA
        else
            byteArrayOf(SECRET_KEY_ANIMATION)   // 0xBB

        advertiser.startAdvertising(buildSettings(), buildAdvData(companyId, data), callback)

    } catch (_: SecurityException) { }
    catch (_: Exception) { }
}

/**
 * Slider advertising
 *
 * companyId  = 0x0001 (fixed)
 * data       = [0xCC, sliderByte]   e.g. value=0x11 → data = CC 11
 */
private fun sendSliderCommand(
    context: Context,
    advertiser: BluetoothLeAdvertiser?,
    callback: AdvertiseCallback,
    sliderValue: Int
) {
    try {
        if (advertiser == null) return

        val companyId = 0x0001
        val data      = byteArrayOf(
            SECRET_KEY_SLIDER,         // 0xCC
            sliderValue.toByte()       // 0x00 – 0xFF
        )

        advertiser.startAdvertising(buildSettings(), buildAdvData(companyId, data), callback)

    } catch (_: SecurityException) { }
    catch (_: Exception) { }
}

private fun stopAdvertising(advertiser: BluetoothLeAdvertiser?, callback: AdvertiseCallback) {
    try { advertiser?.stopAdvertising(callback) }
    catch (_: SecurityException) { }
}

private fun buildSettings(): AdvertiseSettings =
    AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
        .setConnectable(false)
        .setTimeout(0)
        .build()

private fun buildAdvData(companyId: Int, data: ByteArray): AdvertiseData =
    AdvertiseData.Builder()
        .setIncludeDeviceName(false)
        .setIncludeTxPowerLevel(false)
        .addManufacturerData(companyId, data)
        .build()

private fun createAdvertiseCallback(
    onSuccess: () -> Unit,
    onFailure: () -> Unit
): AdvertiseCallback = object : AdvertiseCallback() {
    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) { super.onStartSuccess(settingsInEffect); onSuccess() }
    override fun onStartFailure(errorCode: Int)                      { super.onStartFailure(errorCode);        onFailure() }
}

// ─────────────────────────────────────────────────────────────
// Permission helpers
// ─────────────────────────────────────────────────────────────

private fun checkAdvertisingPermissions(context: Context): Boolean =
    getAdvertisingPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun getAdvertisingPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
    else
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION)

// ─────────────────────────────────────────────────────────────
// Misc helpers
// ─────────────────────────────────────────────────────────────

private fun addToHistory(
    history: MutableList<AdvertisingHistory>,
    label: String,
    remoteType: RemoteType,
    companyId: Int,
    dataHex: String
) {
    history.add(0, AdvertisingHistory(
        label      = label,
        timestamp  = System.currentTimeMillis(),
        remoteType = remoteType,
        companyId  = companyId,
        dataHex    = dataHex
    ))
    if (history.size > 10) history.removeAt(history.lastIndex)
}

private fun bytesToHex(bytes: ByteArray): String =
    bytes.joinToString(" ") { "%02X".format(it) }