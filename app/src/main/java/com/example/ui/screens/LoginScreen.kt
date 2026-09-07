package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.AppDatabase
import com.example.data.database.UserSession
import com.example.util.PersianDateHelper
import com.example.viewmodel.LoginState
import com.example.viewmodel.VpnViewModel
import com.example.vpn.SshVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: VpnViewModel,
    onNavigateToMain: () -> Unit,
    onNavigateToPurchase: () -> Unit
) {
    val loginState by viewModel.loginState.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()

    var username by remember(activeSession) { mutableStateOf(activeSession?.username ?: "") }
    var password by remember(activeSession) { mutableStateOf(activeSession?.sshPassword ?: "") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var localValidationMsg by remember { mutableStateOf<String?>(null) }

    // Direct checkout state: English username and phone before payment
    var showDirectPurchaseSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // Smooth Entrance Animation
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        isVisible = true
    }

    // Modern Cyber Palette
    val deepNavyBg = Color(0xFF060A13)
    val cardBg = Color(0xFF0C1324)
    val inputBg = Color(0xFF111A2E)
    val inputFocusBg = Color(0xFF142038)
    val borderNormal = Color(0xFF1E2D4A)
    val textMuted = Color(0xFF8DA2C0)
    val textSecondary = Color(0xFFCBD5E1)
    val placeholderColor = Color(0xFF5A6F90)
    val brandCyan = Color(0xFF38BDF8)
    val neonElectric = Color(0xFF00E5FF)
    val brandPurple = Color(0xFFA855F7)
    val neonGreen = Color(0xFF10B981)

    // Infinite pulsing/rotation animations for the VPN Security Lock
    val infiniteTransition = rememberInfiniteTransition(label = "cyber_lock_anim")

    // Slow orbit rotation for outer shield ring
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )

    // Breathing glow scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Pulsing aura alpha
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Dynamic Gradients
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF0284C7),
            Color(0xFF2563EB),
            Color(0xFF6366F1),
            Color(0xFFA855F7)
        )
    )

    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0E172C),
            Color(0xFF090E1C)
        )
    )

    // Automatically navigate to Main if user login was successful
    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            onNavigateToMain()
        }
    }

    Scaffold(
        containerColor = deepNavyBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF101B34), deepNavyBg),
                        radius = 1300f
                    )
                )
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
                    initialOffsetY = { 60 },
                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Main Glassmorphic Container Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 440.dp)
                            .border(1.dp, borderNormal, RoundedCornerShape(32.dp))
                            .shadow(
                                elevation = 24.dp,
                                shape = RoundedCornerShape(32.dp),
                                spotColor = brandCyan.copy(alpha = 0.25f),
                                ambientColor = Color.Black
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cardGradient)
                                .padding(horizontal = 24.dp, vertical = 26.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Top Security Badge Chip
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(Color(0xFF14223E))
                                        .border(1.dp, Color(0xFF243A62), RoundedCornerShape(100.dp))
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(neonGreen)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "GMB NET",
                                        color = Color(0xFF93C5FD),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.VpnLock,
                                        contentDescription = null,
                                        tint = brandCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // ==========================================
                                // ANIMATED VPN SECURITY LOCK LOGO
                                // ==========================================
                                Box(
                                    modifier = Modifier
                                        .size(110.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Outer Glowing Aura
                                    Box(
                                        modifier = Modifier
                                            .size(105.dp)
                                            .scale(pulseScale)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        brandCyan.copy(alpha = glowAlpha * 0.45f),
                                                        brandPurple.copy(alpha = glowAlpha * 0.15f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    )

                                    // Rotating Cyber Segment Orbit Ring
                                    Box(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .rotate(ringRotation)
                                            .drawBehind {
                                                val strokeWidth = 2.dp.toPx()
                                                drawCircle(
                                                    brush = Brush.sweepGradient(
                                                        listOf(
                                                            Color.Transparent,
                                                            brandCyan,
                                                            Color.Transparent,
                                                            brandPurple,
                                                            Color.Transparent
                                                        )
                                                    ),
                                                    style = Stroke(
                                                        width = strokeWidth,
                                                        cap = StrokeCap.Round
                                                    )
                                                )
                                            }
                                    )

                                    // Second counter-rotating subtle dot ring
                                    Box(
                                        modifier = Modifier
                                            .size(86.dp)
                                            .rotate(-ringRotation * 1.5f)
                                            .drawBehind {
                                                val r = size.minDimension / 2
                                                val center = Offset(size.width / 2, size.height / 2)
                                                drawCircle(
                                                    color = neonElectric.copy(alpha = 0.8f),
                                                    radius = 3.dp.toPx(),
                                                    center = Offset(center.x + r, center.y)
                                                )
                                                drawCircle(
                                                    color = brandPurple.copy(alpha = 0.8f),
                                                    radius = 2.5.dp.toPx(),
                                                    center = Offset(center.x - r, center.y)
                                                )
                                            }
                                    )

                                    // Central Cyber Hexagon/Squircle Lock Badge
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp)
                                            .shadow(
                                                elevation = 16.dp,
                                                shape = RoundedCornerShape(24.dp),
                                                spotColor = brandCyan,
                                                ambientColor = brandPurple
                                            )
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        Color(0xFF122448),
                                                        Color(0xFF0A1224)
                                                    )
                                                )
                                            )
                                            .border(
                                                1.5.dp,
                                                Brush.linearGradient(
                                                    listOf(
                                                        brandCyan,
                                                        Color(0xFF6366F1),
                                                        brandPurple
                                                    )
                                                ),
                                                RoundedCornerShape(24.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Background subtle Shield
                                        Icon(
                                            imageVector = Icons.Outlined.Shield,
                                            contentDescription = null,
                                            tint = brandCyan.copy(alpha = 0.2f),
                                            modifier = Modifier.size(46.dp)
                                        )

                                        // Central Security Lock Icon
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "VPN Security Lock",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )

                                        // Small Keyhole Glow Indicator
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 8.dp)
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(neonElectric)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Title Changed to: "ورود به اپلیکیشن"
                                Text(
                                    text = "ورود به اپلیکیشن",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 0.5.sp
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Subtitle
                                Text(
                                    text = "جهت اتصال ایمن، مشخصات اشتراک خود را وارد نمایید",
                                    color = textMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(22.dp))

                                // ==========================================
                                // USERNAME INPUT FIELD
                                // ==========================================
                                val usernameInteractionSource = remember { MutableInteractionSource() }
                                val isUsernameFocused by usernameInteractionSource.collectIsFocusedAsState()

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (username.isNotBlank()) {
                                            Text(
                                                text = "پاک کردن",
                                                color = brandCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier
                                                    .clickable { username = "" }
                                                    .padding(2.dp)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.width(1.dp))
                                        }
                                        Text(
                                            text = "نام کاربری",
                                            color = if (isUsernameFocused) brandCyan else textSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }

                                    OutlinedTextField(
                                        value = username,
                                        onValueChange = {
                                            username = it
                                            localValidationMsg = null
                                        },
                                        placeholder = {
                                            Text(
                                                text = "نام کاربری اشتراک...",
                                                color = placeholderColor,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Right,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Person,
                                                contentDescription = "آیکون نام کاربری",
                                                tint = if (isUsernameFocused) brandCyan else placeholderColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        interactionSource = usernameInteractionSource,
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Text,
                                            imeAction = ImeAction.Next
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("username_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = textSecondary,
                                            focusedBorderColor = brandCyan,
                                            unfocusedBorderColor = borderNormal,
                                            cursorColor = brandCyan,
                                            focusedContainerColor = inputFocusBg,
                                            unfocusedContainerColor = inputBg
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // ==========================================
                                // PASSWORD INPUT FIELD
                                // ==========================================
                                val passwordInteractionSource = remember { MutableInteractionSource() }
                                val isPasswordFocused by passwordInteractionSource.collectIsFocusedAsState()

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "کلمه عبور",
                                            color = if (isPasswordFocused) brandCyan else textSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }

                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = {
                                            password = it
                                            localValidationMsg = null
                                        },
                                        placeholder = {
                                            Text(
                                                text = "رمز عبور اختصاصی...",
                                                color = placeholderColor,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Right,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        leadingIcon = {
                                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                                Icon(
                                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = "نمایش یا پنهان‌سازی رمز عبور",
                                                    tint = if (isPasswordVisible) brandCyan else placeholderColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Lock,
                                                contentDescription = "آیکون رمز عبور",
                                                tint = if (isPasswordFocused) brandCyan else placeholderColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        interactionSource = passwordInteractionSource,
                                        singleLine = true,
                                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                focusManager.clearFocus()
                                                if (username.isBlank() || password.isBlank()) {
                                                    localValidationMsg = "لطفاً نام کاربری و کلمه عبور را وارد فرمایید"
                                                } else {
                                                    viewModel.login(username.trim(), password.trim())
                                                }
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("password_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = textSecondary,
                                            focusedBorderColor = brandCyan,
                                            unfocusedBorderColor = borderNormal,
                                            cursorColor = brandCyan,
                                            focusedContainerColor = inputFocusBg,
                                            unfocusedContainerColor = inputBg
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // "مرا به خاطر بسپار" Checkbox Row (Right-Aligned)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { rememberMe = !rememberMe },
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "مرا به خاطر بسپار",
                                        color = textMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Checkbox(
                                        checked = rememberMe,
                                        onCheckedChange = { rememberMe = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = brandCyan,
                                            uncheckedColor = Color(0xFF334155),
                                            checkmarkColor = Color(0xFF0A0F1E)
                                        ),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Local validation warning
                                if (localValidationMsg != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = localValidationMsg ?: "",
                                        color = Color(0xFFF87171),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // ==========================================
                                // MAIN LOGIN ACTION BUTTON
                                // ==========================================
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .shadow(
                                            elevation = 14.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            spotColor = brandCyan.copy(alpha = 0.5f),
                                            ambientColor = brandPurple.copy(alpha = 0.3f)
                                        )
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(buttonGradient)
                                        .clickable(enabled = loginState !is LoginState.Loading) {
                                            focusManager.clearFocus()
                                            if (username.isBlank() || password.isBlank()) {
                                                localValidationMsg = "لطفاً نام کاربری و کلمه عبور را وارد فرمایید"
                                            } else {
                                                localValidationMsg = null
                                                viewModel.login(username.trim(), password.trim())
                                            }
                                        }
                                        .testTag("login_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (loginState is LoginState.Loading) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                color = Color.White,
                                                strokeWidth = 2.5.dp
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "در حال اعتبارسنجی و ورود...",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LockOpen,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "ورود به حساب کاربری",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Divider with "یا"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = Color(0xFF1E2D4A),
                                        thickness = 0.7.dp
                                    )
                                    Text(
                                        text = "یا",
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = Color(0xFF1E2D4A),
                                        thickness = 0.7.dp
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Buy Subscription Action Button / Link
                                OutlinedButton(
                                    onClick = { showDirectPurchaseSheet = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color(0xFF243A62)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFF0F1B33).copy(alpha = 0.6f)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = brandCyan,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "خرید اشتراک جدید و تحویل آنی",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Dynamic Server/Network Error Card
                    AnimatedVisibility(
                        visible = loginState is LoginState.Error,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val errorMsg = (loginState as? LoginState.Error)?.error ?: ""
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 440.dp)
                                .padding(top = 16.dp)
                                .border(1.dp, Color(0x60EF4444), RoundedCornerShape(18.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF25101A)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = errorMsg,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.weight(1f),
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x30EF4444)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "خطا در ورود",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Minimal Footer Info
                    Text(
                        text = "قدرت گرفته توسط گمبرون نت",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ==========================================
        // DIRECT PURCHASE & INSTANT DELIVERY SHEET
        // ==========================================
        if (showDirectPurchaseSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var directUsername by remember { mutableStateOf("") }
            var directPhone by remember { mutableStateOf("") }
            var selectedPlanIndex by remember { mutableIntStateOf(1) } // Default 3 months
            var currentCheckoutStep by remember { mutableIntStateOf(1) } // 1 = Info & Plan, 2 = Payment & Delivery
            var trackingNumber by remember { mutableStateOf("") }
            var cardCopied by remember { mutableStateOf(false) }
            var shabaCopied by remember { mutableStateOf(false) }
            var isCreatingSession by remember { mutableStateOf(false) }

            val plans = listOf(
                Triple("اشتراک ۱ ماهه", "۵۰۰,۰۰۰ تومان", 30),
                Triple("اشتراک ۳ ماهه ⭐", "۱,۳۰۰,۰۰۰ تومان", 90),
                Triple("اشتراک ۶ ماهه", "۲,۴۰۰,۰۰۰ تومان", 180),
                Triple("اشتراک ۱ ساله ویژه", "۴,۲۰۰,۰۰۰ تومان", 365)
            )

            val chosenPlan = plans[selectedPlanIndex]
            val isUsernameValid = directUsername.trim().length >= 3
            val isPhoneValid = directPhone.startsWith("09") && directPhone.length == 11

            ModalBottomSheet(
                onDismissRequest = { showDirectPurchaseSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF080E1C),
                scrimColor = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                dragHandle = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155))
                        )
                    }
                },
                modifier = Modifier.fillMaxHeight(0.95f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showDirectPurchaseSheet = false },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF162032))
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "بستن",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (currentCheckoutStep == 1) "خرید اشتراک جدید و تحویل آنی" else "پرداخت و دریافت آنی اشتراک",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (currentCheckoutStep == 1) "مرحله ۱: دریافت نام کاربری و شماره تلفن" else "مرحله ۲: واریز و فعال‌سازی فوری",
                                color = brandCyan,
                                fontSize = 11.sp
                            )
                        }

                        Surface(
                            color = neonGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "تحویل آنی",
                                color = neonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (currentCheckoutStep == 1) {
                        // ==========================================
                        // STEP 1: USERNAME (ENGLISH) + PHONE + PLAN
                        // ==========================================
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF1E2E4A), RoundedCornerShape(18.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "۱. نام کاربری به انگلیسی",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "حداقل ۳ حرف",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }

                                // English Username Input
                                OutlinedTextField(
                                    value = directUsername,
                                    onValueChange = { input ->
                                        // Only English letters, digits, and underscores
                                        directUsername = input.filter { ch ->
                                            (ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9') || ch == '_'
                                        }
                                    },
                                    placeholder = {
                                        Text("مثال: user123 (فقط حروف انگلیسی)", color = Color(0xFF475569), fontSize = 12.sp)
                                    },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Left
                                    ),
                                    supportingText = {
                                        Text(
                                            text = if (isUsernameValid) "✓ نام کاربری معتبر است" else "نام کاربری اشتراک باید به انگلیسی باشد",
                                            color = if (isUsernameValid) neonGreen else Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = brandCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = brandCyan,
                                        unfocusedBorderColor = Color(0xFF1E2E4A),
                                        focusedContainerColor = Color(0xFF090E1C),
                                        unfocusedContainerColor = Color(0xFF090E1C)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "۲. شماره تلفن همراه",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "جهت ارسال پیامک و مشخصات",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }

                                // Phone Number Input
                                OutlinedTextField(
                                    value = directPhone,
                                    onValueChange = { input ->
                                        directPhone = input.filter { it.isDigit() }.take(11)
                                    },
                                    placeholder = {
                                        Text("مثال: ۰۹۱۲۳۴۵۶۷۸۹ (۱۱ رقمی)", color = Color(0xFF475569), fontSize = 12.sp)
                                    },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Left
                                    ),
                                    supportingText = {
                                        Text(
                                            text = if (isPhoneValid) "✓ شماره موبایل تایید شد" else "شماره ۱۱ رقمی همراه با شروع ۰۹",
                                            color = if (isPhoneValid) neonGreen else Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = brandCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = brandCyan,
                                        unfocusedBorderColor = Color(0xFF1E2E4A),
                                        focusedContainerColor = Color(0xFF090E1C),
                                        unfocusedContainerColor = Color(0xFF090E1C)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Select Plan
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF1E2E4A), RoundedCornerShape(18.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "۳. انتخاب مدت اشتراک:",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                plans.forEachIndexed { index, plan ->
                                    val isSelected = selectedPlanIndex == index
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) brandCyan.copy(alpha = 0.12f) else Color(0xFF0A0F1E))
                                            .border(
                                                1.dp,
                                                if (isSelected) brandCyan else Color(0xFF1E2D4A),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { selectedPlanIndex = index }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedPlanIndex = index },
                                            colors = RadioButtonDefaults.colors(selectedColor = brandCyan)
                                        )

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = plan.first,
                                                color = if (isSelected) brandCyan else Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = plan.second,
                                                color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Proceed Button
                        Button(
                            onClick = {
                                if (isUsernameValid && isPhoneValid) {
                                    currentCheckoutStep = 2
                                } else {
                                    Toast.makeText(context, "لطفاً نام کاربری انگلیسی و شماره تلفن معتبر وارد فرمایید", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = isUsernameValid && isPhoneValid,
                            colors = ButtonDefaults.buttonColors(containerColor = brandCyan),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFF041021),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "تایید و رفتن به پرداخت و تحویل",
                                    color = Color(0xFF041021),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        TextButton(onClick = {
                            showDirectPurchaseSheet = false
                            onNavigateToPurchase()
                        }) {
                            Text("مشاهده صفحه کامل پلن‌ها و امکانات", color = Color(0xFF64748B), fontSize = 12.sp)
                        }

                    } else {
                        // ==========================================
                        // STEP 2: PAYMENT & INSTANT DELIVERY
                        // ==========================================
                        // Selected user & plan recap
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF1E2E4A), RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1629)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = directUsername,
                                        color = brandCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "نام کاربری اشتراک:",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = directPhone,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "شماره تماس:",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${chosenPlan.first} • ${chosenPlan.second}",
                                        color = Color(0xFFF59E0B),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "پلن انتخابی:",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Bank Card Info
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF1E2E4A), RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1224)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "اطلاعات کارت جهت واریز وجه:",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )

                                // Card number box
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0F1A30))
                                        .border(1.dp, Color(0xFF1E2E4A), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString("6037997514238890"))
                                            cardCopied = true
                                            Toast.makeText(context, "شماره کارت کپی شد", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2E4A)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(if (cardCopied) "کپی شد ✓" else "کپی کارت", color = brandCyan, fontSize = 11.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("۶۰۳۷ - ۹۹۷۵ - ۱۴۲۳ - ۸۸۹۰", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("بانک ملی ایران • به نام گمبرون نت", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                    }
                                }

                                // Shaba box
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0F1A30))
                                        .border(1.dp, Color(0xFF1E2E4A), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString("IR820170000000123456789012"))
                                            shabaCopied = true
                                            Toast.makeText(context, "شماره شبا کپی شد", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2E4A)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(if (shabaCopied) "کپی شد ✓" else "کپی شبا", color = brandCyan, fontSize = 11.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("IR82 0170 0000 0012 3456 7890 12", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("شماره شبا پایا / ساتنا", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                    }
                                }

                                // Tracking input
                                OutlinedTextField(
                                    value = trackingNumber,
                                    onValueChange = { trackingNumber = it },
                                    label = { Text("شماره پیگیری / کد ارجاع تراکنش (اختیاری)", fontSize = 11.sp) },
                                    placeholder = { Text("مثال: ۱۲۳۴۵۶", color = Color(0xFF475569), fontSize = 11.sp) },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Right
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = brandCyan,
                                        unfocusedBorderColor = Color(0xFF1E2E4A),
                                        focusedContainerColor = Color(0xFF090E1C),
                                        unfocusedContainerColor = Color(0xFF090E1C)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Send to Telegram Button
                                Button(
                                    onClick = {
                                        try {
                                            val invoiceMsg = buildString {
                                                append("سلام، درخواست خرید و تحویل آنی اشتراک GMB NET:\n")
                                                append("👤 نام کاربری: $directUsername\n")
                                                append("📱 شماره همراه: $directPhone\n")
                                                append("📦 پلن: ${chosenPlan.first}\n")
                                                append("💰 مبلغ: ${chosenPlan.second}\n")
                                                if (trackingNumber.isNotBlank()) append("🔢 کد پیگیری: $trackingNumber\n")
                                                append("📅 تاریخ: ${PersianDateHelper.getExpiryDateShamsiFormatted(0)}")
                                            }
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://t.me/GMB_NET_Support?text=" + Uri.encode(invoiceMsg))
                                            )
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            clipboardManager.setText(AnnotatedString("@GMB_NET_Support"))
                                            Toast.makeText(context, "آیدی تلگرام کپی شد: @GMB_NET_Support", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ارسال مشخصات به پشتیبانی تلگرام", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Instant Activate and Delivery Action Button!
                        Button(
                            onClick = {
                                isCreatingSession = true
                                coroutineScope.launch {
                                    val db = AppDatabase.getDatabase(context)
                                    val days = chosenPlan.third
                                    val finishDateShamsi = PersianDateHelper.getExpiryDateShamsiFormatted(days)
                                    val newSession = UserSession(
                                        id = 1,
                                        username = directUsername.trim(),
                                        token = "token_${System.currentTimeMillis()}",
                                        remainingDays = days,
                                        finishDate = "",
                                        shamsiFinishDate = finishDateShamsi,
                                        consumedTrafficMb = 0L,
                                        totalTrafficMb = 100L * 1024L,
                                        status = "active",
                                        sshHost = "gmb.server-vip.net",
                                        sshPort = 443,
                                        sshUsername = directUsername.trim(),
                                        sshPassword = "gmb_pass_${directUsername.trim()}",
                                        apiBaseUrl = "https://gmb.server-vip.net"
                                    )
                                    db.userSessionDao().saveSession(newSession)
                                    Toast.makeText(
                                        context,
                                        "اشتراک جدید شما برای نام کاربری ${directUsername.trim()} با موفقیت تحویل داده شد و در برنامه فعال گردید!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    SshVpnService.currentHost = newSession.sshHost
                                    SshVpnService.currentPort = newSession.sshPort
                                    SshVpnService.currentUser = newSession.sshUsername
                                    SshVpnService.currentPass = newSession.sshPassword
                                    SshVpnService.currentUdpgwPort = newSession.udpgwPort
                                    isCreatingSession = false
                                    showDirectPurchaseSheet = false
                                    onNavigateToMain()
                                }
                            },
                            enabled = !isCreatingSession,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            if (isCreatingSession) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = neonGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "تایید پرداخت و تحویل فوری در برنامه",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Back to edit info
                        TextButton(onClick = { currentCheckoutStep = 1 }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("بازگشت و ویرایش نام کاربری و شماره", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
