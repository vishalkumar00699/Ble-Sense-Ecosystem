package com.blesense.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@Composable
fun AnalyticsScreen(navController: NavHostController) {
    val dark by ThemeManager.isDarkMode.collectAsState()

    val bgGrad = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF1E1235), Color(0xFF140C24)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF3F4F6), Color(0xFFE5E7EB)))
    }
    
    val cardBg = if (dark) Color(0xFF2A213B) else Color.White
    val txtCol = if (dark) Color.White else Color.Black
    val txt2Col = if (dark) Color(0xFFAAAAAA) else Color.Gray

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics Dashboard", color = txtCol, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = txtCol)
                    }
                },
                backgroundColor = if (dark) Color(0xFF1E1235) else Color.White,
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AnalyticsSummaryCard(cardBg, txtCol, txt2Col)
                }
                item {
                    LineChartCard(
                        title = "Monthly Activity",
                        dataPoints = listOf(10f, 30f, 25f, 50f, 40f, 80f, 60f, 90f),
                        lineColor = Color(0xFFBB86FC),
                        cardBg = cardBg,
                        txtCol = txtCol
                    )
                }
                item {
                    BarChartCard(
                        title = "Device Connections",
                        dataPoints = listOf(40f, 60f, 30f, 80f, 50f),
                        barColor = Color(0xFF03DAC5),
                        cardBg = cardBg,
                        txtCol = txtCol
                    )
                }
                item {
                    LineChartCard(
                        title = "Data Packets Received",
                        dataPoints = listOf(100f, 150f, 80f, 220f, 180f, 300f, 250f),
                        lineColor = Color(0xFFFF5252),
                        cardBg = cardBg,
                        txtCol = txtCol
                    )
                }
            }
        }
    }
}

@Composable
fun AnalyticsSummaryCard(cardBg: Color, txtCol: Color, txt2Col: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = cardBg,
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total Scans", color = txt2Col, fontSize = 12.sp)
                Text("1,248", color = txtCol, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Devices Found", color = txt2Col, fontSize = 12.sp)
                Text("342", color = txtCol, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Active Time", color = txt2Col, fontSize = 12.sp)
                Text("14h", color = txtCol, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LineChartCard(title: String, dataPoints: List<Float>, lineColor: Color, cardBg: Color, txtCol: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = cardBg,
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = txtCol, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(16.dp))
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val maxVal = dataPoints.maxOrNull()?.takeIf { it > 0 } ?: 100f
                val stepX = w / (dataPoints.size - 1).coerceAtLeast(1).toFloat()
                
                val path = Path().apply {
                    dataPoints.forEachIndexed { index, value ->
                        val x = index * stepX
                        val y = h - (value / maxVal * h)
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                
                dataPoints.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = h - (value / maxVal * h)
                    drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(x, y))
                    drawCircle(color = cardBg, radius = 2.5f.dp.toPx(), center = Offset(x, y))
                }
            }
        }
    }
}

@Composable
fun BarChartCard(title: String, dataPoints: List<Float>, barColor: Color, cardBg: Color, txtCol: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = cardBg,
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = txtCol, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(16.dp))
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val maxVal = dataPoints.maxOrNull()?.takeIf { it > 0 } ?: 100f
                val barWidth = (w / dataPoints.size) * 0.6f
                val spacing = (w / dataPoints.size)
                
                dataPoints.forEachIndexed { index, value ->
                    val x = (index * spacing) + (spacing - barWidth) / 2
                    val barHeight = (value / maxVal * h)
                    val y = h - barHeight
                    
                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight)
                    )
                }
            }
        }
    }
}
