package com.blesense.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily.Companion.Monospace
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blesense.app.ui.theme.BleSenseColors

@Composable
fun AnimatedFirstScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onGuestSignIn: () -> Unit
) {
    // ---- Animations ---------------------------------------------------
    val backgroundScale = remember { Animatable(0f) }
    val iconAlpha       = remember { Animatable(0f) }
    val iconTranslateY  = remember { Animatable(40f) }
    val textAlpha       = remember { Animatable(0f) }
    val textTranslateY  = remember { Animatable(20f) }
    val buttonAlpha     = remember { Animatable(0f) }
    val buttonTranslateY = remember { Animatable(20f) }
    val glowPulse       = remember { Animatable(0.6f) }

    LaunchedEffect(Unit) {
        backgroundScale.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        iconAlpha.animateTo(1f, tween(500))
        iconTranslateY.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
        textAlpha.animateTo(1f, tween(400))
        textTranslateY.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        buttonAlpha.animateTo(1f, tween(400))
        buttonTranslateY.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
    }

    // Pulsing glow for the icon
    LaunchedEffect(Unit) {
        glowPulse.animateTo(
            1f,
            infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse)
        )
    }

    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BleSenseColors.BackgroundDark)
            .systemBarsPadding()
    ) {
        // ---- Main content -----------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Spacer(modifier = Modifier.weight(1f))

            // ---- Icon with glowing ring ---------------------------------
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer(
                        alpha = iconAlpha.value,
                        translationY = iconTranslateY.value
                    )
            ) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size((160 * glowPulse.value).dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    BleSenseColors.PrimaryGreen.copy(alpha = 0.12f * glowPulse.value),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // Icon container
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(BleSenseColors.PrimaryGreenDark.copy(alpha = 0.4f))
                        .border(
                            1.dp,
                            BleSenseColors.PrimaryGreen.copy(alpha = 0.3f),
                            RoundedCornerShape(28.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.bg_remove_ble),
                        contentDescription = "BLE Sense icon",
                        modifier = Modifier.size(60.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ---- App name + tagline ------------------------------------
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer(
                    alpha = textAlpha.value,
                    translationY = textTranslateY.value
                )
            ) {
                Text(
                    text = "BleSense",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Monospace,
                    color = BleSenseColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Seamless sensor control",
                    fontSize = 15.sp,
                    fontFamily = Monospace,
                    color = BleSenseColors.PrimaryGreen,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Monitor and manage all your BLE sensors with ease.",
                    fontSize = 13.sp,
                    fontFamily = Monospace,
                    color = BleSenseColors.TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ---- Buttons -----------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        alpha = buttonAlpha.value,
                        translationY = buttonTranslateY.value
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Login button (filled green)
                Button(
                    onClick = onNavigateToLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BleSenseColors.PrimaryGreenDark
                    ),
                    border = BorderStroke(1.dp, BleSenseColors.PrimaryGreen.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Login",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Monospace,
                        color = BleSenseColors.TextPrimary
                    )
                }

                // Sign up button (outlined)
                OutlinedButton(
                    onClick = onNavigateToSignup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BleSenseColors.SurfaceLight),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = BleSenseColors.SurfaceDark
                    )
                ) {
                    Text(
                        text = "Sign Up",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Monospace,
                        color = BleSenseColors.TextPrimary
                    )
                }

                // Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = BleSenseColors.SurfaceLight
                    )
                    Text(
                        text = "  OR  ",
                        color = BleSenseColors.TextTertiary,
                        fontSize = 12.sp,
                        fontFamily = Monospace
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = BleSenseColors.SurfaceLight
                    )
                }

                // Continue as Guest
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = BleSenseColors.PrimaryGreen,
                            strokeWidth = 2.5.dp
                        )
                    }
                } else {
                    TextButton(
                        onClick = {
                            isLoading = true
                            onGuestSignIn()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Continue as Guest",
                            fontSize = 15.sp,
                            fontFamily = Monospace,
                            fontWeight = FontWeight.Medium,
                            color = BleSenseColors.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}