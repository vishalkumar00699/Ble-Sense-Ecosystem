package com.blesense.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.SparseArray
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import com.blesense.app.api.RetrofitClient
import com.blesense.app.api.SensorPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BluetoothScanViewModel<T>(private val context: Context) : ViewModel() {

    sealed class SensorData {
        abstract val deviceId: String

        data class SHT40Data(
            override val deviceId: String,
            val temperature: String,
            val humidity: String
        ) : SensorData()

        data class LuxSensorData(
            override val deviceId: String,
            val lux: String,
            val rawData: String
        ) : SensorData()

        data class LIS2DHData(
            override val deviceId: String,
            val x: String,
            val y: String,
            val z: String
        ) : SensorData()

        data class SoilSensorData(
            override val deviceId: String,
            val nitrogen: String,
            val phosphorus: String,
            val potassium: String,
            val moisture: String,
            val temperature: String,
            val ec: String,
            val pH: String,
            val salinity: String
        ) : SensorData()

        data class SDTData(
            override val deviceId: String,
            val speed: String,
            val distance: String
        ) : SensorData()

        data class AmmoniaSensorData(
            override val deviceId: String,
            val ammonia: String,
            val rawData: String
        ) : SensorData()

        data class TempLoggerData(
            override val deviceId: String,
            val temperature: String,
            val humidity: String,
            val rawTemperature: Int,
            val rawHumidity: Int,
            val rawData: String,
            val deviceAddress: String,
            val timestamp: Long = System.currentTimeMillis()
        ) : SensorData() {
            val displaySummary: String
                get() = "Temp: $temperature°C, Hum: $humidity%, Device: $deviceId"
        }

        data class Sen66Data(
            override val deviceId: String,
            val pm1: String,
            val pm25: String,
            val pm4: String,
            val pm10: String,
            val temperature: String,
            val humidity: String,
            val co2: String,
            val voc: String,
            val nox: String,
            val deviceAddress: String = "",
            val timestamp: Long = System.currentTimeMillis()
        ) : SensorData() {

            val displaySummary: String
                get() = "PM2.5: $pm25 μg/m³, CO₂: $co2 ppm, Temp: $temperature°C, RH: $humidity%"

            val airQualityIndex: String
                get() = when {
                    pm25.toDoubleOrNull()?.let { it <= 12.0 } == true -> "Good"
                    pm25.toDoubleOrNull()?.let { it <= 35.4 } == true -> "Moderate"
                    pm25.toDoubleOrNull()?.let { it <= 55.4 } == true -> "Unhealthy for Sensitive Groups"
                    pm25.toDoubleOrNull()?.let { it <= 150.4 } == true -> "Unhealthy"
                    else -> "Very Unhealthy"
                }

            val airQualityColor: Int
                get() = when (airQualityIndex) {
                    "Good" -> 0xFF00FF00.toInt()
                    "Moderate" -> 0xFFFFFF00.toInt()
                    "Unhealthy for Sensitive Groups" -> 0xFFFFA500.toInt()
                    "Unhealthy" -> 0xFFFF0000.toInt()
                    else -> 0xFF800080.toInt()
                }

            val co2Quality: String
                get() = when {
                    co2.toIntOrNull()?.let { it < 800 } == true -> "Good"
                    co2.toIntOrNull()?.let { it < 1200 } == true -> "Fair"
                    co2.toIntOrNull()?.let { it < 2000 } == true -> "Poor"
                    else -> "Very Poor"
                }
        }

        data class DataLoggerData(
            override val deviceId: String,
            val currentPacketId: Int,
            val lastPacketId: Int,
            val payloadAccel: List<Triple<Int, Int, Int>>,
            val timestamp: Long,
            val rawData: String
        ) : SensorData() {

            val displaySummary: String
                get() = "Packet: $currentPacketId (last: $lastPacketId), Points: ${payloadAccel.size}, Time: $timestamp"
        }
    }

    data class BluetoothDevice(
        val name: String,
        val rssi: String,
        val address: String,
        val deviceId: String,
        val sensorData: SensorData? = null,
        val scanRecordBytes: ByteArray? = null,
        val isScannable: Boolean = false,
        val isConnectable: Boolean = false,
        val lastSeen: Long = System.currentTimeMillis()
    )

    data class HistoricalDataEntry(
        val timestamp: Long,
        val sensorData: SensorData?,
        val rawData: ByteArray? = null
    )

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private var scanJob: Job? = null

    // Hardcoded sensor MAC for optimized discovery
    private val POWER_OPTIMIZED_MAC = "DE:AD:BE:AF:BA:58"

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _latestPacketId = MutableStateFlow(-1)
    val latestPacketId: StateFlow<Int> = _latestPacketId.asStateFlow()

    val dataLoggerPacketHistory = MutableStateFlow<List<SensorData.DataLoggerData>>(emptyList())

    val tempLoggerPacketHistory = MutableStateFlow<Map<String, List<SensorData.TempLoggerData>>>(emptyMap())
    val latestTempLoggerPacket = MutableStateFlow<Map<String, SensorData.TempLoggerData?>>(emptyMap())

    val sen66PacketHistory = MutableStateFlow<Map<String, List<SensorData.Sen66Data>>>(emptyMap())
    val latestSen66Packet = MutableStateFlow<Map<String, SensorData.Sen66Data?>>(emptyMap())

    private val _latestDataLoggerPacket = MutableStateFlow<SensorData.DataLoggerData?>(null)
    val latestDataLoggerPacket: StateFlow<SensorData.DataLoggerData?> = _latestDataLoggerPacket.asStateFlow()

    // History update trigger for UI observation
    private val _historyUpdateTrigger = MutableStateFlow(0L)
    val historyUpdateTrigger: StateFlow<Long> = _historyUpdateTrigger.asStateFlow()

    fun getHistory(address: String): List<HistoricalDataEntry> {
        return deviceHistoricalData[address]?.toList() ?: emptyList()

    }

    private val deviceHistoricalData = HashMap<String, MutableList<HistoricalDataEntry>>()
    private val bluetoothScanner: BluetoothLeScanner? by lazy {
        BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner
    }

    companion object {
        private const val SCAN_RESTART_INTERVAL = 5 * 60 * 1000L
        private const val MAX_HISTORY_ENTRIES_PER_DEVICE = 1000
    }

    fun startContinuousScan(activity: Activity) {
        if (_isScanning.value) return

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _isScanning.value = true
            startScan(activity)

            while (isActive) {
                delay(SCAN_RESTART_INTERVAL)
                restartScan(activity)
            }
        }
    }

    private fun restartScan(activity: Activity) {
        stopScan()
        Handler(Looper.getMainLooper()).postDelayed({
            startScan(activity)
        }, 100)
    }

    @SuppressLint("MissingPermission")
    fun startScan(activity: Activity?) {
        if (!hasRequiredPermissions()) {
            Log.e("BLE", "Missing permissions to start scan")
            return
        }

        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                Log.e("BLE", "BluetoothAdapter is null")
                return
            }

            if (!adapter.isEnabled) {
                Log.e("BLE", "Bluetooth is DISABLED")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d("BLE", "Extended Advertising Support: ${adapter.isLeExtendedAdvertisingSupported}")
            }

            bluetoothScanner?.let { scanner ->
                val scanSettings = createScanSettings()
                scanCallback = createScanCallback()
                scanner.startScan(null, scanSettings, scanCallback)
                Log.d("BLE", "Scan started successfully")
            } ?: Log.e("BLE", "BluetoothLeScanner is null - is Bluetooth ON?")
        } catch (e: Exception) {
            Log.e("BLE", "Error starting scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            bluetoothScanner?.let { scanner ->
                scanCallback?.let {
                    scanner.stopScan(it)
                    scanCallback = null
                }
            }
        } catch (_: Exception) { }
        _isScanning.value = false
    }

    fun stopContinuousScan() {
        scanJob?.cancel()
        stopScan()
        _isScanning.value = false
    }

    private fun createScanSettings(): ScanSettings =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setLegacy(false)
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()

    private var scanCallback: ScanCallback? = null
    private var lastPacketIdGlobal: Int? = null
    private var lastPacketTimeGlobal: Long? = null
    private val PACKET_INTERVAL_MS = 10_000L

    private fun bytesToUInt(msb: Byte, lsb: Byte): Int {
        return ((msb.toInt() and 0xFF) shl 8) or (lsb.toInt() and 0xFF)
    }

    private fun packetDiff(last: Int, current: Int, max: Int = 65536): Int {
        return if (last >= current) {
            last - current
        } else {
            last + (max - current)
        }
    }

    private fun getScannable(result: ScanResult): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (result.dataStatus and ScanResult.DATA_COMPLETE) == ScanResult.DATA_COMPLETE
        } else {
            false
        }
    }

    private fun getConnectable(result: ScanResult): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result.isConnectable
        } else {
            false
        }
    }

    private fun createScanCallback(): ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            viewModelScope.launch(Dispatchers.Default) {
                processScanResult(result)
            }
        }

        private suspend fun processScanResult(result: ScanResult) {
            try {
                if (!hasRequiredPermissions()) return

                val device = result.device ?: return
                val deviceName = device.name ?: "N/A"
                val deviceAddress = device.address ?: return
                val deviceType = determineDeviceType(deviceName, deviceAddress)
                val manufacturerData = result.scanRecord?.manufacturerSpecificData
                var sensorData: SensorData? = null

                if (manufacturerData != null && manufacturerData.size() > 0) {
                    for (i in 0 until manufacturerData.size()) {
                        val data = manufacturerData.valueAt(i) ?: continue
                        val parsed = when (deviceType) {
                            "Ammonia Sensor" -> parseAmmoniaSensorData(data, deviceAddress)
                            "Lux Sensor" -> parseLuxSensorData(data, deviceAddress)
                            "TempLogger" -> parseTempLoggerData(data, deviceAddress, deviceName)
                            "DataLogger" -> parseDataLoggerData(data, deviceAddress)
                            "sen66" -> parseSen66Data(manufacturerData, deviceAddress)
                            else -> parseAdvertisingData(result, deviceType)
                        }
                        if (parsed != null) {
                            sensorData = parsed
                            break
                        }
                    }
                }

                if (sensorData == null) {
                    // Fallback: try parsing as any known type if deviceType was Unknown
                    if (manufacturerData != null && manufacturerData.size() > 0) {
                        for (i in 0 until manufacturerData.size()) {
                            val data = manufacturerData.valueAt(i) ?: continue
                            if (data.size >= 240) {
                                sensorData = parseDataLoggerData(data, deviceAddress)
                                if (sensorData != null) break
                            }
                        }
                    }
                }

                val bluetoothDevice = BluetoothDevice(
                    name = deviceName,
                    address = deviceAddress,
                    rssi = result.rssi.toString(),
                    deviceId = sensorData?.deviceId ?: "Unknown",
                    sensorData = sensorData,
                    scanRecordBytes = result.scanRecord?.bytes,
                    isScannable = getScannable(result),
                    isConnectable = getConnectable(result)
                )

                // Always save to history for diagnostic purposes
                val entry = HistoricalDataEntry(
                    timestamp = System.currentTimeMillis(),
                    sensorData = sensorData,
                    rawData = result.scanRecord?.bytes
                )
                
                synchronized(deviceHistoricalData) {
                    val historyList = deviceHistoricalData.getOrPut(deviceAddress) { mutableListOf() }
                    historyList.add(entry)
                    while (historyList.size > MAX_HISTORY_ENTRIES_PER_DEVICE) {
                        historyList.removeAt(0)
                    }
                }

                updateDevice(bluetoothDevice)
                _historyUpdateTrigger.value = System.currentTimeMillis()

                // Dashboard Sync
                if (sensorData != null) {
                    sendToDashboard(sensorData, deviceAddress)
                }

                if (deviceType == "DataLogger" && sensorData is SensorData.DataLoggerData) {
                    _latestPacketId.value = sensorData.currentPacketId
                }
            } catch (_: Exception) { }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            // Process the entire batch in a single background task for efficiency
            viewModelScope.launch(Dispatchers.Default) {
                for (result in results) {
                    processScanResult(result)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BLE", "Scan failed with error code: $errorCode")
            _isScanning.value = false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun sendAdvertiseCommandToSensor(deviceAddress: String, command: ByteArray) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val advertiser = bluetoothManager.adapter?.bluetoothLeAdvertiser ?: return

        val macBytes = try {
            deviceAddress.split(":").map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            return
        }

        val payload = command + macBytes

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(5000)
            .build()

        val data = AdvertiseData.Builder()
            .addManufacturerData(0x0059, payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d("BLE", "Targeted advertising started for $deviceAddress")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e("BLE", "Targeted advertising failed: $errorCode")
            }
        }
        advertiser.startAdvertising(settings, data, callback)
    }

    // Cache to prevent duplicate packets (Key: address + stable data key)
    private val sentPacketsCache = mutableSetOf<String>()

    private val appId: String by lazy { DeviceIdentifier.getOrGenerateId(context) }

    private fun sendToDashboard(sensorData: SensorData, address: String) {
        // Generate a stable key that ignores transient metadata like current timestamps
        val stableDataKey = when (sensorData) {
            is SensorData.DataLoggerData -> "dlog_${sensorData.lastPacketId}"
            is SensorData.TempLoggerData -> "tlog_${sensorData.rawData}"
            is SensorData.Sen66Data -> "sen66_${sensorData.co2}_${sensorData.pm25}_${sensorData.pm10}"
            is SensorData.SHT40Data -> "sht_${sensorData.temperature}_${sensorData.humidity}"
            is SensorData.AmmoniaSensorData -> "nh3_${sensorData.ammonia}"
            is SensorData.LuxSensorData -> "lux_${sensorData.lux}"
            else -> sensorData.hashCode().toString()
        }
        
        val packetKey = "${address}_$stableDataKey"
        
        synchronized(sentPacketsCache) {
            if (sentPacketsCache.contains(packetKey)) return
            sentPacketsCache.add(packetKey)
            // Keep cache size healthy
            if (sentPacketsCache.size > 500) {
                val iterator = sentPacketsCache.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dataMap = when (sensorData) {
                    is SensorData.SHT40Data -> mapOf(
                        "appId" to appId,
                        "deviceId" to sensorData.deviceId,
                        "temperature" to sensorData.temperature,
                        "humidity" to sensorData.humidity
                    )
                    is SensorData.SoilSensorData -> mapOf(
                        "appId" to appId,
                        "deviceId" to sensorData.deviceId,
                        "nitrogen" to sensorData.nitrogen,
                        "phosphorus" to sensorData.phosphorus,
                        "potassium" to sensorData.potassium,
                        "moisture" to sensorData.moisture,
                        "temperature" to sensorData.temperature,
                        "ec" to sensorData.ec,
                        "pH" to sensorData.pH,
                        "salinity" to sensorData.salinity
                    )
                    is SensorData.Sen66Data -> mapOf(
                        "appId" to appId,
                        "deviceId" to sensorData.deviceId,
                        "co2" to sensorData.co2,
                        "pm25" to sensorData.pm25,
                        "pm10" to sensorData.pm10,
                        "temperature" to sensorData.temperature,
                        "humidity" to sensorData.humidity,
                        "voc" to sensorData.voc
                    )
                    is SensorData.DataLoggerData -> {
                        val points = sensorData.payloadAccel.map {
                            mapOf("x" to it.first, "y" to it.second, "z" to it.third)
                        }
                        mapOf(
                            "appId" to appId,
                            "packetId" to sensorData.lastPacketId,
                            "totalPackets" to sensorData.currentPacketId,
                            "points" to points,
                            "deviceId" to sensorData.deviceId,
                            "rawData" to sensorData.rawData
                        )
                    }
                    is SensorData.AmmoniaSensorData -> mapOf(
                        "appId" to appId,
                        "deviceId" to sensorData.deviceId,
                        "ammonia" to sensorData.ammonia
                    )
                    is SensorData.LuxSensorData -> mapOf(
                        "appId" to appId,
                        "deviceId" to sensorData.deviceId,
                        "lux" to sensorData.lux
                    )
                    is SensorData.TempLoggerData -> mapOf(
                        "appId" to appId,
                        "deviceId" to sensorData.deviceId,
                        "temperature" to sensorData.temperature,
                        "humidity" to sensorData.humidity
                    )
                    else -> emptyMap()
                }

                if (dataMap.isNotEmpty()) {
                    RetrofitClient.instance.sendSensorData(
                        SensorPacket(System.currentTimeMillis(), dataMap)
                    )
                }
            } catch (e: Exception) {
                Log.e("Dashboard", "Sync error: ${e.message}")
                // Optional: remove from cache on failure to allow retry
                synchronized(sentPacketsCache) { sentPacketsCache.remove(packetKey) }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getHistoricalDataForDevice(address: String): MutableList<HistoricalDataEntry> {
        return deviceHistoricalData.getOrPut(address) { mutableListOf() }
    }

    fun parseAdvertisingData(result: ScanResult, deviceType: String?): SensorData? {
        val manufacturerData = result.scanRecord?.manufacturerSpecificData ?: return null
        if (manufacturerData.size() == 0) return null
        val deviceAddress = result.device?.address ?: return null

        for (i in 0 until manufacturerData.size()) {
            val data = manufacturerData.valueAt(i) ?: continue
            val parsed = when (deviceType) {
                "SHT40" -> parseSHT40Data(data)
                "LIS2DH" -> parseLIS2DHData(data)
                "Soil Sensor" -> parseSoilSensorData(data)
                "SPEED_DISTANCE" -> parseSDTData(data)
                "Ammonia Sensor" -> parseAmmoniaSensorData(data, deviceAddress)
                "Lux Sensor" -> parseLuxSensorData(data, deviceAddress)
                "DataLogger" -> parseDataLoggerData(data, deviceAddress)
                else -> if (data.size >= 240) parseDataLoggerData(data, deviceAddress) else null
            }
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseLuxSensorData(data: ByteArray?, deviceAddress: String): SensorData? {
        if (data == null || data.size < 5) return null
        val rawDataString = data.joinToString(" ") { "%02X".format(it) }
        val deviceId = data[0].toInt() and 0xFF
        val highLux = data[1].toInt() and 0xFF
        val lowLux = data[2].toInt() and 0xFF
        val luxValue = (highLux * 256) + lowLux
        return SensorData.LuxSensorData(
            deviceId = deviceId.toString(),
            lux = luxValue.toString(),
            rawData = rawDataString
        )
    }

    private fun parseTempLoggerData(
        data: ByteArray?,
        deviceAddress: String,
        deviceName: String
    ): SensorData.TempLoggerData? {

        if (data == null || data.size < 5) return null

        val rawDataString = data.joinToString(" ") { "%02X".format(it) }

        val deviceId = data[0].toInt() and 0xFF
        val tempInt = data[1].toInt() and 0xFF
        val tempFrac = data[2].toInt() and 0xFF
        val humInt = data[3].toInt() and 0xFF
        val humFrac = data[4].toInt() and 0xFF

        val temperature = "$tempInt.$tempFrac".toDouble()
        val humidity = "$humInt.$humFrac".toDouble()

        if (temperature in 5.0..60.0 && humidity in 10.0..99.0) {
            val packet = SensorData.TempLoggerData(
                deviceId = deviceId.toString(),
                temperature = String.format("%.2f", temperature),
                humidity = String.format("%.2f", humidity),
                rawTemperature = (tempInt * 100 + tempFrac),
                rawHumidity = (humInt * 100 + humFrac),
                rawData = rawDataString,
                deviceAddress = deviceAddress,
                timestamp = System.currentTimeMillis()
            )

            val deviceKey = deviceAddress

            val newLatestMap = latestTempLoggerPacket.value.toMutableMap()
            newLatestMap[deviceKey] = packet
            latestTempLoggerPacket.value = newLatestMap

            tempLoggerPacketHistory.update { currentMap ->
                val currentList = currentMap[deviceKey] ?: emptyList()
                val newList = if (currentList.any { it.rawData == packet.rawData }) {
                    currentList
                } else {
                    currentList + packet
                }

                val newMap = currentMap.toMutableMap()
                newMap[deviceKey] = newList
                newMap
            }
            return packet
        }

        return null
    }

    private fun parseSen66Data(
        manufacturerData: SparseArray<ByteArray>,
        deviceAddress: String
    ): SensorData.Sen66Data? {

        var data: ByteArray? = null
        var startOffset = 0

        for (i in 0 until manufacturerData.size()) {
            val candidate = manufacturerData.valueAt(i) ?: continue
            when (candidate.size) {
                19 -> { data = candidate; startOffset = 2; break }
                21 -> { data = candidate; startOffset = 0; break }
            }
        }

        if (data == null) return null

        fun readUInt16(mfgIdx: Int): Int {
            val localIdx = mfgIdx - startOffset
            if (localIdx < 0 || localIdx + 1 >= data!!.size) return 0
            val msb = data!![localIdx].toUByte().toInt()
            val lsb = data!![localIdx + 1].toUByte().toInt()
            return (msb shl 8) or lsb
        }

        fun readInt16(mfgIdx: Int): Int {
            val localIdx = mfgIdx - startOffset
            if (localIdx < 0 || localIdx + 1 >= data!!.size) return 0
            val msb = data!![localIdx].toUByte().toInt()
            val lsb = data!![localIdx + 1].toUByte().toInt()
            return ((msb shl 8) or lsb).toShort().toInt()
        }

        fun readByte(mfgIdx: Int): Int {
            val localIdx = mfgIdx - startOffset
            if (localIdx < 0 || localIdx >= data!!.size) return 0
            return data!![localIdx].toUByte().toInt()
        }

        val deviceId = readByte(2).toString()
        val pm1  = readUInt16(3)  / 10.0
        val pm25 = readUInt16(5)  / 10.0
        val pm4  = readUInt16(7)  / 10.0
        val pm10 = readUInt16(9)  / 10.0
        val temp = readInt16(11)  / 200.0
        val rh   = readInt16(13)  / 100.0
        val co2  = readUInt16(15)
        val voc  = readInt16(17)
        val nox  = readInt16(19)

        val sen66Data = SensorData.Sen66Data(
            deviceId = deviceId,
            pm1  = String.format("%.1f", pm1),
            pm25 = String.format("%.1f", pm25),
            pm4  = String.format("%.1f", pm4),
            pm10 = String.format("%.1f", pm10),
            temperature = String.format("%.2f", temp),
            humidity    = String.format("%.2f", rh),
            co2 = co2.toString(),
            voc = voc.toString(),
            nox = nox.toString(),
            deviceAddress = deviceAddress,
            timestamp = System.currentTimeMillis()
        )

        val newLatestMap = latestSen66Packet.value.toMutableMap()
        newLatestMap[deviceAddress] = sen66Data
        latestSen66Packet.value = newLatestMap

        sen66PacketHistory.update { currentMap ->
            val currentList = currentMap[deviceAddress] ?: emptyList()
            val newList = if (currentList.any {
                    it.pm25 == sen66Data.pm25 && it.temperature == sen66Data.temperature
                }) {
                currentList
            } else {
                currentList + sen66Data
            }
            val newMap = currentMap.toMutableMap()
            newMap[deviceAddress] = newList
            newMap
        }

        return sen66Data
    }

    private var baseTimestamp: Long? = null
    private var totalStoredPackets: Int = 0
    private var dumpBaseTime: Long? = null
    
    // Quick cache for DataLogger deduplication to avoid O(N) searches
    private val dataLoggerReceivedIds = mutableSetOf<Int>()

    private fun parseDataLoggerData(
        data: ByteArray?,
        deviceAddress: String
    ): SensorData.DataLoggerData? {

        if (data == null) return null
        
        if (data.size < 240) {
            // Log.v("BLE", "DataLogger: Too short (${data.size} bytes)")
            return null
        }

        val size = data.size
        val rawData = data.joinToString(" ") { "%02X".format(it) }

        val deviceId = (data[0].toInt() and 0xFF).toString()

        if ((data[size - 1].toInt() and 0xFF) != 0xFE) {
            Log.w("BLE", "DataLogger [$deviceAddress]: Missing 0xFE footer, got ${"%02X".format(data[size-1])}")
            // We'll continue anyway for now to see if data is still valid
        }

        val currentReceivedId = (data[size - 5].toInt() and 0xFF) or
                ((data[size - 4].toInt() and 0xFF) shl 8)

        val totalPacketsCount = (data[size - 3].toInt() and 0xFF) or
                ((data[size - 2].toInt() and 0xFF) shl 8)

        val now = System.currentTimeMillis()

        if (dumpBaseTime == null) {
            dumpBaseTime = now
        }

        val packetAge = (totalPacketsCount - currentReceivedId) * 8_000L
        val calculatedTimestamp = dumpBaseTime!! - packetAge

        val accelData = mutableListOf<Triple<Int, Int, Int>>()
        var index = 1
        // Chaining support: Use dynamic size instead of hardcoded 241
        val payloadEnd = size - 5

        while (index + 2 < payloadEnd) {
            val x = data[index++].toByte().toInt()
            val y = data[index++].toByte().toInt()
            val z = data[index++].toByte().toInt()
            accelData.add(Triple(x, y, z))
        }

        val loggerData = SensorData.DataLoggerData(
            deviceId = deviceId,
            currentPacketId = totalPacketsCount,
            lastPacketId = currentReceivedId,
            payloadAccel = accelData,
            timestamp = calculatedTimestamp,
            rawData = rawData
        )

        _latestDataLoggerPacket.value = loggerData

        // Efficient deduplication using a Set
        val isNew = synchronized(dataLoggerReceivedIds) {
            if (dataLoggerReceivedIds.contains(loggerData.lastPacketId)) {
                false
            } else {
                dataLoggerReceivedIds.add(loggerData.lastPacketId)
                // Keep cache size reasonable
                if (dataLoggerReceivedIds.size > 2000) {
                    dataLoggerReceivedIds.clear() // Reset on overflow
                }
                true
            }
        }

        if (isNew) {
            dataLoggerPacketHistory.update { currentList ->
                val newList = currentList + loggerData
                if (newList.size > MAX_HISTORY_ENTRIES_PER_DEVICE) {
                    newList.drop(newList.size - MAX_HISTORY_ENTRIES_PER_DEVICE)
                } else {
                    newList
                }
            }
        }

        if (currentReceivedId == totalPacketsCount) {
            dumpBaseTime = null
        }

        return loggerData
    }

    private fun parseSHT40Data(data: ByteArray): SensorData? {
        if (data.size < 5) return null
        val tempInt = data[1].toInt()
        val tempFrac = data[2].toUByte().toInt()
        val humInt = data[3].toInt()
        val humFrac = data[4].toUByte().toInt()
        val temperature = tempInt + tempFrac / 10000.0
        val humidity = humInt + humFrac / 10000.0
        return SensorData.SHT40Data(
            deviceId = data[0].toUByte().toString(),
            temperature = String.format("%.2f", temperature),
            humidity = String.format("%.2f", humidity)
        )
    }

    private fun parseLIS2DHData(data: ByteArray): SensorData? {
        if (data.size < 7) return null
        return SensorData.LIS2DHData(
            deviceId = data[0].toUByte().toString(),
            x = "${data[1].toInt()}.${data[2].toUByte()}",
            y = "${data[3].toInt()}.${data[4].toUByte()}",
            z = "${data[5].toInt()}.${data[6].toUByte()}"
        )
    }

    private fun parseSoilSensorData(data: ByteArray): SensorData? {
        if (data.size < 16) return null

        fun u(index: Int) = data[index].toUByte().toInt()

        return SensorData.SoilSensorData(
            deviceId = u(0).toString(),
            nitrogen = ((u(2) shl 8) or u(1)).toString(),
            phosphorus = ((u(4) shl 8) or u(3)).toString(),
            potassium = ((u(6) shl 8) or u(5)).toString(),
            moisture = u(7).toString(),
            temperature = "${u(8)}.${u(9)}",
            ec = ((u(11) shl 8) or u(10)).toString(),
            pH = "${u(12)}.${u(13)}",
            salinity = ((u(15) shl 8) or u(14)).toString()
        )
    }

    private fun parseSDTData(data: ByteArray): SensorData? {
        if (data.size < 6) return null
        return SensorData.SDTData(
            deviceId = data[0].toUByte().toString(),
            speed = "${data[1].toUByte()}.${data[2].toUByte()}",
            distance = "${data[4].toUByte()}.${data[5].toUByte()}"
        )
    }

    private fun parseAmmoniaSensorData(data: ByteArray?, deviceAddress: String): SensorData? {
        if (data == null || data.size < 6) return null
        val rawDataString = data.joinToString(" ") { String.format("%02X", it) }
        val deviceId = data[0].toUByte().toString()
        val ammoniaPpm = try { data[5].toUByte().toFloat() } catch (e: Exception) { return null }
        val ammoniaValue = String.format(Locale.US, "%.1f", ammoniaPpm)
        return SensorData.AmmoniaSensorData(
            deviceId = deviceId,
            ammonia = "$ammoniaValue ppm",
            rawData = rawDataString
        )
    }

    private fun determineDeviceType(name: String?, address: String): String = when {
        address.equals(POWER_OPTIMIZED_MAC, ignoreCase = true) -> "DataLogger"
        name?.contains("SHT", ignoreCase = true) == true -> "SHT40"
        name?.contains("Lux", ignoreCase = true) == true -> "Lux Sensor"
        name?.contains("SOIL", ignoreCase = true) == true -> "Soil Sensor"
        name?.contains("Activity", ignoreCase = true) == true -> "LIS2DH"
        name?.contains("Speed", ignoreCase = true) == true -> "SPEED_DISTANCE"
        name?.contains("NH", ignoreCase = true) == true -> "Ammonia Sensor"
        name?.contains("sen66", ignoreCase = true) == true -> "sen66"
        name?.contains("DataLogger", ignoreCase = true) == true -> "DataLogger"
        name?.contains("Data Logger", ignoreCase = true) == true -> "DataLogger"
        name?.contains("DLOG", ignoreCase = true) == true -> "DataLogger"
        name?.contains("TempLogger", ignoreCase = true) == true -> "TempLogger"
        name?.contains("TLOG", ignoreCase = true) == true -> "TempLogger"
        name?.contains("Temp Logger", ignoreCase = true) == true -> "TempLogger"
        else -> "Unknown Device"
    }

    private fun updateDevice(newDevice: BluetoothDevice) {
        _devices.update { devices ->
            val existingIndex = devices.indexOfFirst { it.address == newDevice.address }
            if (existingIndex >= 0) {
                val existing = devices[existingIndex]
                val updatedList = devices.toMutableList()
                
                // Keep the best name (don't overwrite a real name with N/A)
                val bestName = if (newDevice.name == "N/A" && existing.name != "N/A") {
                    existing.name
                } else {
                    newDevice.name
                }
                
                // Keep best sensorData if new one is null (unless we want to clear it, but usually we want persistence)
                val bestSensorData = newDevice.sensorData ?: existing.sensorData

                // Keep best scanRecordBytes
                val bestScanBytes = newDevice.scanRecordBytes ?: existing.scanRecordBytes

                updatedList[existingIndex] = newDevice.copy(
                    name = bestName,
                    sensorData = bestSensorData,
                    scanRecordBytes = bestScanBytes,
                    lastSeen = System.currentTimeMillis()
                )
                updatedList
            } else {
                devices + newDevice.copy(lastSeen = System.currentTimeMillis())
            }
        }
    }

    fun clearDevices() {
        _devices.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        stopContinuousScan()
    }
}