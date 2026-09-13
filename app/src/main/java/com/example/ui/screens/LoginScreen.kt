package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.example.R
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
import com.example.util.LocalAppStrings
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

    // Modern Theme-Aware Design System
    val strings = LocalAppStrings.current
    val isDark = com.example.ui.theme.AppTheme.colors.isDark
    val appColors = com.example.ui.theme.AppTheme.colors
    val deepNavyBg = appColors.background
    val surfaceElevatedColor = appColors.surfaceElevated
    val borderStrokeColor = appColors.cardBorder
    val brandCyan = appColors.brandCyan
    val electricSky = appColors.electricSky
    val brandPurple = appColors.brandPurple
    val neonGreen = appColors.neonGreen
    val neonOrange = appColors.neonOrange
    val neonRed = appColors.neonRed
    val neonYellow = appColors.neonYellow

    val cardBg = appColors.surfaceCard
    val inputBg = if (isDark) Color(0xFF0A1224) else Color(0xFFF1F5F9)
    val inputFocusBg = if (isDark) Color(0xFF0F1A30) else Color(0xFFE2E8F0)
    val borderNormal = appColors.cardBorder
    val textMuted = appColors.textMuted
    val textSecondary = appColors.textSecondary
    val placeholderColor = if (isDark) Color(0xFF475569) else Color(0xFF94A3B8)

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
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Pulsing aura alpha
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Dynamic Gradients matching MainScreen's high-tech aesthetic
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF0284C7),
            Color(0xFF2563EB),
            Color(0xFF4F46E5),
            Color(0xFF7C3AED)
        )
    )

    val cardGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF111C32),
                Color(0xFF090F1C)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White,
                Color(0xFFF8FAFC)
            )
        )
    }

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
                    Brush.verticalGradient(
                        if (isDark) {
                            listOf(
                                Color(0xFF0A1224),
                                deepNavyBg,
                                Color(0xFF04060C)
                            )
                        } else {
                            listOf(
                                Color(0xFFF1F5F9),
                                deepNavyBg,
                                Color(0xFFE2E8F0)
                            )
                        }
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
                    // Main Glassmorphic Container Card matching MainScreen style
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 440.dp)
                            .border(
                                1.dp,
                                if (isDark) {
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0x5038BDF8),
                                            Color(0x1538BDF8),
                                            Color(0x40A855F7)
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFFBAE6FD),
                                            Color(0xFFE2E8F0),
                                            Color(0xFFE9D5FF)
                                        )
                                    )
                                },
                                RoundedCornerShape(28.dp)
                            )
                            .shadow(
                                elevation = 24.dp,
                                shape = RoundedCornerShape(28.dp),
                                spotColor = brandCyan.copy(alpha = if (isDark) 0.35f else 0.15f),
                                ambientColor = if (isDark) Color.Black else Color(0x10000000)
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cardGradient)
                                .padding(horizontal = 24.dp, vertical = 28.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Top Security Badge Chip with Neon Beacon
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(if (isDark) Color(0xFF0F1A2F) else Color(0xFFE2E8F0))
                                        .border(
                                            1.dp,
                                            Brush.horizontalGradient(
                                                if (isDark) {
                                                    listOf(
                                                        Color(0x4038BDF8),
                                                        Color(0x30A855F7)
                                                    )
                                                } else {
                                                    listOf(
                                                        Color(0xFFBAE6FD),
                                                        Color(0xFFDDD6FE)
                                                    )
                                                }
                                            ),
                                            RoundedCornerShape(100.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(neonGreen)
                                    )
                                    Spacer(modifier = Modifier.width(7.dp))
                                    Text(
                                        text = "GMB NET",
                                        color = if (isDark) Color.White else Color(0xFF0F172A),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.2.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.VpnLock,
                                        contentDescription = null,
                                        tint = brandCyan,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // ==========================================
                                // ANIMATED VPN SECURITY LOCK LOGO
                                // ==========================================
                                Box(
                                    modifier = Modifier
                                        .size(116.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Outer Glowing Aura
                                    Box(
                                        modifier = Modifier
                                            .size(110.dp)
                                            .scale(pulseScale)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        brandCyan.copy(alpha = glowAlpha * 0.40f),
                                                        brandPurple.copy(alpha = glowAlpha * 0.15f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    )

                                    // Rotating Cyber Segment Orbit Ring
                                    Box(
                                        modifier = Modifier
                                            .size(102.dp)
                                            .rotate(ringRotation)
                                            .drawBehind {
                                                val strokeWidth = 2.dp.toPx()
                                                drawCircle(
                                                    brush = Brush.sweepGradient(
                                                        listOf(
                                                            Color.Transparent,
                                                            brandCyan,
                                                            Color.Transparent,
                                                            electricSky,
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
                                            .size(90.dp)
                                            .rotate(-ringRotation * 1.4f)
                                            .drawBehind {
                                                val r = size.minDimension / 2
                                                val center = Offset(size.width / 2, size.height / 2)
                                                drawCircle(
                                                    color = brandCyan.copy(alpha = 0.9f),
                                                    radius = 3.dp.toPx(),
                                                    center = Offset(center.x + r, center.y)
                                                )
                                                drawCircle(
                                                    color = brandPurple.copy(alpha = 0.9f),
                                                    radius = 2.5.dp.toPx(),
                                                    center = Offset(center.x - r, center.y)
                                                )
                                            }
                                    )

                                    // Modern Floating Cyber Logo (Zero background, pure futuristic transparency)
                                    Box(
                                        modifier = Modifier.size(82.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Ambient Soft Glow behind the logo
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(
                                                Brush.radialGradient(
                                                    listOf(
                                                        brandCyan.copy(alpha = 0.28f),
                                                        brandPurple.copy(alpha = 0.16f),
                                                        Color.Transparent
                                                    )
                                                ),
                                                CircleShape
                                            )
                                        )

                                        Image(
                                            painter = painterResource(id = R.drawable.ic_gmb_logo),
                                            contentDescription = "GMB NET Logo",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Title
                                Text(
                                    text = strings.loginTitle,
                                    color = if (isDark) Color.White else Color(0xFF0F172A),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 0.5.sp
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Subtitle
                                Text(
                                    text = strings.loginSubtitle,
                                    color = if (isDark) textMuted else Color(0xFF64748B),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(24.dp))

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
                                                text = strings.clearField,
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
                                            text = strings.loginUsernameLabel,
                                            color = if (isUsernameFocused) brandCyan else (if (isDark) textSecondary else Color(0xFF334155)),
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
                                                text = strings.loginUsernamePlaceholder,
                                                color = placeholderColor,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Right,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Person,
                                                contentDescription = strings.loginUsernameLabel,
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
                                            focusedTextColor = if (isDark) Color.White else Color(0xFF0F172A),
                                            unfocusedTextColor = if (isDark) textSecondary else Color(0xFF334155),
                                            focusedBorderColor = brandCyan,
                                            unfocusedBorderColor = if (isDark) borderStrokeColor else Color(0xFFCBD5E1),
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
                                            text = strings.loginPasswordLabel,
                                            color = if (isPasswordFocused) brandCyan else (if (isDark) textSecondary else Color(0xFF334155)),
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
                                                text = strings.loginPasswordPlaceholder,
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
                                                    contentDescription = strings.loginPasswordLabel,
                                                    tint = if (isPasswordVisible) brandCyan else placeholderColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Lock,
                                                contentDescription = strings.loginPasswordLabel,
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
                                                    localValidationMsg = strings.loginValidationEmpty
                                                } else {
                                                    viewModel.login(username.trim(), password.trim())
                                                }
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("password_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = if (isDark) Color.White else Color(0xFF0F172A),
                                            unfocusedTextColor = if (isDark) textSecondary else Color(0xFF334155),
                                            focusedBorderColor = brandCyan,
                                            unfocusedBorderColor = if (isDark) borderStrokeColor else Color(0xFFCBD5E1),
                                            cursorColor = brandCyan,
                                            focusedContainerColor = inputFocusBg,
                                            unfocusedContainerColor = inputBg
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Remember Me Checkbox Row
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
                                        text = strings.loginRememberMe,
                                        color = if (isDark) textMuted else Color(0xFF475569),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Checkbox(
                                        checked = rememberMe,
                                        onCheckedChange = { rememberMe = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = brandCyan,
                                            uncheckedColor = if (isDark) borderStrokeColor else Color(0xFF94A3B8),
                                            checkmarkColor = if (isDark) Color(0xFF060912) else Color.White
                                        ),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Local validation warning
                                if (localValidationMsg != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = localValidationMsg ?: "",
                                        color = neonRed,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Spacer(modifier = Modifier.height(22.dp))

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
                                                localValidationMsg = strings.loginValidationEmpty
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
                                                text = strings.loginValidating,
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
                                                text = strings.loginButtonSubmit,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Divider with "یا" / "OR"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = if (isDark) borderStrokeColor else Color(0xFFE2E8F0),
                                        thickness = 0.7.dp
                                    )
                                    Text(
                                        text = strings.loginOrDivider,
                                        color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = if (isDark) borderStrokeColor else Color(0xFFE2E8F0),
                                        thickness = 0.7.dp
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Informational Text
                                Text(
                                    text = strings.loginNeedAccountHint,
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Buy Subscription Action Button with Cyan Glass Style
                                Surface(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gmb-net.ir"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, strings.errorOpeningBrowser, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isDark) Color(0xFF0F2642) else Color(0xFFE0F2FE),
                                    border = BorderStroke(1.dp, if (isDark) Color(0xFF0284C7).copy(alpha = 0.6f) else Color(0xFF38BDF8)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("buy_subscription_login_button")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ShoppingBag,
                                            contentDescription = null,
                                            tint = brandCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = strings.loginBuySubscription,
                                            color = if (isDark) electricSky else Color(0xFF0284C7),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
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
                                .border(1.dp, neonRed.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF25101A) else Color(0xFFFEF2F2)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = errorMsg,
                                    color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
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
                                        contentDescription = null,
                                        tint = neonRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Minimal Footer Info
                    Text(
                        text = strings.loginPoweredBy,
                        color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
