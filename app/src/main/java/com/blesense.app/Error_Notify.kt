package com.blesense.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "BLeSense"

// ─────────────────────────────────────────────────────────────────────────────
// TARGET DEVICE
// ─────────────────────────────────────────────────────────────────────────────

private const val TARGET_MAC  = "DE:AD:BE:AF:BA:58"
private const val DEVICE_NAME = "AWS_BLE"

// ─────────────────────────────────────────────────────────────────────────────
// ERROR SLOT MAP — firmware ke fixed byte positions → error code
// ─────────────────────────────────────────────────────────────────────────────

private val ERROR_SLOT_MAP = listOf(
    11 to 7,   // WS_ERR_DUPLICATE_RECORD
    12 to 8,   // WS_ERR_STATION_ID_MISMATCH
    13 to 9,   // WS_ERR_REQUIRED_FIELD_MISSING
    14 to 10,  // WS_ERR_ALL_REQUIRED_VALUE_MISSING
    15 to 11,  // WS_ERR_MAX_TEMP_NOT_FLOAT
    16 to 12,  // WS_ERR_MAX_TEMP_OUT_OF_RANGE
    17 to 21,  // WS_ERR_MIN_TEMP_NOT_FLOAT
    18 to 22,  // WS_ERR_MIN_TEMP_OUT_OF_RANGE
    19 to 31,  // WS_ERR_NOW_TEMP_NOT_FLOAT
    20 to 32,  // WS_ERR_NOW_TEMP_OUT_OF_RANGE
    21 to 41,  // WS_ERR_MAX_RH_NOT_FLOAT
    22 to 42,  // WS_ERR_MAX_RH_OUT_OF_RANGE
    23 to 51,  // WS_ERR_MIN_RH_NOT_FLOAT
    24 to 52,  // WS_ERR_MIN_RH_OUT_OF_RANGE
    25 to 61,  // WS_ERR_NOW_RH_NOT_FLOAT
    26 to 62,  // WS_ERR_NOW_RH_OUT_OF_RANGE
    27 to 71,  // WS_ERR_RAIN_NOT_FLOAT
    28 to 72,  // WS_ERR_RAIN_OUT_OF_RANGE
    29 to 81,  // WS_ERR_WIND_SPEED_NOT_FLOAT
    30 to 82,  // WS_ERR_WIND_SPEED_OUT_OF_RANGE
    31 to 91,  // WS_ERR_WIND_DIR_NOT_FLOAT
    32 to 92,  // WS_ERR_WIND_DIR_OUT_OF_RANGE
)

// ─────────────────────────────────────────────────────────────────────────────
// DATA CLASSES
// ─────────────────────────────────────────────────────────────────────────────

data class SensorData(
    val deviceId: Int = 0,
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val windSpeed: Float = 0f,
    val windDirection: Int = 0,
    val rainCumulative: Float = 0f,
    val errorCodes: List<Int> = emptyList(),

    val allErrorSlots: List<Pair<Int, Boolean>> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val sourceMac: String = "",
    val detectedMfgId: Int = -1,
    val rawHex: String = "",
    val mfgHex: String = "",
    val rawSize: Int = 0
)

data class ErrorInfo(val code: Int, val meaning: String, val severity: Severity)
data class ErrorDisplayItem(val info: ErrorInfo, val isActive: Boolean)
enum class Severity { OK, WARNING, ERROR }
data class LogEntry(val time: String, val tag: String, val message: String)

// ─────────────────────────────────────────────────────────────────────────────
// LOGGER
// ─────────────────────────────────────────────────────────────────────────────

object BleLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private const val MAX = 300

    fun i(msg: String) = add("INFO", msg).also { Log.i(TAG, msg) }
    fun w(msg: String) = add("WARN", msg).also { Log.w(TAG, msg) }
    fun e(msg: String) = add("ERR ", msg).also { Log.e(TAG, msg) }
    fun d(msg: String) = add("DATA", msg).also { Log.d(TAG, msg) }
    fun b(msg: String) = add("BLE ", msg).also { Log.d(TAG, msg) }
    fun clear() { _logs.value = emptyList() }

    private fun add(tag: String, msg: String) {
        val entry = LogEntry(fmt.format(Date()), tag, msg)
        val current = _logs.value.toMutableList()
        current.add(entry)
        if (current.size > MAX) current.removeAt(0)
        _logs.value = current
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ERROR MAP
// ─────────────────────────────────────────────────────────────────────────────

val ERROR_MAP = mapOf(
    1  to ErrorInfo(1,  "NO error",                                          Severity.OK),
    7  to ErrorInfo(7,  "Duplicate Record",                                  Severity.ERROR),
    8  to ErrorInfo(8,  "Station_id Mismatch",                               Severity.ERROR),
    9  to ErrorInfo(9,  "Required Field Missing",                            Severity.ERROR),
    10 to ErrorInfo(10, "All Required value is Missing",                     Severity.ERROR),
    11 to ErrorInfo(11, "Maximum_Temperature is not float",                  Severity.ERROR),
    12 to ErrorInfo(12, "Maximum_Temperature not in plausible range",        Severity.ERROR),
    21 to ErrorInfo(21, "Minimum_Temperature is not float",                  Severity.ERROR),
    22 to ErrorInfo(22, "Minimum_Temperature not in plausible range",        Severity.ERROR),
    31 to ErrorInfo(31, "now_temperature is not float",                      Severity.ERROR),
    32 to ErrorInfo(32, "now_temperature not in plausible range",            Severity.ERROR),
    41 to ErrorInfo(41, "max_rh is not float",                               Severity.ERROR),
    42 to ErrorInfo(42, "max_rh not in plausible range",                     Severity.ERROR),
    51 to ErrorInfo(51, "min_rh is not float",                               Severity.ERROR),
    52 to ErrorInfo(52, "min_rh not in plausible range",                     Severity.ERROR),
    61 to ErrorInfo(61, "now_rh is not float",                               Severity.ERROR),
    62 to ErrorInfo(62, "now_rh not in plausible range",                     Severity.ERROR),
    71 to ErrorInfo(71, "Rainfall_Hourly_Cumulative is not float",           Severity.ERROR),
    72 to ErrorInfo(72, "Rainfall_Hourly_Cumulative not in plausible range", Severity.ERROR),
    81 to ErrorInfo(81, "wind_speed is not float",                           Severity.ERROR),
    82 to ErrorInfo(82, "wind_speed not in plausible range",                 Severity.ERROR),
    91 to ErrorInfo(91, "wind_direction is not float",                       Severity.ERROR),
    92 to ErrorInfo(92, "wind_direction not in plausible range",             Severity.ERROR),
)

fun resolveError(code: Int): ErrorInfo =
    ERROR_MAP[code] ?: ErrorInfo(code, "Unknown error code: $code", Severity.ERROR)

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
fun ByteArray.toHexCompact(): String = joinToString("") { "%02X".format(it) }

// ─────────────────────────────────────────────────────────────────────────────
// SENSOR DATA SCANNER
// ─────────────────────────────────────────────────────────────────────────────

class SensorDataScanner(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var leScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val scanScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _sensorData = MutableStateFlow<SensorData?>(null)
    val sensorData: StateFlow<SensorData?> = _sensorData

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanStatus = MutableStateFlow("Idle")
    val scanStatus: StateFlow<String> = _scanStatus

    private fun hasPermissions(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        else true

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermissions()) { BleLogger.e("BLUETOOTH_SCAN permission missing"); return }
        if (adapter == null)   { BleLogger.e("BluetoothAdapter is null"); return }
        if (_isScanning.value) { BleLogger.w("Already scanning"); return }

        leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            BleLogger.e("BluetoothLeScanner null — BT off?")
            _scanStatus.value = "BLE not available"
            return
        }

        _isScanning.value = true
        _scanStatus.value = "Scanning... (target: $TARGET_MAC)"
        BleLogger.i("Scan started — waiting for $DEVICE_NAME ($TARGET_MAC)")

        scanCallback = object : ScanCallback() {

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val mac    = result.device.address
                val rssi   = result.rssi
                val record = result.scanRecord ?: return
                val raw    = record.bytes?.copyOf()

                if (!mac.equals(TARGET_MAC, ignoreCase = true)) return

                scanScope.launch { processDevice(mac, rssi, raw, record) }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                val snapshots = results
                    .filter { it.device.address.equals(TARGET_MAC, ignoreCase = true) }
                    .map { Triple(it.device.address, it.rssi, it.scanRecord) }

                scanScope.launch {
                    if (snapshots.isNotEmpty())
                        BleLogger.b("Batch: ${snapshots.size} packets from $DEVICE_NAME")
                    snapshots.forEach { (mac, rssi, record) ->
                        if (record != null) processDevice(mac, rssi, null, record)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val reason = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED                 -> "ALREADY_STARTED"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APP_REG_FAILED"
                    SCAN_FAILED_INTERNAL_ERROR                  -> "INTERNAL_ERROR"
                    SCAN_FAILED_FEATURE_UNSUPPORTED             -> "FEATURE_UNSUPPORTED"
                    else                                        -> "code=$errorCode"
                }
                BleLogger.e("Scan FAILED: $reason")
                _isScanning.value = false
                _scanStatus.value = "Scan failed ($reason)"
            }
        }

        try {
            leScanner?.startScan(
                null,
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0)
                    .setLegacy(false)
                    .build(),
                scanCallback
            )
        } catch (e: SecurityException) {
            BleLogger.e("startScan SecurityException: ${e.message}")
            _isScanning.value = false
            _scanStatus.value = "Permission denied"
        }
    }

    private fun processDevice(
        mac: String, rssi: Int,
        rawBytes: ByteArray?, record: ScanRecord
    ) {
        BleLogger.b("✅ TARGET FOUND: $mac RSSI=$rssi")
        val raw       = rawBytes ?: record.bytes ?: return
        val rawHexStr = raw.toHex()
        val rawSize   = raw.size
        BleLogger.d("RAW(${raw.size}B): $rawHexStr")

        val mfgData = extractManufacturerData(raw)
        if (mfgData != null) {
            val mfgId   = mfgData.first
            val payload = mfgData.second
            BleLogger.i(
                "Found MFG Data → ID=0x${
                    mfgId.toString(16).uppercase().padStart(4, '0')
                } | size=${payload.size}B"
            )
            BleLogger.i("MFG Payload: ${payload.toHex()}")
            parsePayload(payload, mac, mfgId, rawHexStr, payload.toHex(), rawSize)
        } else {
            BleLogger.w("No manufacturer data found in raw bytes")
            _sensorData.value = SensorData(
                sourceMac = mac, detectedMfgId = -1,
                rawHex = rawHexStr, mfgHex = "No manufacturer data",
                rawSize = rawSize, timestamp = System.currentTimeMillis()
            )
        }
    }

    private fun extractManufacturerData(raw: ByteArray): Pair<Int, ByteArray>? {
        var i = 0
        while (i + 1 < raw.size) {
            val length = raw[i].toInt() and 0xFF
            if (length == 0) break
            if (i + length >= raw.size) break
            val type = raw[i + 1].toInt() and 0xFF
            if (type == 0xFF && length >= 3) {
                val companyId = ((raw[i + 2].toInt() and 0xFF)) or
                        ((raw[i + 3].toInt() and 0xFF) shl 8)
                val payload = raw.copyOfRange(i + 4, i + 1 + length)
                return Pair(companyId, payload)
            }
            i += length + 1
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PARSE PAYLOAD — confirmed format from screenshot
    //
    //  mfgData (after company ID strip):
    //  [0]      = header byte 1 (0x66) — skip
    //  [1]      = header byte 2 (0x1D) — skip
    //  [2]      = deviceId
    //  [3]      = tempInt   (signed)
    //  [4]      = tempDec
    //  [5]      = humInt    (signed)
    //  [6]      = humDec
    //  [7]      = windSpeedInt (signed)
    //  [8]      = windSpeedDec
    //  [9]      = windDir LSB  (little-endian 16-bit)
    //  [10]     = windDir MSB
    //  [11]     = rainInt   (signed)
    //  [12]     = rainDec
    //  [13..34] = 22 fixed error slots (0x00 = inactive)
    // ─────────────────────────────────────────────────────────────────────────
    private fun parsePayload(
        mfgData: ByteArray, sourceMac: String, mfgId: Int,
        rawHexStr: String, mfgHexStr: String, rawSize: Int
    ) {
        val n = mfgData.size
        BleLogger.i("Payload size: ${n}B")

        if (n < 11) {
            BleLogger.e("Payload too small: ${n}B")
            return
        }

        fun safe(i: Int, signed: Boolean = false): Int {
            if (i >= n) return 0
            return if (signed) mfgData[i].toInt()
            else               mfgData[i].toInt() and 0xFF
        }

        // 59 00 = company ID — extractManufacturerData mein strip ho chuka
        // payload[0] se directly sensor data start hota hai — koi header nahi
        //
        // [0]      = deviceId
        // [1]      = tempInt   (signed)
        // [2]      = tempDec
        // [3]      = humInt    (signed)
        // [4]      = humDec
        // [5]      = windSpeedInt (signed)
        // [6]      = windSpeedDec
        // [7]      = windDir LSB (little-endian)
        // [8]      = windDir MSB
        // [9]      = rainInt   (signed)
        // [10]     = rainDec
        // [11..32] = 22 error slots — non-zero = active error code, 0x00 = inactive

        val deviceId       = safe(0)
        val temperature    = safe(1, signed = true) + (safe(2) / 100.0f)
        val humidity       = safe(3, signed = true) + (safe(4) / 100.0f)
        val windSpeed      = safe(5, signed = true) + (safe(6) / 100.0f)
        val windDirection  = (safe(8) shl 8) or safe(7)
        val rainCumulative = safe(9, signed = true) + (safe(10) / 100.0f)

        // ── Error slots [11..32] — 22 slots ──────────────────────────────────
        val errorCodes    = mutableListOf<Int>()
        val allErrorSlots = mutableListOf<Pair<Int, Boolean>>()

        for ((byteIndex, expectedCode) in ERROR_SLOT_MAP) {
            val rawVal    = safe(byteIndex)
            val isActive  = rawVal != 0x00
            val codeToUse = if (isActive) rawVal else expectedCode
            allErrorSlots.add(Pair(codeToUse, isActive))
            if (isActive) errorCodes.add(rawVal)
        }

        BleLogger.d(
            "✅ devId=$deviceId temp=${temperature}°C hum=${humidity}% " +
                    "wind=${windSpeed}m/s dir=${windDirection}° " +
                    "rain=${rainCumulative}mm activeErrors=$errorCodes"
        )

        _sensorData.value = SensorData(
            deviceId       = deviceId,
            temperature    = temperature,
            humidity       = humidity,
            windSpeed      = windSpeed,
            windDirection  = windDirection,
            rainCumulative = rainCumulative,
            errorCodes     = errorCodes,
            allErrorSlots  = allErrorSlots,
            timestamp      = System.currentTimeMillis(),
            sourceMac      = sourceMac,
            detectedMfgId  = mfgId,
            rawHex         = rawHexStr,
            mfgHex         = mfgHexStr,
            rawSize        = rawSize
        )
        _scanStatus.value = "✅ Data received from $DEVICE_NAME"
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!hasPermissions()) return
        try { scanCallback?.let { leScanner?.stopScan(it) } }
        catch (e: SecurityException) { BleLogger.e("stopScan: ${e.message}") }
        _isScanning.value = false
        _scanStatus.value = "Stopped"
        scanCallback = null
        BleLogger.i("BLE scan stopped")
    }

    fun clearData() {
        _sensorData.value = null
        _scanStatus.value = if (_isScanning.value) "Scanning for $DEVICE_NAME..." else "Idle"
    }

    fun destroy() {
        stopScan()
        scanScope.cancel()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMMAND SENDER
// ─────────────────────────────────────────────────────────────────────────────

class CommandSender(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser

    private val companyId = 0x0059
    private var currentCallback: AdvertiseCallback? = null

    companion object {
        val TRIGGER_SIGNATURE = byteArrayOf(0xBB.toByte(), 0xCC.toByte())
        val RESET_SIGNATURE   = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
    }

    private fun hasPermissions(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) ==
                    PackageManager.PERMISSION_GRANTED
        else true

    @SuppressLint("MissingPermission")
    fun sendCommand(command: ByteArray, durationMs: Long = 5000L) {
        if (!hasPermissions()) { BleLogger.e("BLUETOOTH_ADVERTISE permission missing"); return }
        if (advertiser == null) { BleLogger.e("BluetoothLeAdvertiser is null"); return }
        // We now append MAC, so we allow 2-byte commands
        if (command.isEmpty())  { BleLogger.e("Command is empty"); return }

        stopAdvertising()
        val hex = command.toHexCompact()

        // Append TARGET_MAC bytes
        val macBytes = try {
            TARGET_MAC.split(":").map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            byteArrayOf()
        }
        val finalPayload = command + macBytes

        BleLogger.i("Sending 0x$hex + MAC targeting $TARGET_MAC for ${durationMs}ms")

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

        currentCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                BleLogger.i("Advertising OK — 0x$hex")
            }
            override fun onStartFailure(errorCode: Int) {
                val reason = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE       -> "DATA_TOO_LARGE"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                    ADVERTISE_FAILED_ALREADY_STARTED      -> "ALREADY_STARTED"
                    ADVERTISE_FAILED_INTERNAL_ERROR       -> "INTERNAL_ERROR"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED  -> "FEATURE_UNSUPPORTED"
                    else                                  -> "code=$errorCode"
                }
                BleLogger.e("Advertising FAILED: $reason")
                currentCallback = null
            }
        }

        try {
            advertiser.startAdvertising(settings, data, currentCallback)
            MainScope().launch {
                delay(durationMs)
                BleLogger.i("Auto-stopping after ${durationMs}ms")
                stopAdvertising()
            }
        } catch (e: SecurityException) {
            BleLogger.e("sendCommand SecurityException: ${e.message}")
        }
    }

    fun sendTriggerCommand(durationMs: Long = 5000L) {
        BleLogger.i("sendTriggerCommand → $DEVICE_NAME")
        sendCommand(TRIGGER_SIGNATURE, durationMs)
    }

    fun sendResetCommand(durationMs: Long = 5000L) {
        BleLogger.i("sendResetCommand → $DEVICE_NAME")
        sendCommand(RESET_SIGNATURE, durationMs)
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (!hasPermissions()) return
        try {
            currentCallback?.let {
                advertiser?.stopAdvertising(it)
                currentCallback = null
                BleLogger.i("Advertising stopped")
            }
        } catch (e: SecurityException) { BleLogger.e("stopAdvertising: ${e.message}") }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COLORS
// ─────────────────────────────────────────────────────────────────────────────

private val BgColor     = Color(0xFF0B1622)
private val CardColor   = Color(0xFF152030)
private val AccentColor = Color(0xFF00D4FF)
private val OkColor     = Color(0xFF4CAF50)
private val WarnColor   = Color(0xFFFFC107)
private val ErrColor    = Color(0xFFF44336)
private val TextPrimary = Color(0xFFECF0F1)
private val TextSecond  = Color(0xFF7B90A0)
private val LogBg       = Color(0xFF0A1520)
private val RawBg       = Color(0xFF0D1B2A)
private val ErrBgColor  = Color(0xFF1A0808)
private val WarnBgColor = Color(0xFF1A1400)
private val InactiveBg  = Color(0xFF0F1A24)

// ─────────────────────────────────────────────────────────────────────────────
// MAIN SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@Composable
fun SensorErrorScreen(
    navController: NavController,
    viewModel: BluetoothScanViewModel<Any> = viewModel(
        factory = BluetoothScanViewModelFactory(LocalContext.current)
    )
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val dataScanner = remember { SensorDataScanner(context) }
    val cmdSender   = remember { CommandSender(context) }

    val sensorData by dataScanner.sensorData.collectAsState()
    val isScanning by dataScanner.isScanning.collectAsState()
    val scanStatus by dataScanner.scanStatus.collectAsState()
    val logs       by BleLogger.logs.collectAsState()

    var isSendingTrigger by remember { mutableStateOf(false) }
    var isSendingReset   by remember { mutableStateOf(false) }
    var lastCommandType  by remember { mutableStateOf<String?>(null) }
    var showLogPanel     by remember { mutableStateOf(true) }
    var showRawPanel     by remember { mutableStateOf(true) }

    val logListState = rememberLazyListState()
    val scrollState  = rememberScrollState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) logListState.animateScrollToItem(logs.size - 1)
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants.values.all { it }
        BleLogger.i("Permissions: $ok")
        if (ok) dataScanner.startScan()
        else BleLogger.e("Denied: ${grants.filterValues { !it }.keys}")
    }

    LaunchedEffect(Unit) {
        val needed = buildList {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            BleLogger.w("Requesting: $needed")
            permLauncher.launch(needed.toTypedArray())
        } else {
            BleLogger.i("All permissions granted — scanning for $DEVICE_NAME ($TARGET_MAC)")
            dataScanner.startScan()
        }
    }

    LaunchedEffect(sensorData) {
        if (sensorData != null) {
            isSendingTrigger = false
            isSendingReset   = false
            lastCommandType  = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            dataScanner.destroy()
            cmdSender.stopAdvertising()
        }
    }

    // Saare 22 error slots — active + inactive
    val errorDisplayItems = remember(sensorData?.allErrorSlots) {
        sensorData?.allErrorSlots?.map { (code, isActive) ->
            ErrorDisplayItem(resolveError(code), isActive)
        } ?: emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {

            // ── TOP BAR ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    dataScanner.destroy()
                    cmdSender.stopAdvertising()
                    navController.popBackStack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        DEVICE_NAME,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = AccentColor, textAlign = TextAlign.Center
                    )
                    Text(
                        TARGET_MAC,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp, color = TextSecond,
                        textAlign = TextAlign.Center
                    )
                }
                IconButton(onClick = { dataScanner.clearData() }) {
                    Icon(Icons.Default.Clear, "Clear", tint = TextPrimary)
                }
            }

            // ── SCAN STATUS ───────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "scan")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "alpha"
                )
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (isScanning) OkColor.copy(alpha = alpha) else TextSecond)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    scanStatus, color = TextSecond,
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
            }

            // ── COMMAND BUTTONS ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (isSendingTrigger) return@Button
                        dataScanner.clearData()
                        isSendingTrigger = true; lastCommandType = "TRIGGER"
                        cmdSender.sendTriggerCommand(30000L)
                        scope.launch { delay(30000); isSendingTrigger = false }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSendingTrigger) Color(0xFF162535) else AccentColor
                    ),
                    enabled = !isSendingTrigger && !isSendingReset
                ) {
                    if (isSendingTrigger) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp), color = AccentColor, strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Waiting...", fontSize = 12.sp, color = AccentColor)
                    } else {
                        Icon(
                            Icons.Default.PlayArrow, null,
                            tint = Color.Black, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Get Data", fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = Color.Black
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isSendingReset) return@Button
                        dataScanner.clearData()
                        isSendingReset = true; lastCommandType = "RESET"
                        cmdSender.sendResetCommand(30000L)
                        scope.launch { delay(30000); isSendingReset = false }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSendingReset) Color(0xFF162535) else Color(0xFF37474F)
                    ),
                    enabled = !isSendingTrigger && !isSendingReset
                ) {
                    if (isSendingReset) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp), color = AccentColor, strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Resetting...", fontSize = 12.sp, color = AccentColor)
                    } else {
                        Icon(
                            Icons.Default.Refresh, null,
                            tint = TextPrimary, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Reset Sensor", fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = TextPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── RAW DATA PANEL ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "🔬 Raw BLE Payload", color = WarnColor, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = { showRawPanel = !showRawPanel },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (showRawPanel) "Hide" else "Show",
                        color = WarnColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (showRawPanel) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RawBg)
                        .border(1.dp, WarnColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    if (sensorData == null) {
                        Text(
                            "Waiting for $DEVICE_NAME packet...",
                            color = TextSecond, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Column {
                            Text(
                                "ADV RAW (${sensorData!!.rawSize}B):",
                                color = WarnColor, fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                            )
                            Text(
                                sensorData!!.rawHex.ifEmpty { "N/A" },
                                color = Color(0xFFFFE082), fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Text(
                                "MFG DATA (ID=0x${
                                    sensorData!!.detectedMfgId
                                        .takeIf { it >= 0 }
                                        ?.toString(16)?.uppercase()?.padStart(4, '0') ?: "----"
                                }):",
                                color = AccentColor, fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                            )
                            Text(
                                sensorData!!.mfgHex.ifEmpty { "N/A" },
                                color = Color(0xFF80DEEA), fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── LOG PANEL ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "📋 Debug Log", color = AccentColor, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                )
                Row {
                    TextButton(
                        onClick = { BleLogger.clear() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "Clear", color = TextSecond,
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                    TextButton(
                        onClick = { showLogPanel = !showLogPanel },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (showLogPanel) "Hide" else "Show",
                            color = AccentColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if (showLogPanel) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth().height(160.dp)
                        .clip(RoundedCornerShape(10.dp)).background(LogBg)
                        .border(1.dp, AccentColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            "No logs yet...", color = TextSecond, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(state = logListState) {
                            items(logs) { entry -> LogLine(entry) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── SENSOR DATA / EMPTY STATE ─────────────────────────────────────
            if (sensorData == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌤️", fontSize = 56.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = when {
                                isSendingTrigger || isSendingReset ->
                                    "Command sent — waiting for\n$DEVICE_NAME ($TARGET_MAC)..."
                                lastCommandType != null ->
                                    "Waiting for $DEVICE_NAME response..."
                                else ->
                                    "Press 'Get Data' to fetch weather\nfrom $DEVICE_NAME"
                            },
                            color = TextSecond, fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp, textAlign = TextAlign.Center
                        )
                    }
                }
            } else {

                // Source info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Text("📡", fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "$DEVICE_NAME  •  ${sensorData!!.sourceMac}  " +
                                "@ ${SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    .format(Date(sensorData!!.timestamp))}",
                        color = AccentColor.copy(alpha = 0.8f),
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace
                    )
                }

                // Sensor cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SensorCard(
                        "Temperature", String.format("%.1f", sensorData!!.temperature),
                        "°C", "🌡️", Modifier.weight(1f)
                    )
                    SensorCard(
                        "Humidity", String.format("%.1f", sensorData!!.humidity),
                        "%", "💧", Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SensorCard(
                        "Wind Speed", String.format("%.1f", sensorData!!.windSpeed),
                        "m/s", "💨", Modifier.weight(1f)
                    )
                    SensorCard(
                        "Wind Dir", sensorData!!.windDirection.toString(),
                        "°", "🧭", Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SensorCard(
                        "Rain", String.format("%.1f", sensorData!!.rainCumulative),
                        "mm", "🌧️", Modifier.weight(1f)
                    )
                    SensorCard(
                        "DeviceID", sensorData!!.deviceId.toString(),
                        "", "📡", Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ── SYSTEM ERRORS ─────────────────────────────────────────────
                val activeErrors = errorDisplayItems.filter { it.isActive }
                val hasError     = activeErrors.any { it.info.severity == Severity.ERROR }
                val hasWarning   = activeErrors.any { it.info.severity == Severity.WARNING }

                Text(
                    "⚠️ SYSTEM ERRORS ⚠️",
                    color = when {
                        hasError   -> ErrColor
                        hasWarning -> WarnColor
                        else       -> OkColor
                    },
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatChip(
                        "${errorDisplayItems.count { it.isActive }} Active",
                        AccentColor, Modifier.weight(1f)
                    )
                    StatChip(
                        "${errorDisplayItems.count {
                            it.isActive && it.info.severity == Severity.WARNING
                        }} Warn",
                        WarnColor, Modifier.weight(1f)
                    )
                    StatChip(
                        "${errorDisplayItems.count {
                            it.isActive && it.info.severity == Severity.ERROR
                        }} Err",
                        ErrColor, Modifier.weight(1f)
                    )
                    StatChip(
                        "${errorDisplayItems.count { !it.isActive }} OK",
                        OkColor, Modifier.weight(1f)
                    )
                }

                // All 22 error rows — active = colored, inactive = grey dimmed
                if (errorDisplayItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardColor)
                            .border(
                                1.dp, TextSecond.copy(alpha = 0.2f), RoundedCornerShape(12.dp)
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Waiting for error data...",
                            color = TextSecond, fontFamily = FontFamily.Monospace, fontSize = 13.sp
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        errorDisplayItems.forEach { item ->
                            ErrorRow(info = item.info, isActive = item.isActive)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LogLine(entry: LogEntry) {
    val tagColor = when (entry.tag.trim()) {
        "INFO" -> Color(0xFF4CAF50); "WARN" -> Color(0xFFFFC107)
        "ERR"  -> Color(0xFFF44336); "DATA" -> Color(0xFF00D4FF)
        "BLE"  -> Color(0xFF9C27B0); else   -> Color(0xFF7B90A0)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            entry.time, color = Color(0xFF4A6070), fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.width(76.dp)
        )
        Text(
            "[${entry.tag}]", color = tagColor, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(44.dp)
        )
        Text(
            entry.message, color = Color(0xFFB0C4D0), fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SensorCard(
    title: String, value: String, unit: String,
    icon: String, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(110.dp).clip(RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    title, color = TextSecond, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium
                )
                Text(icon, fontSize = 20.sp)
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    value, color = AccentColor, fontSize = 28.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                )
                Text(
                    unit, color = TextSecond, fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = color, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ErrorRow(info: ErrorInfo, isActive: Boolean) {
    // Severity + active state ke hisaab se colors
    val color = when {
        !isActive                         -> TextSecond
        info.severity == Severity.ERROR   -> ErrColor
        info.severity == Severity.WARNING -> WarnColor
        else                              -> OkColor
    }
    val bgColor = when {
        !isActive                         -> InactiveBg
        info.severity == Severity.ERROR   -> ErrBgColor
        info.severity == Severity.WARNING -> WarnBgColor
        else                              -> CardColor
    }
    val textColor = when {
        !isActive                         -> TextSecond.copy(alpha = 0.45f)
        info.severity == Severity.ERROR   -> Color(0xFFFF6B6B)
        info.severity == Severity.WARNING -> Color(0xFFFFD54F)
        else                              -> TextPrimary
    }
    val badge = when {
        !isActive                         -> "Inactive"
        info.severity == Severity.ERROR   -> "ERR"
        info.severity == Severity.WARNING -> "WARN"
        else                              -> "OK"
    }
    val borderAlpha = if (isActive) when (info.severity) {
        Severity.ERROR   -> 0.6f
        Severity.WARNING -> 0.5f
        Severity.OK      -> 0.3f
    } else 0.12f

    val codeAlpha  = if (isActive) 1f    else 0.35f
    val badgeAlpha = if (isActive) 0.20f else 0.07f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, color.copy(alpha = borderAlpha), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Error code circle
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (isActive) 0.18f else 0.07f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${info.code}",
                color = color.copy(alpha = codeAlpha),
                fontSize = if (info.code >= 100) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.width(12.dp))
        // Error description
        Text(
            info.meaning,
            color = textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        // Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(color.copy(alpha = badgeAlpha))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                badge,
                color = color.copy(alpha = codeAlpha),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}