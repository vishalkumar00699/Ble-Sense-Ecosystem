@file:OptIn(ExperimentalMaterial3Api::class)

package com.blesense.app

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.delay

import com.blesense.app.ui.theme.BleSenseColors
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

@Composable
fun BleSenseLoginScreen(
    viewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }

    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current

    // Animated scanning effect
    LaunchedEffect(Unit) {
        isScanning = true
    }

    val isFormValid by remember(email, password) {
        derivedStateOf { email.isNotBlank() && password.length >= 6 }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account.idToken?.let { viewModel.signInWithGoogle(it) }
            } catch (e: ApiException) {
                errorMessage = "Google Sign-In failed"
                showErrorDialog = true
            }
        }
    }

    val googleSignInClient = remember { GoogleSignInHelper.getGoogleSignInClient(context) }
    LaunchedEffect(Unit) {
        viewModel.setGoogleSignInClient(googleSignInClient)
    }

    // Handle auth states
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> onNavigateToHome()
            is AuthState.Error -> {
                errorMessage = (authState as AuthState.Error).message
                showErrorDialog = true
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BleSenseColors.BackgroundDark)
    ) {
        // Background gradient effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            BleSenseColors.PrimaryGreen.copy(alpha = 0.08f),
                            Color.Transparent,
                            Color.Transparent
                        ),
                        radius = 800f,
                        center = Offset(300f, 200f)
                    )
                )
        )

        // Animated floating particles
        FloatingParticles()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // Top bar with scanning status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Logo circle
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(BleSenseColors.PrimaryGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Logo",
                            tint = BleSenseColors.PrimaryGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "BleSense",
                        color = BleSenseColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                }

                // Scanning status indicator
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BleSenseColors.SurfaceDark,
                    modifier = Modifier
                        .height(32.dp)
                        .wrapContentWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scanning",
                            tint = BleSenseColors.PrimaryGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Scanning",
                            color = BleSenseColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(BleSenseColors.PrimaryGreen)
                                .animateContentSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Hero image area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                // Placeholder for hero image - replace with your actual image resource
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    BleSenseColors.PrimaryGreen.copy(alpha = 0.15f),
                                    BleSenseColors.PrimaryGreen.copy(alpha = 0.05f)
                                )
                            )
                        )
                )

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    BleSenseColors.BackgroundDark.copy(alpha = 0.8f)
                                ),
                                startY = 100f
                            )
                        )
                )

                // Text overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Seamless",
                        color = BleSenseColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light
                    )
                    Text(
                        text = "sensor control",
                        color = BleSenseColors.PrimaryGreen,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Monitor and manage all your BLE sensors with ease.",
                        color = BleSenseColors.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Status cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusCard(
                    icon = Icons.Default.Wifi,
                    label = "Status",
                    value = "Ready",
                    iconColor = BleSenseColors.PrimaryGreen
                )

                StatusCard(
                    icon = Icons.Default.Add,
                    label = "New",
                    value = "Sign Up",
                    iconColor = BleSenseColors.TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Login form card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = BleSenseColors.SurfaceDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Welcome Back",
                        color = BleSenseColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Sign in to continue",
                        color = BleSenseColors.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Email field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = {
                            Text("Email address", color = BleSenseColors.TextTertiary)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = BleSenseColors.PrimaryGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = BleSenseColors.PrimaryGreen,
                            unfocusedBorderColor = BleSenseColors.SurfaceLight,
                            focusedTextColor = BleSenseColors.TextPrimary,
                            unfocusedTextColor = BleSenseColors.TextPrimary,
                            cursorColor = BleSenseColors.PrimaryGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = {
                            Text("Password", color = BleSenseColors.TextTertiary)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = BleSenseColors.PrimaryGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = BleSenseColors.TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = BleSenseColors.PrimaryGreen,
                            unfocusedBorderColor = BleSenseColors.SurfaceLight,
                            focusedTextColor = BleSenseColors.TextPrimary,
                            unfocusedTextColor = BleSenseColors.TextPrimary,
                            cursorColor = BleSenseColors.PrimaryGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        )
                    )

                    // Forgot password
                    TextButton(
                        onClick = { /* Handle forgot password */ },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = "Forgot Password?",
                            color = BleSenseColors.PrimaryGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Login button
                    Button(
                        onClick = {
                            if (isFormValid) {
                                viewModel.loginUser(email.trim(), password)
                            }
                        },
                        enabled = isFormValid && authState !is AuthState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BleSenseColors.PrimaryGreen,
                            disabledContainerColor = BleSenseColors.PrimaryGreen.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (authState is AuthState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = BleSenseColors.BackgroundDark,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Sign In",
                                color = BleSenseColors.BackgroundDark,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = BleSenseColors.SurfaceLight,
                            thickness = 1.dp
                        )
                        Text(
                            text = "Or continue with",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = BleSenseColors.TextTertiary,
                            fontSize = 13.sp
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = BleSenseColors.SurfaceLight,
                            thickness = 1.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Social login buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        SocialButton(
                            icon = R.drawable.google_g,
                            onClick = { launcher.launch(googleSignInClient.signInIntent) }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Sign up link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Don't have an account? ",
                            color = BleSenseColors.TextSecondary,
                            fontSize = 14.sp
                        )
                        TextButton(
                            onClick = onNavigateToRegister,
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(
                                text = "Register Now",
                                color = BleSenseColors.PrimaryGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation tiles
            Text(
                text = "Quick Access",
                color = BleSenseColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NavigationTile(
                    icon = Icons.Default.Bluetooth,
                    title = "Bluetooth",
                    subtitle = "Scan & Connect",
                    iconColor = BleSenseColors.BluetoothBlue
                )

                NavigationTile(
                    icon = Icons.Default.Storage,
                    title = "Data Logger",
                    subtitle = "Record & Export",
                    iconColor = BleSenseColors.PrimaryGreen
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        // Error dialog
        if (showErrorDialog && errorMessage != null) {
            AlertDialog(
                onDismissRequest = {
                    showErrorDialog = false
                    errorMessage = null
                },
                title = {
                    Text(
                        text = "Error",
                        color = BleSenseColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = errorMessage ?: "An error occurred",
                        color = BleSenseColors.TextSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showErrorDialog = false
                            errorMessage = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BleSenseColors.PrimaryGreen
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("OK", color = BleSenseColors.BackgroundDark)
                    }
                },
                containerColor = BleSenseColors.SurfaceDark,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun RowScope.StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    iconColor: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BleSenseColors.SurfaceDark,
        modifier = Modifier.weight(1f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = label,
                    color = BleSenseColors.TextTertiary,
                    fontSize = 11.sp
                )
                Text(
                    text = value,
                    color = BleSenseColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun RowScope.NavigationTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BleSenseColors.SurfaceDark,
        modifier = Modifier.weight(1f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                color = BleSenseColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = BleSenseColors.TextTertiary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun SocialButton(
    icon: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BleSenseColors.SurfaceLight,
        modifier = Modifier.size(56.dp),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = "Social login",
                tint = Color.Unspecified,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun FloatingParticles() {
    // Simplified particle effect - you can expand this for more visual interest
    Box(modifier = Modifier.fillMaxSize()) {
        // Add animated particles here if desired
    }
}

// Preview
@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun BleSenseLoginScreenPreview() {
    MaterialTheme {
        BleSenseLoginScreen(
            viewModel = AuthViewModel(),
            onNavigateToRegister = {},
            onNavigateToHome = {}
        )
    }
}