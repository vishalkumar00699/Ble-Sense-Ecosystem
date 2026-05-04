package com.blesense.app

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

// ── Design tokens ──────────────────────────────────────────────────────────────
private val DarkBackground = Color(0xFF0A0A0F)
private val DarkSurface = Color(0xFF141418)
private val DarkCard = Color(0xFF1A1A22)
private val DarkDivider = Color(0xFF252530)
private val GreenAccent = Color(0xFF00BC7D)
private val GreenDark = Color(0xFF0D542B)
private val GreenMuted = Color(0xFF00BC7D26)
private val BlueAccent = Color(0xFF60A5FA)
private val YellowAccent = Color(0xFFFBBF24)
private val OrangeAccent = Color(0xFFFB923C)
private val PurpleAccent = Color(0xFFA78BFA)
private val TealAccent = Color(0xFF2DD4BF)
private val RedAccent = Color(0xFFFF6467)
private val TextPrimary = Color(0xFFF0F0F0)
private val TextSecondary = Color(0xFF9F9FA9)

data class NavDestination(
    val iconResId: Int,
    val label: String,
    val sublabel: String,
    val accentColor: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val route: String? = null,
    val intentClass: Class<*>? = null
)

@Composable
fun IntermediateScreen(
    navController: NavHostController,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {}
    )

    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot"
    )

    val destinations = listOf(
        NavDestination(
            iconResId = R.drawable.bluetooth,
            label = "Bluetooth",
            sublabel = "Scan & Connect",
            accentColor = BlueAccent,
            gradientStart = Color(0xFF1A2E50),
            gradientEnd = Color(0xFF111D38),
            route = "home_screen"
        ),
        NavDestination(
            iconResId = R.drawable.data_logger,
            label = "Data Logger",
            sublabel = "Record & Export",
            accentColor = GreenAccent,
            gradientStart = Color(0xFF0F2318),
            gradientEnd = Color(0xFF08160E),
            route = "data_logger/auto_connect/DataLogger/DataLogger_1"
        ),
        NavDestination(
            iconResId = R.drawable.robo_car_icon,
            label = "Robot Control",
            sublabel = "Drive & Steer",
            accentColor = PurpleAccent,
            gradientStart = Color(0xFF1E1235),
            gradientEnd = Color(0xFF140C24),
            route = "robot_screen"
        ),
        NavDestination(
            iconResId = R.drawable.error,
            label = "BLE Advertise",
            sublabel = "Broadcast Data",
            accentColor = YellowAccent,
            gradientStart = Color(0xFF261C06),
            gradientEnd = Color(0xFF1A1304),
            route = "advertiser_screen"
        ),
        NavDestination(
            iconResId = R.drawable.error,
            label = "Sensor Errors",
            sublabel = "Check Error Codes",
            accentColor = RedAccent,
            gradientStart = Color(0xFF2A0A0A),
            gradientEnd = Color(0xFF1A0505),
            route = "sensor_error_screen"
        ),
        NavDestination(
            iconResId = R.drawable.settings,
            label = "Settings",
            sublabel = "App Config",
            accentColor = OrangeAccent,
            gradientStart = Color(0xFF251208),
            gradientEnd = Color(0xFF190C05),
            route = "settings_screen"
        ),
        // ── UPDATED ANALYTICS CARD (BigAdv Scanner) ─────────────────────────────
        NavDestination(
            iconResId = R.drawable.data_logger,
            label = "BigAdv Scanner",
            sublabel = "Extended Adv + Raw Data",
            accentColor = TealAccent,
            gradientStart = Color(0xFF082424),
            gradientEnd = Color(0xFF051818),
            route = "bigadv_scanner_screen"
        )
    )

    Scaffold(
        modifier = Modifier.systemBarsPadding(),
        backgroundColor = DarkBackground,
        topBar = {
            TopAppBar(
                backgroundColor = DarkSurface,
                elevation = 0.dp
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(GreenAccent, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Sensors,
                                contentDescription = null,
                                tint = GreenDark,
                                modifier = Modifier.size(20.dp)
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
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .background(Color(0xFF1F2E1F), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(GreenAccent.copy(alpha = dotAlpha), CircleShape)
                        )
                        Text(
                            "Live",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GreenAccent
                        )
                    }
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
            item { IntermediateHeroBanner(dotAlpha = dotAlpha) }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Choose a Module",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Select a feature to get started",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            val rows = destinations.chunked(2)
            rows.forEach { rowDests ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowDests.forEach { dest ->
                            NavigationIconBox(
                                destination = dest,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (dest.intentClass != null) {
                                        launcher.launch(Intent(context, dest.intentClass))
                                    } else if (dest.route != null) {
                                        navController.navigate(dest.route)
                                    }
                                }
                            )
                        }
                        if (rowDests.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkCard, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        "QUICK STATUS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.0.sp,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        QuickStat(value = "4", label = "Devices", color = GreenAccent)
                        Box(modifier = Modifier.width(0.5.dp).height(40.dp).background(DarkDivider))
                        QuickStat(value = "3", label = "Active", color = BlueAccent)
                        Box(modifier = Modifier.width(0.5.dp).height(40.dp).background(DarkDivider))
                        QuickStat(value = "1,247", label = "Logged", color = YellowAccent)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkCard, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(GreenAccent.copy(alpha = dotAlpha), CircleShape))
                        Column {
                            Text("Auto-Scanning", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Background discovery enabled", fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(GreenMuted, RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("ON", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenAccent)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

// ── Baaki helper composables (same as before) ────────────────────────────────
@Composable
private fun IntermediateHeroBanner(dotAlpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0D2B1D), Color(0xFF0B1A28), Color(0xFF0A0A0F)))))
        Box(modifier = Modifier.size(180.dp).align(Alignment.CenterEnd).offset(x = 50.dp, y = 20.dp).background(GreenAccent.copy(alpha = 0.05f), CircleShape))
        Box(modifier = Modifier.size(100.dp).align(Alignment.BottomStart).offset(x = (-30).dp, y = 30.dp).background(BlueAccent.copy(alpha = 0.05f), CircleShape))

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(7.dp).background(GreenAccent.copy(alpha = dotAlpha), CircleShape))
                Text("CONNECTED & MONITORING", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GreenAccent, letterSpacing = 1.0.sp)
            }
            Column {
                Text("Sensor Control\nCenter", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 26.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("4 devices active · 12.4k readings today", fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun QuickStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 10.sp, color = TextSecondary)
    }
}

@Composable
fun NavigationIconBox(
    destination: NavDestination,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "scale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 0.dp,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "elev"
    )
    val cardColor by animateColorAsState(
        targetValue = if (isPressed) DarkCard.copy(alpha = 0.8f) else DarkCard,
        animationSpec = tween(150),
        label = "card"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "icon"
    )

    Card(
        modifier = modifier
            .aspectRatio(1.05f)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        val success = tryAwaitRelease()
                        isPressed = false
                        if (success) onClick()
                    }
                )
            },
        elevation = elevation,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = cardColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(destination.accentColor.copy(alpha = 0.07f), Color.Transparent))),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .scale(iconScale)
                        .background(Brush.linearGradient(listOf(destination.gradientStart, destination.gradientEnd)), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = destination.iconResId),
                        contentDescription = destination.label,
                        modifier = Modifier.size(26.dp),
                        tint = destination.accentColor
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = destination.label,
                    fontSize = 13.sp,
                    color = if (isPressed) TextPrimary.copy(alpha = 0.7f) else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = destination.sublabel,
                    fontSize = 9.sp,
                    color = if (isPressed) TextSecondary.copy(alpha = 0.6f) else TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}