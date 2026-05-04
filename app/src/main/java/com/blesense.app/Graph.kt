package com.blesense.app

import android.app.Activity
import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontFamily.Companion.Monospace

/* -------------------------------------------------------------
   3-D helpers (unchanged)
   ------------------------------------------------------------- */
data class Point3D(val x: Float, val y: Float, val z: Float) {
    fun rotateX(a: Double) = Point3D(
        x,
        (y * cos(a) - z * sin(a)).toFloat(),
        (y * sin(a) + z * cos(a)).toFloat()
    )
    fun rotateY(a: Double) = Point3D(
        (x * cos(a) + z * sin(a)).toFloat(),
        y,
        (-x * sin(a) + z * cos(a)).toFloat()
    )
    fun rotateZ(a: Double) = Point3D(
        (x * cos(a) - y * sin(a)).toFloat(),
        (x * sin(a) + y * cos(a)).toFloat(),
        z
    )
}

/* -------------------------------------------------------------
   Main screen – English only, no translation
   ------------------------------------------------------------- */
@Composable
fun ChartScreen(navController: NavController, deviceAddress: String? = null) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val factory = remember { BluetoothScanViewModelFactory(app) }
    val vm: BluetoothScanViewModel<Any?> = viewModel(factory = factory)

    val dark by ThemeManager.isDarkMode.collectAsState()

    val sensorData by remember(deviceAddress) {
        vm.devices.map { it.find { d -> d.address == deviceAddress }?.sensorData }
    }.collectAsState(initial = null)

    // ---- raw sensor values -------------------------------------------------
    val temp   = (sensorData as? BluetoothScanViewModel.SensorData.SHT40Data)?.temperature?.toFloatOrNull()
    val hum    = (sensorData as? BluetoothScanViewModel.SensorData.SHT40Data)?.humidity?.toFloatOrNull()
    val speed  = (sensorData as? BluetoothScanViewModel.SensorData.SDTData)?.speed?.toFloatOrNull()
    val dist   = (sensorData as? BluetoothScanViewModel.SensorData.SDTData)?.distance?.toFloatOrNull()
    val accX   = (sensorData as? BluetoothScanViewModel.SensorData.LIS2DHData)?.x?.toFloatOrNull()
    val accY   = (sensorData as? BluetoothScanViewModel.SensorData.LIS2DHData)?.y?.toFloatOrNull()
    val accZ   = (sensorData as? BluetoothScanViewModel.SensorData.LIS2DHData)?.z?.toFloatOrNull()
    val soilM  = (sensorData as? BluetoothScanViewModel.SensorData.SoilSensorData)?.moisture?.toFloatOrNull()
    val soilT  = (sensorData as? BluetoothScanViewModel.SensorData.SoilSensorData)?.temperature?.toFloatOrNull()
    val soilN  = (sensorData as? BluetoothScanViewModel.SensorData.SoilSensorData)?.nitrogen?.toFloatOrNull()
    val soilP  = (sensorData as? BluetoothScanViewModel.SensorData.SoilSensorData)?.phosphorus?.toFloatOrNull()
    val soilK  = (sensorData as? BluetoothScanViewModel.SensorData.SoilSensorData)?.potassium?.toFloatOrNull()
    val soilEC = (sensorData as? BluetoothScanViewModel.SensorData.SoilSensorData)?.ec?.toFloatOrNull()
    val soilPH = (sensorData as? BluetoothScanViewModel.SensorData.SoilSensorData)?.pH?.toFloatOrNull()

    // ---- history buffers ---------------------------------------------------
    val tempH   = remember { mutableStateListOf<Float>() }
    val humH    = remember { mutableStateListOf<Float>() }
    val speedH  = remember { mutableStateListOf<Float>() }
    val distH   = remember { mutableStateListOf<Float>() }
    val accXH   = remember { mutableStateListOf<Float>() }
    val accYH   = remember { mutableStateListOf<Float>() }
    val accZH   = remember { mutableStateListOf<Float>() }
    val soilMH  = remember { mutableStateListOf<Float>() }
    val soilTH  = remember { mutableStateListOf<Float>() }
    val soilNH  = remember { mutableStateListOf<Float>() }
    val soilPHist = remember { mutableStateListOf<Float>() }
    val soilKHist = remember { mutableStateListOf<Float>() }
    val soilECHist = remember { mutableStateListOf<Float>() }
    val soilPHHist = remember { mutableStateListOf<Float>() }
    val timestamps = remember { mutableStateListOf<String>() }
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // ---- hard-coded English strings ----------------------------------------
    val titleGraphs            = "Graphs"
    val tempLabel              = "Temperature (°C)"
    val humLabel               = "Humidity (%)"
    val speedLabel             = "Speed (m/s)"
    val distLabel              = "Distance (m)"
    val xLabel                 = "X Axis (g)"
    val yLabel                 = "Y Axis (g)"
    val zLabel                 = "Z Axis (g)"
    val soilTitle              = "Soil Sensor Data (Click for detailed view)"
    val soilMoistLabel         = "Soil Moisture (%)"
    val soilTempLabel          = "Soil Temperature (°C)"
    val soilNLabel             = "Soil Nitrogen (ppm)"
    val soilPLabel             = "Soil Phosphorus (ppm)"
    val soilKLabel             = "Soil Potassium (ppm)"
    val soilECLabel            = "Soil EC (µS/cm)"
    val soilPHLabel            = "Soil pH"
    val clickAll               = "Click to view all soil parameters and table view"
    val waitingSensor          = "Waiting for sensor data..."
    val ensureConnected        = "Make sure the device is connected and sending data"
    val currentTxt             = "Current"
    val naTxt                  = "N/A"
    val tabGraphs              = "Graphs"
    val tabSoilTable           = "Soil Data Table"
    val backToSensors          = "Back to All Sensors"

    // ---- theme colours ------------------------------------------------------
    val bgGrad = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF121212), Color(0xFF424242)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF0A74DA), Color(0xFFADD8E6)))
    }
    val cardBg   = if (dark) Color(0xFF1E1E1E) else Color.White
    val txt      = if (dark) Color.White else Color.Black
    val txt2     = if (dark) Color(0xFFFFFFFF) else Color(0xFF2A2626)
    val accent   = if (dark) Color(0xFFBB86FC) else Color(0xFF0A74DA)
    val tabBg    = if (dark) Color(0xFF2A2A2A) else Color.Transparent
    val appBarBg = if (dark) Color(0xFF121212) else Color.White

    // ---- UI state -----------------------------------------------------------
    val receiving   = remember { mutableStateOf(false) }
    val hasSoil     = remember { mutableStateOf(false) }
    var soilClicked by remember { mutableStateOf(false) }
    var tabIdx      by remember { mutableStateOf(0) }
    val tabs        = listOf(tabGraphs, tabSoilTable)
    val flowState   = remember { mutableStateOf("Waiting for data...") }

    // ---- start scanning -----------------------------------------------------
    LaunchedEffect(Unit) { vm.startScan(ctx as Activity) }

    // ---- data-received flags ------------------------------------------------
    LaunchedEffect(sensorData) {
        receiving.value = temp != null || hum != null || speed != null || dist != null ||
                accX != null || accY != null || accZ != null ||
                soilM != null || soilT != null || soilN != null ||
                soilP != null || soilK != null || soilEC != null || soilPH != null

        hasSoil.value = soilM != null || soilT != null || soilN != null ||
                soilP != null || soilK != null || soilEC != null || soilPH != null
    }

    // ---- update histories ---------------------------------------------------
    LaunchedEffect(temp, hum, speed, dist, accX, accY, accZ,
        soilM, soilT, soilN, soilP, soilK, soilEC, soilPH) {

        temp?.let   { updateHistory(tempH,   it) }
        hum?.let    { updateHistory(humH,    it) }
        speed?.let  { updateHistory(speedH,  it) }
        dist?.let   { updateHistory(distH,   it) }

        val addTs = soilM != null || soilT != null || soilN != null ||
                soilP != null || soilK != null || soilEC != null || soilPH != null
        if (addTs) {
            if (timestamps.size >= 20) timestamps.removeAt(0)
            timestamps.add(fmt.format(Date()))
        }

        soilM?.let  { updateHistory(soilMH,  it) }
        soilT?.let  { updateHistory(soilTH,  it) }
        soilN?.let  { updateHistory(soilNH,  it) }
        soilP?.let  { updateHistory(soilPHist, it) }
        soilK?.let  { updateHistory(soilKHist, it) }
        soilEC?.let { updateHistory(soilECHist, it) }
        soilPH?.let { updateHistory(soilPHHist, it) }
    }

    LaunchedEffect(accX, accY, accZ) {
        val now = System.currentTimeMillis()
        flowState.value = "Data received at: ${SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(now))}"
        accX?.let { if (accXH.size >= 50) accXH.removeAt(0); accXH.add(it) }
        accY?.let { if (accYH.size >= 50) accYH.removeAt(0); accYH.add(it) }
        accZ?.let { if (accZH.size >= 50) accZH.removeAt(0); accZH.add(it) }
    }

    // -------------------------------------------------------------------------
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        titleGraphs,
                        fontFamily = Monospace,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = txt
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (dark) Color.White else Color.Black)
                    }
                },
                actions = {
                    IconButton(onClick = { /* export */ }) {
                        Icon(Icons.Default.TableChart, "Export", tint = if (dark) Color.White else Color.Black)
                    }
                    IconButton(onClick = { /* options */ }) {
                        Icon(Icons.AutoMirrored.Filled.List, "Options", tint = if (dark) Color.White else Color.Black)
                    }
                },
                backgroundColor = appBarBg,
                elevation = 0.dp
            )
        },
        backgroundColor = Color.Transparent
    ) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGrad)
                .padding(pad)
        ) {
            Column {
                // ----- TAB ROW (only when soil card is expanded) -----
                if (soilClicked && hasSoil.value) {
                    TabRow(
                        selectedTabIndex = tabIdx,
                        backgroundColor = tabBg,
                        contentColor = accent
                    ) {
                        tabs.forEachIndexed { i, t ->
                            Tab(
                                text = { Text(t, color = txt) },
                                selected = tabIdx == i,
                                onClick = { tabIdx = i }
                            )
                        }
                    }
                }

                // ----- MAIN CONTENT -----
                if (!soilClicked || (soilClicked && tabIdx == 0)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ---- SHT40 -------------------------------------------------
                        if (sensorData is BluetoothScanViewModel.SensorData.SHT40Data) {
                            item { SensorGraphCard(tempLabel, temp, tempH, Color(0xFFE53935), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            item { SensorGraphCard(humLabel, hum, humH, Color(0xFF1976D2), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                        }
                        // ---- SDT ---------------------------------------------------
                        if (sensorData is BluetoothScanViewModel.SensorData.SDTData) {
                            item { SensorGraphCard(speedLabel, speed, speedH, Color(0xFF43A047), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            item { SensorGraphCard(distLabel, dist, distH, Color(0xFFFFB300), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                        }
                        // ---- LIS2DH ------------------------------------------------
                        if (sensorData is BluetoothScanViewModel.SensorData.LIS2DHData) {
                            item { SensorGraphCard(xLabel, accX, accXH, Color(0xFFE91E63), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            item { SensorGraphCard(yLabel, accY, accYH, Color(0xFF9C27B0), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            item { SensorGraphCard(zLabel, accZ, accZH, Color(0xFF009688), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            item {
                                // ← Replaces Accelerometer3DVisualization
                                BleNodeVisualizer(
                                    xAxis              = accX,
                                    yAxis              = accY,
                                    zAxis              = accZ,
                                    cardBackground     = cardBg,
                                    textColor          = txt,
                                    secondaryTextColor = txt2,
                                    isDarkMode         = dark
                                )
                            }
                            item {
                                AccelerometerAngleDisplay(
                                    xAxis              = accX,
                                    yAxis              = accY,
                                    zAxis              = accZ,
                                    cardBackground     = cardBg,
                                    textColor          = txt,
                                    secondaryTextColor = txt2,
                                    isDarkMode         = dark
                                )
                            }
                        }
                        // ---- SOIL --------------------------------------------------
                        if (sensorData is BluetoothScanViewModel.SensorData.SoilSensorData) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { soilClicked = true },
                                    elevation = 2.dp,
                                    backgroundColor = if (soilClicked) accent.copy(alpha = 0.1f) else cardBg
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(soilTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.padding(bottom = 8.dp))
                                        SensorGraphCard(soilMoistLabel, soilM, soilMH, Color(0xFF6200EA), cardBg, txt, txt2, currentTxt, naTxt, dark)
                                        Spacer(Modifier.height(16.dp))
                                        SensorGraphCard(soilTempLabel, soilT, soilTH, Color(0xFFFF6D00), cardBg, txt, txt2, currentTxt, naTxt, dark)

                                        if (!soilClicked) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                                horizontalArrangement = Arrangement.Center
                                            ) { Text(clickAll, color = accent) }
                                        } else {
                                            Spacer(Modifier.height(16.dp))
                                            SensorGraphCard(soilNLabel, soilN, soilNH, Color(0xFF00897B), cardBg, txt, txt2, currentTxt, naTxt, dark)
                                            Spacer(Modifier.height(16.dp))
                                            SensorGraphCard(soilPLabel, soilP, soilPHist, Color(0xFFC2185B), cardBg, txt, txt2, currentTxt, naTxt, dark)
                                            Spacer(Modifier.height(16.dp))
                                            SensorGraphCard(soilKLabel, soilK, soilKHist, Color(0xFF7B1FA2), cardBg, txt, txt2, currentTxt, naTxt, dark)
                                            Spacer(Modifier.height(16.dp))
                                            SensorGraphCard(soilECLabel, soilEC, soilECHist, Color(0xFFF57C00), cardBg, txt, txt2, currentTxt, naTxt, dark)
                                            Spacer(Modifier.height(16.dp))
                                            SensorGraphCard(soilPHLabel, soilPH, soilPHHist, Color(0xFFD32F2F), cardBg, txt, txt2, currentTxt, naTxt, dark)
                                        }
                                    }
                                }
                            }
                        }

                        // ---- data-flow monitor ----------------------------------------
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                backgroundColor = if (dark) Color(0xFF2A2A2A) else Color(0xFFE3F2FD)
                            ) {
                                Text(
                                    text = flowState.value,
                                    color = if (dark) Color.White else Color.Black,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        // ---- no data yet ---------------------------------------------
                        if (!receiving.value) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(waitingSensor, modifier = Modifier.padding(vertical = 32.dp), color = txt)
                                    Text(ensureConnected, fontSize = 14.sp, color = txt2, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                } else if (soilClicked && tabIdx == 1) {
                    SoilSensorDataTable(
                        soilMoistureHistory = soilMH,
                        soilTemperatureHistory = soilTH,
                        soilNitrogenHistory = soilNH,
                        soilPhosphorusHistory = soilPHist,
                        soilPotassiumHistory = soilKHist,
                        soilEcHistory = soilECHist,
                        soilPhHistory = soilPHHist,
                        timestamps = timestamps,
                        isReceivingData = receiving.value && (soilMH.isNotEmpty() || soilTH.isNotEmpty() ||
                                soilNH.isNotEmpty() || soilPHist.isNotEmpty() || soilKHist.isNotEmpty() ||
                                soilECHist.isNotEmpty() || soilPHHist.isNotEmpty()),
                        soilMoistureLabel = soilMoistLabel,
                        soilTemperatureLabel = soilTempLabel,
                        soilNitrogenLabel = soilNLabel,
                        soilPhosphorusLabel = soilPLabel,
                        soilPotassiumLabel = soilKLabel,
                        soilEcLabel = soilECLabel,
                        soilPhLabel = soilPHLabel,
                        waitingForSensorData = waitingSensor,
                        textColor = txt,
                        secondaryTextColor = txt2,
                        cardBackground = cardBg
                    )
                }

                // ---- back button when soil view is expanded --------------------
                if (soilClicked) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .background(accent, RoundedCornerShape(8.dp))
                                .clickable { soilClicked = false }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(backToSensors, color = if (dark) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------
   Graph card (touch-enabled)
   ------------------------------------------------------------- */
@Composable
fun SensorGraphCard(
    title: String,
    cur: Float?,
    hist: List<Float>,
    lineCol: Color,
    cardBg: Color,
    txtCol: Color,
    txt2Col: Color,
    curLabel: String,
    naLabel: String,
    dark: Boolean
) {
    var tapPos by remember { mutableStateOf<Offset?>(null) }
    var tapVal by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(tapPos) {
        if (tapPos != null) {
            delay(1500)
            tapPos = null
            tapVal = null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        elevation = 4.dp,
        backgroundColor = cardBg
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = txtCol,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "$curLabel: ${cur?.let { "%.2f".format(it) } ?: naLabel}",
                fontSize = 15.sp,
                color = txt2Col,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))

            if (hist.isNotEmpty()) {
                var canvasSize by remember { mutableStateOf(Size.Zero) }

                Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) { detectTapGestures { tapPos = it } }
                    ) {
                        canvasSize = size
                        val pts = hist
                        if (pts.isNotEmpty()) {
                            val lm = 50f
                            val rm = 16f
                            val tm = 24f
                            val bm = 36f
                            val w = size.width - lm - rm
                            val h = size.height - tm - bm
                            val sx = lm
                            val sy = tm
                            val ex = sx + w
                            val ey = sy + h

                            // Updated: -15 to +15 (accelerometer ke ±10 ke around comfortable margin)
                            val yMin = -20f
                            val yMax = 20f
                            val yRng = yMax - yMin

                            val stepX = w / (pts.size.coerceAtLeast(2) - 1).toFloat()

                            // Background
                            drawRect(if (dark) Color(0x0AFFFFFF) else Color(0x0A000000), Offset(sx, sy), Size(w, h))

                            // Grid lines
                            val grid = txt2Col.copy(alpha = 0.12f)
                            for (v in -15..15 step 5) {
                                val y = sy + h * (1 - (v.toFloat() - yMin) / yRng)
                                drawLine(color = grid, start = Offset(sx, y), end = Offset(ex, y), strokeWidth = 0.8f)
                            }
                            for (i in 0..pts.size step 10) {
                                if (i < pts.size) {
                                    val x = sx + i * stepX
                                    drawLine(color = grid, start = Offset(x, sy), end = Offset(x, ey), strokeWidth = 0.8f)
                                }
                            }

                            // Bold zero line
                            val zeroY = sy + h * (1 - (0f - yMin) / yRng)
                            drawLine(
                                color = txt2Col.copy(alpha = 0.5f),
                                start = Offset(sx, zeroY),
                                end = Offset(ex, zeroY),
                                strokeWidth = 2f
                            )

                            // Border
                            drawRect(txt2Col.copy(alpha = 0.35f), Offset(sx, sy), Size(w, h), style = Stroke(1.2f))

                            // Y labels
                            listOf(-20f,-15f, -10f, -5f, 0f, 5f, 10f, 15f,20f).forEach {
                                val y = sy + h * (1 - (it - yMin) / yRng)
                                val p = android.graphics.Paint().apply {
                                    color = android.graphics.Color.argb(
                                        (txt2Col.alpha * 255).toInt(),
                                        (txt2Col.red * 255).toInt(),
                                        (txt2Col.green * 255).toInt(),
                                        (txt2Col.blue * 255).toInt()
                                    )
                                    textSize = 18f
                                    textAlign = android.graphics.Paint.Align.RIGHT
                                }
                                drawContext.canvas.nativeCanvas.drawText(
                                    "%.0f".format(it),
                                    sx - 28f,
                                    y + 6f,
                                    p
                                )
                            }

                            // X labels
                            listOf(0, 10, 20, 30, 40).forEach { idx ->
                                if (idx < pts.size) {
                                    val x = sx + idx * stepX
                                    val p = android.graphics.Paint().apply {
                                        color = android.graphics.Color.argb(
                                            (txt2Col.alpha * 255).toInt(),
                                            (txt2Col.red * 255).toInt(),
                                            (txt2Col.green * 255).toInt(),
                                            (txt2Col.blue * 255).toInt()
                                        )
                                        textSize = 16f
                                        textAlign = android.graphics.Paint.Align.CENTER
                                    }
                                    drawContext.canvas.nativeCanvas.drawText(
                                        idx.toString(),
                                        x,
                                        ey + 18f,
                                        p
                                    )
                                }
                            }

                            // Main line – thickness badha di
                            val path = Path().apply {
                                val firstY = sy + h * (1 - (pts[0] - yMin) / yRng)
                                moveTo(sx, firstY)
                                for (i in 1 until pts.size) {
                                    val x = sx + i * stepX
                                    val y = sy + h * (1 - (pts[i] - yMin) / yRng)
                                    lineTo(x, y)
                                }
                            }
                            drawPath(
                                path,
                                lineCol,
                                style = Stroke(
                                    width = 3.8f,           // ← yahan thickness badha di (2.5 → 3.8)
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            // Latest point bhi thoda bada
                            val li = pts.size - 1
                            val lx = sx + li * stepX
                            val ly = sy + h * (1 - (pts.last() - yMin) / yRng)
                            drawCircle(lineCol, 5.5f, Offset(lx, ly))   // ← point bhi thoda visible

                            // Tap
                            tapPos?.let { tp ->
                                val step = w / (pts.size.coerceAtLeast(2) - 1).toFloat()
                                val idx = ((tp.x - lm) / step).toInt().coerceIn(0, pts.size - 1)
                                val tx = sx + idx * stepX
                                val ty = sy + h * (1 - (pts[idx] - yMin) / yRng)
                                tapVal = pts[idx]

                                drawLine(
                                    color = lineCol.copy(alpha = 0.6f),
                                    start = Offset(tx, sy),
                                    end = Offset(tx, ey),
                                    strokeWidth = 1.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                                )
                                drawCircle(lineCol, 8f, Offset(tx, ty))

                                val p = android.graphics.Paint().apply {
                                    color = android.graphics.Color.argb(
                                        (lineCol.alpha * 255).toInt(),
                                        (lineCol.red * 255).toInt(),
                                        (lineCol.green * 255).toInt(),
                                        (lineCol.blue * 255).toInt()
                                    )
                                    textSize = 23f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    setShadowLayer(5f, 2f, 2f, android.graphics.Color.BLACK)
                                }
                                drawContext.canvas.nativeCanvas.drawText(
                                    "%.2f".format(pts[idx]),
                                    tx,
                                    ty - 22f,
                                    p
                                )
                            }
                        }
                    }
                }

                tapVal?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Touched: %.2f".format(it),
                        fontSize = 13.sp,
                        color = lineCol,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Spacer(Modifier.height(40.dp))
                Text(
                    "Waiting for data...",
                    color = txtCol.copy(alpha = 0.7f),
                    fontSize = 15.sp
                )
            }
        }
    }
}
/* -------------------------------------------------------------
   Helper – keep history at max 50 points
   ------------------------------------------------------------- */
private fun updateHistory(list: MutableList<Float>, v: Float) {
    if (list.size >= 50) list.removeAt(0)
    list.add(v)
}

/* -------------------------------------------------------------
   Soil table (tab 1)
   ------------------------------------------------------------- */
@Composable
fun SoilSensorDataTable(
    soilMoistureHistory: List<Float>,
    soilTemperatureHistory: List<Float>,
    soilNitrogenHistory: List<Float>,
    soilPhosphorusHistory: List<Float>,
    soilPotassiumHistory: List<Float>,
    soilEcHistory: List<Float>,
    soilPhHistory: List<Float>,
    timestamps: List<String>,
    isReceivingData: Boolean,
    soilMoistureLabel: String,
    soilTemperatureLabel: String,
    soilNitrogenLabel: String,
    soilPhosphorusLabel: String,
    soilPotassiumLabel: String,
    soilEcLabel: String,
    soilPhLabel: String,
    waitingForSensorData: String,
    textColor: Color,
    secondaryTextColor: Color,
    cardBackground: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        elevation = 4.dp,
        backgroundColor = cardBackground
    ) {
        Column(Modifier.padding(16.dp)) {
            if (isReceivingData) {
                LazyColumn {
                    items(timestamps.size) { i ->
                        Column {
                            Text("Timestamp: ${timestamps.getOrNull(i) ?: "-"}", color = textColor, fontWeight = FontWeight.Bold)
                            Text("$soilMoistureLabel: ${soilMoistureHistory.getOrNull(i) ?: "-"}", color = secondaryTextColor)
                            Text("$soilTemperatureLabel: ${soilTemperatureHistory.getOrNull(i) ?: "-"}", color = secondaryTextColor)
                            Text("$soilNitrogenLabel: ${soilNitrogenHistory.getOrNull(i) ?: "-"}", color = secondaryTextColor)
                            Text("$soilPhosphorusLabel: ${soilPhosphorusHistory.getOrNull(i) ?: "-"}", color = secondaryTextColor)
                            Text("$soilPotassiumLabel: ${soilPotassiumHistory.getOrNull(i) ?: "-"}", color = secondaryTextColor)
                            Text("$soilEcLabel: ${soilEcHistory.getOrNull(i) ?: "-"}", color = secondaryTextColor)
                            Text("$soilPhLabel: ${soilPhHistory.getOrNull(i) ?: "-"}", color = secondaryTextColor)
                            Divider(color = secondaryTextColor.copy(alpha = 0.2f), thickness = 1.dp)
                        }
                    }
                }
            } else {
                Text(waitingForSensorData, modifier = Modifier.padding(vertical = 32.dp), color = textColor)
            }
        }
    }
}

/* -------------------------------------------------------------
   3-D accelerometer cube
   ------------------------------------------------------------- */
@Composable
fun Accelerometer3DVisualization(
    xAxis: Float?,
    yAxis: Float?,
    zAxis: Float?,
    cardBackground: Color,
    textColor: Color,
    isDarkMode: Boolean
) {
    val x = xAxis ?: 0f
    val y = yAxis ?: 0f
    val z = zAxis ?: 0f

    Card(
        modifier = Modifier.fillMaxWidth().height(380.dp),
        elevation = 4.dp,
        backgroundColor = cardBackground
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "3D Orientation Visualizer",
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Canvas(modifier = Modifier.fillMaxWidth().height(280.dp).padding(8.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val scale = minOf(size.width, size.height) * 0.25f
                val axisLen = 300f
                val diag = axisLen / sqrt(2f)
                val edge = if (isDarkMode) Color.White else Color.Black

                // ----- cube vertices & edges (same as helper) -----
                val verts = arrayOf(
                    floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
                    floatArrayOf(1f, 1f, -1f),   floatArrayOf(-1f, 1f, -1f),
                    floatArrayOf(-1f, -1f, 1f),  floatArrayOf(1f, -1f, 1f),
                    floatArrayOf(1f, 1f, 1f),   floatArrayOf(-1f, 1f, 1f)
                )
                val edges = arrayOf(
                    intArrayOf(0,1), intArrayOf(1,2), intArrayOf(2,3), intArrayOf(3,0),
                    intArrayOf(4,5), intArrayOf(5,6), intArrayOf(6,7), intArrayOf(7,4),
                    intArrayOf(0,4), intArrayOf(1,5), intArrayOf(2,6), intArrayOf(3,7)
                )

                fun rot(x: Float, y: Float, z: Float): FloatArray {
                    val rx = Math.toRadians(20.0).toFloat()
                    val ry = Math.toRadians(25.0).toFloat()
                    val rz = Math.toRadians(5.0).toFloat()

                    var yy = y * cos(rx) - z * sin(rx)
                    var zz = y * sin(rx) + z * cos(rx)
                    var xx = x

                    val zz2 = zz * cos(ry) - xx * sin(ry)
                    val xx2 = zz * sin(ry) + xx * cos(ry)

                    val xx3 = xx2 * cos(rz) - yy * sin(rz)
                    val yy3 = xx2 * sin(rz) + yy * cos(rz)
                    return floatArrayOf(xx3, yy3, zz2)
                }

                fun proj(x: Float, y: Float, z: Float): Offset {
                    val d = 5f
                    val p = d / (d - z)
                    return Offset(cx + x * scale * p, cy - y * scale * p)
                }

                val projected = verts.map {
                    val r = rot(it[0], it[1], it[2])
                    proj(r[0], r[1], r[2])
                }

                edges.forEach { (a, b) -> drawLine(color = edge, start = projected[a], end = projected[b], strokeWidth = 3f) }
                projected.forEach { drawCircle(edge, 4f, it) }

                // ----- axes -------------------------------------------------
                val xEnd = Offset(cx + axisLen, cy)
                val yEnd = Offset(cx, cy - axisLen)
                val zEnd = Offset(cx - diag, cy + diag)

                drawLine(color = Color.Red,   start = Offset(cx, cy), end = xEnd, strokeWidth = 3f)
                drawLine(color = Color.Green, start = Offset(cx, cy), end = yEnd, strokeWidth = 3f)
                drawLine(color = Color.Cyan,  start = Offset(cx, cy), end = zEnd, strokeWidth = 3f)

                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply { textSize = 36f; isAntiAlias = true }
                    p.color = android.graphics.Color.RED;   drawText("X", xEnd.x + 10f, xEnd.y, p)
                    p.color = android.graphics.Color.GREEN; drawText("Y", yEnd.x + 10f, yEnd.y, p)
                    p.color = android.graphics.Color.CYAN;  drawText("Z", zEnd.x + 20f, zEnd.y + 30f, p)
                }

                // ----- sensor dot --------------------------------------------
                val range = 10f
                val sx = (x / range).coerceIn(-1f, 1f)
                val sy = (y / range).coerceIn(-1f, 1f)
                val sz = (z / range).coerceIn(-1f, 1f)

                val r = rot(sx, sy, sz)
                val dot = proj(r[0], r[1], r[2])
                drawCircle(Color.Magenta, 10f, dot)
            }

            Text(
                "X: %.2f, Y: %.2f, Z: %.2f".format(x, y, z),
                color = textColor,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/* -------------------------------------------------------------
   Angle / Tilt visualizer for LIS2DH / LIS3DH
   ------------------------------------------------------------- */
@Composable
fun AccelerometerAngleDisplay(
    xAxis: Float?,
    yAxis: Float?,
    zAxis: Float?,
    cardBackground: Color,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkMode: Boolean
) {
    val x = xAxis ?: 0f
    val y = yAxis ?: 0f
    val z = zAxis ?: 0f

    // --- compute angles using atan2 ---
    // Roll  = rotation around X axis  (tilt left/right)
    // Pitch = rotation around Y axis  (tilt forward/back)
    // Yaw approximation from X/Y plane (not absolute without magnetometer)
    val roll  = Math.toDegrees(kotlin.math.atan2(y.toDouble(), z.toDouble())).toFloat()
    val pitch = Math.toDegrees(kotlin.math.atan2((-x).toDouble(),
        kotlin.math.sqrt((y * y + z * z).toDouble()))).toFloat()
    val yawApprox = Math.toDegrees(kotlin.math.atan2(x.toDouble(), y.toDouble())).toFloat()

    // Normalize roll/pitch to -180..180 already by atan2
    // Clamp for display
    val rollD  = roll.coerceIn(-180f, 180f)
    val pitchD = pitch.coerceIn(-90f, 90f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        elevation = 4.dp,
        backgroundColor = cardBackground
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Tilt / Angle Monitor",
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))

            // --- Angle values row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AngleValueBox("Roll",  rollD,  Color(0xFFE91E63), textColor, secondaryTextColor)
                AngleValueBox("Pitch", pitchD, Color(0xFF2196F3), textColor, secondaryTextColor)
                AngleValueBox("Yaw~",  yawApprox, Color(0xFF4CAF50), textColor, secondaryTextColor)
            }

            Spacer(Modifier.height(16.dp))

            // --- Side-by-side gauge canvases ---
            Row(
                modifier = Modifier.fillMaxWidth().height(170.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Roll gauge
                Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    drawAngleGauge(
                        scope      = this,
                        angleDeg   = rollD,
                        label      = "Roll",
                        color      = Color(0xFFE91E63),
                        bgColor    = if (isDarkMode) Color(0x1AFFFFFF) else Color(0x1A000000),
                        textColor  = textColor,
                        rangeMin   = -180f,
                        rangeMax   = 180f
                    )
                }
                // Pitch gauge
                Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    drawAngleGauge(
                        scope      = this,
                        angleDeg   = pitchD,
                        label      = "Pitch",
                        color      = Color(0xFF2196F3),
                        bgColor    = if (isDarkMode) Color(0x1AFFFFFF) else Color(0x1A000000),
                        textColor  = textColor,
                        rangeMin   = -90f,
                        rangeMax   = 90f
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Note: Yaw (~) is approximate without magnetometer",
                color = secondaryTextColor,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/* --- small value box --- */
@Composable
private fun AngleValueBox(
    name: String,
    value: Float,
    color: Color,
    textColor: Color,
    secondaryColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, fontSize = 13.sp, color = secondaryColor, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "%.1f°".format(value),
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/* --- semicircle gauge drawn on Canvas --- */
private fun drawAngleGauge(
    scope: DrawScope,
    angleDeg: Float,
    label: String,
    color: Color,
    bgColor: Color,
    textColor: Color,
    rangeMin: Float,
    rangeMax: Float
) {
    with(scope) {
        val cx     = size.width / 2f
        val cy     = size.height * 0.72f          // push arc toward bottom so label fits
        val radius = minOf(size.width, size.height) * 0.42f

        // Background arc (grey track) – full 180°
        drawArc(
            color      = bgColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter  = false,
            topLeft    = Offset(cx - radius, cy - radius),
            size       = Size(radius * 2, radius * 2),
            style      = Stroke(width = 18f, cap = StrokeCap.Round)
        )

        // Colored fill arc proportional to angle
        val fraction  = (angleDeg - rangeMin) / (rangeMax - rangeMin)   // 0..1
        val sweep     = (fraction * 180f).coerceIn(0f, 180f)
        if (sweep > 0f) {
            drawArc(
                color      = color,
                startAngle = 180f,
                sweepAngle = sweep,
                useCenter  = false,
                topLeft    = Offset(cx - radius, cy - radius),
                size       = Size(radius * 2, radius * 2),
                style      = Stroke(width = 18f, cap = StrokeCap.Round)
            )
        }

        // Needle
        val needleAngleDeg = 180f + fraction * 180f         // maps 0..1 → 180°..360°
        val needleRad      = Math.toRadians(needleAngleDeg.toDouble())
        val needleTip = Offset(
            cx + (radius * cos(needleRad)).toFloat(),
            cy + (radius * sin(needleRad)).toFloat()
        )
        drawLine(
            color       = color,
            start       = Offset(cx, cy),
            end         = needleTip,
            strokeWidth = 4f,
            cap         = StrokeCap.Round
        )
        drawCircle(color, 7f, Offset(cx, cy))   // pivot dot

        // Min / max labels on arc ends
        val labelPaint = android.graphics.Paint().apply {
            textSize    = 22f
            isAntiAlias = true
            this.color  = android.graphics.Color.argb(
                (textColor.alpha * 255).toInt(),
                (textColor.red   * 255).toInt(),
                (textColor.green * 255).toInt(),
                (textColor.blue  * 255).toInt()
            )
            textAlign   = android.graphics.Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.apply {
            drawText("${rangeMin.toInt()}°", cx - radius - 4f, cy + 26f, labelPaint)
            drawText("${rangeMax.toInt()}°", cx + radius + 4f, cy + 26f, labelPaint)
            // Center angle value
            val bigPaint = android.graphics.Paint().apply {
                textSize    = 34f
                isAntiAlias = true
                isFakeBoldText = true
                this.color  = android.graphics.Color.argb(
                    (color.alpha * 255).toInt(),
                    (color.red   * 255).toInt(),
                    (color.green * 255).toInt(),
                    (color.blue  * 255).toInt()
                )
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawText("%.1f°".format(angleDeg), cx, cy + 52f, bigPaint)
            // Label
            val lblPaint = android.graphics.Paint().apply {
                textSize    = 24f
                isAntiAlias = true
                this.color  = android.graphics.Color.argb(
                    (textColor.alpha * 255).toInt(),
                    (textColor.red   * 255).toInt(),
                    (textColor.green * 255).toInt(),
                    (textColor.blue  * 255).toInt()
                )
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawText(label, cx, cy - radius - 10f, lblPaint)
        }
    }
}
/* -------------------------------------------------------------
   BLE Node Visualizer – replaces 3D cube
   Rotates/tilts in real-time based on accelerometer axes
   ------------------------------------------------------------- */
@Composable
fun BleNodeVisualizer(
    xAxis: Float?,
    yAxis: Float?,
    zAxis: Float?,
    cardBackground: Color,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkMode: Boolean
) {
    val x = xAxis ?: 0f
    val y = yAxis ?: 0f
    val z = zAxis ?: 0f

    val animRoll by animateFloatAsState(
        targetValue = Math.toDegrees(kotlin.math.atan2(y.toDouble(), z.toDouble())).toFloat(),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 80f),
        label = "roll"
    )
    val animPitch by animateFloatAsState(
        targetValue = Math.toDegrees(kotlin.math.atan2(
            (-x).toDouble(), kotlin.math.sqrt((y * y + z * z).toDouble())
        )).toFloat(),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 80f),
        label = "pitch"
    )
    val animYaw by animateFloatAsState(
        targetValue = Math.toDegrees(kotlin.math.atan2(x.toDouble(), y.toDouble())).toFloat(),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 80f),
        label = "yaw"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue  = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    val accentBlue  = Color(0xFF2979FF)
    val accentCyan  = Color(0xFF00E5FF)
    val nodeBody    = if (isDarkMode) Color(0xFF1A237E) else Color(0xFF1565C0)
    val nodeSurface = if (isDarkMode) Color(0xFF283593) else Color(0xFF1976D2)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp),
        elevation = 6.dp,
        shape = RoundedCornerShape(16.dp),
        backgroundColor = cardBackground
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header row with blinking BLE dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            accentBlue.copy(alpha = blinkAlpha),
                            androidx.compose.foundation.shape.CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "BLE Node Orientation",
                    color = textColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(4.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                val rollRad  = Math.toRadians(animRoll.toDouble())
                val pitchRad = Math.toRadians(animPitch.toDouble())
                val yawRad   = Math.toRadians(animYaw.toDouble())

                fun project(px: Float, py: Float, pz: Float): Offset {
                    // Pitch (X rotation)
                    val y1 = (py * cos(pitchRad) - pz * sin(pitchRad)).toFloat()
                    val z1 = (py * sin(pitchRad) + pz * cos(pitchRad)).toFloat()
                    // Roll (Z rotation)
                    val x2 = (px * cos(rollRad) - y1 * sin(rollRad)).toFloat()
                    val y2 = (px * sin(rollRad) + y1 * cos(rollRad)).toFloat()
                    // Yaw (Y rotation)
                    val x3 = (x2 * cos(yawRad) + z1 * sin(yawRad)).toFloat()
                    val z3 = (-x2 * sin(yawRad) + z1 * cos(yawRad)).toFloat()
                    // Perspective divide
                    val d = 5f
                    val scale = 130f
                    val persp = d / (d - z3 * 0.5f)
                    return Offset(cx + x3 * scale * persp, cy - y2 * scale * persp)
                }

                // ---- Pulse rings ----
                for (i in 1..3) {
                    val r = 38f + i * 22f * pulseScale
                    drawCircle(
                        color  = accentBlue.copy(alpha = pulseAlpha * (1f - i * 0.25f)),
                        radius = r,
                        center = Offset(cx, cy),
                        style  = Stroke(width = 2.5f)
                    )
                }

                // ---- Ground shadow ----
                drawOval(
                    color   = Color.Black.copy(alpha = 0.18f),
                    topLeft = Offset(cx - 80f, cy + 85f),
                    size    = Size(160f, 28f)
                )

                // ---- PCB Board ----
                val boardW = 1.35f
                val boardH = 0.09f
                val boardD = 0.90f

                val boardBottom = listOf(
                    project(-boardW, -boardH, -boardD),
                    project( boardW, -boardH, -boardD),
                    project( boardW, -boardH,  boardD),
                    project(-boardW, -boardH,  boardD)
                )
                val boardTop = listOf(
                    project(-boardW,  boardH, -boardD),
                    project( boardW,  boardH, -boardD),
                    project( boardW,  boardH,  boardD),
                    project(-boardW,  boardH,  boardD)
                )

                // Bottom face
                val boardBotPath = Path().apply {
                    moveTo(boardBottom[0].x, boardBottom[0].y)
                    boardBottom.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(boardBotPath, nodeBody)

                // Top face (PCB green)
                val boardTopPath = Path().apply {
                    moveTo(boardTop[0].x, boardTop[0].y)
                    boardTop.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(boardTopPath, if (isDarkMode) Color(0xFF1B5E20) else Color(0xFF2E7D32))
                drawPath(boardTopPath, accentCyan.copy(alpha = 0.12f), style = Stroke(1.5f))

                // Side edges
                for (i in 0..3) {
                    val next = (i + 1) % 4
                    val sidePath = Path().apply {
                        moveTo(boardBottom[i].x, boardBottom[i].y)
                        lineTo(boardBottom[next].x, boardBottom[next].y)
                        lineTo(boardTop[next].x, boardTop[next].y)
                        lineTo(boardTop[i].x, boardTop[i].y)
                        close()
                    }
                    drawPath(sidePath, nodeSurface.copy(alpha = 0.7f))
                }

                // ---- PCB trace lines ----
                val traceColor = accentCyan.copy(alpha = 0.25f)
                for (i in -3..3) {
                    val t1 = project(i * 0.20f, boardH + 0.005f, -boardD + 0.05f)
                    val t2 = project(i * 0.20f, boardH + 0.005f,  boardD - 0.05f)
                    drawLine(traceColor, t1, t2, strokeWidth = 1f)
                }
                // Horizontal traces
                for (i in -2..2) {
                    val t1 = project(-boardW + 0.05f, boardH + 0.005f, i * 0.28f)
                    val t2 = project( boardW - 0.05f, boardH + 0.005f, i * 0.28f)
                    drawLine(traceColor, t1, t2, strokeWidth = 1f)
                }

                // ---- Main Chip ----
                val chipCorners = listOf(
                    project(-0.38f, boardH + 0.01f, -0.24f),
                    project( 0.38f, boardH + 0.01f, -0.24f),
                    project( 0.38f, boardH + 0.01f,  0.24f),
                    project(-0.38f, boardH + 0.01f,  0.24f)
                )
                val chipPath = Path().apply {
                    moveTo(chipCorners[0].x, chipCorners[0].y)
                    chipCorners.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(chipPath, if (isDarkMode) Color(0xFF212121) else Color(0xFF37474F))
                drawPath(chipPath, accentCyan.copy(alpha = 0.3f), style = Stroke(1.5f))
                // Chip cross lines
                drawLine(
                    color = accentCyan.copy(alpha = 0.4f),
                    start = Offset(
                        (chipCorners[0].x + chipCorners[1].x) / 2f,
                        (chipCorners[0].y + chipCorners[1].y) / 2f
                    ),
                    end = Offset(
                        (chipCorners[2].x + chipCorners[3].x) / 2f,
                        (chipCorners[2].y + chipCorners[3].y) / 2f
                    ),
                    strokeWidth = 1f
                )
                drawLine(
                    color = accentCyan.copy(alpha = 0.4f),
                    start = Offset(
                        (chipCorners[0].x + chipCorners[3].x) / 2f,
                        (chipCorners[0].y + chipCorners[3].y) / 2f
                    ),
                    end = Offset(
                        (chipCorners[1].x + chipCorners[2].x) / 2f,
                        (chipCorners[1].y + chipCorners[2].y) / 2f
                    ),
                    strokeWidth = 1f
                )

                // ---- Small secondary chip ----
                val chip2Corners = listOf(
                    project(-1.05f, boardH + 0.01f, -0.18f),
                    project(-0.60f, boardH + 0.01f, -0.18f),
                    project(-0.60f, boardH + 0.01f,  0.18f),
                    project(-1.05f, boardH + 0.01f,  0.18f)
                )
                val chip2Path = Path().apply {
                    moveTo(chip2Corners[0].x, chip2Corners[0].y)
                    chip2Corners.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(chip2Path, if (isDarkMode) Color(0xFF263238) else Color(0xFF455A64))
                drawPath(chip2Path, accentCyan.copy(alpha = 0.2f), style = Stroke(1f))

                // ---- Capacitors (small squares) ----
                listOf(
                    Triple( 0.60f, boardH + 0.01f,  0.55f),
                    Triple( 0.85f, boardH + 0.01f,  0.55f),
                    Triple( 0.60f, boardH + 0.01f,  0.70f),
                    Triple( 0.85f, boardH + 0.01f,  0.70f)
                ).forEach { (cx2, cy2, cz2) ->
                    val capCorners = listOf(
                        project(cx2 - 0.06f, cy2, cz2 - 0.05f),
                        project(cx2 + 0.06f, cy2, cz2 - 0.05f),
                        project(cx2 + 0.06f, cy2, cz2 + 0.05f),
                        project(cx2 - 0.06f, cy2, cz2 + 0.05f)
                    )
                    val capPath = Path().apply {
                        moveTo(capCorners[0].x, capCorners[0].y)
                        capCorners.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                    }
                    drawPath(capPath, Color(0xFFB8860B))
                    drawPath(capPath, Color(0xFFFFD700).copy(alpha = 0.4f), style = Stroke(1f))
                }

                // ---- LED dot ----
                val ledPos = project(0.88f, boardH + 0.02f, -0.62f)
                drawCircle(Color(0xFF76FF03).copy(alpha = 0.85f + 0.15f * pulseAlpha), 7f, ledPos)
                drawCircle(Color(0xFF76FF03).copy(alpha = 0.3f), 13f, ledPos, style = Stroke(2f))
                drawCircle(Color(0xFF76FF03).copy(alpha = pulseAlpha * 0.4f), 20f * pulseScale * 0.4f, ledPos)

                // ---- Antenna ----
                val antBase = project(1.10f, boardH,        -0.75f)
                val antTip  = project(1.10f, boardH + 0.7f, -0.75f)
                // Antenna body
                drawLine(accentCyan.copy(alpha = 0.9f), antBase, antTip, strokeWidth = 3.5f, cap = StrokeCap.Round)
                // Antenna tip glow
                drawCircle(accentCyan, 6f, antTip)
                drawCircle(accentCyan.copy(alpha = 0.3f), 12f, antTip, style = Stroke(2f))

                // ---- BLE signal waves from antenna ----
                for (i in 1..3) {
                    val waveAlpha = (pulseAlpha * (1f - i * 0.28f)).coerceIn(0f, 1f)
                    drawCircle(
                        color  = accentBlue.copy(alpha = waveAlpha),
                        radius = 12f + i * 16f * pulseScale * 0.5f,
                        center = antTip,
                        style  = Stroke(width = 2f)
                    )
                }

                // ---- Connector pins row ----
                for (i in 0..5) {
                    val pinX = -boardW + 0.12f + i * 0.22f
                    val pinTop = project(pinX, boardH + 0.01f, boardD - 0.04f)
                    val pinBot = project(pinX, boardH + 0.01f, boardD + 0.12f)
                    drawLine(
                        color = Color(0xFFB0BEC5),
                        start = pinTop,
                        end   = pinBot,
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(Color(0xFFCFD8DC), 4f, pinBot)
                }

                // ---- Axis arrows ----
                val originPt = project(0f, 0f, 0f)
                val xTip = project(1.6f, 0f, 0f)
                val yTip = project(0f, 1.6f, 0f)
                val zTip = project(0f, 0f, 1.6f)

                drawLine(Color.Red.copy(alpha = 0.85f),   originPt, xTip, strokeWidth = 2.5f, cap = StrokeCap.Round)
                drawLine(Color.Green.copy(alpha = 0.85f), originPt, yTip, strokeWidth = 2.5f, cap = StrokeCap.Round)
                drawLine(Color.Cyan.copy(alpha = 0.85f),  originPt, zTip, strokeWidth = 2.5f, cap = StrokeCap.Round)

                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply {
                        textSize = 26f; isAntiAlias = true; isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    p.color = android.graphics.Color.RED;   drawText("X", xTip.x + 14f, xTip.y, p)
                    p.color = android.graphics.Color.GREEN; drawText("Y", yTip.x + 14f, yTip.y, p)
                    p.color = android.graphics.Color.CYAN;  drawText("Z", zTip.x, zTip.y - 10f, p)
                }
            }

            // ---- Live angle readout strip ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isDarkMode) Color(0xFF0D1B2A) else Color(0xFFE3F2FD),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(
                    Triple("Roll",  animRoll,  Color(0xFFE91E63)),
                    Triple("Pitch", animPitch, Color(0xFF2196F3)),
                    Triple("Yaw~",  animYaw,   Color(0xFF4CAF50))
                ).forEach { (name, value, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(name, fontSize = 11.sp, color = secondaryTextColor)
                        Text(
                            "%.1f°".format(value),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
            }
        }
    }
}