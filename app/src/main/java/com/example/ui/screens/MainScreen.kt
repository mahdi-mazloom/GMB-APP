package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.vpn.VpnStatus
import com.example.viewmodel.LoginState
import com.example.viewmodel.RedeemLicenseUiState
import com.example.viewmodel.VpnViewModel
import com.example.util.PersianDateHelper
import com.example.util.BatteryOptimizationHelper
import com.example.util.LocalAppStrings
import java.util.Locale

@Composable
fun MainScreen(
    viewModel: VpnViewModel,
    onNavigateBackToLogin: () -> Unit,
    onNavigateToPurchase: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val strings = LocalAppStrings.current
    val activeSession by viewModel.activeSession.collectAsState()
    val loginState by viewModel.loginState.collectAsState()

    // Battery optimization exemption state with auto-refresh on app resume
    val prefs = remember { context.getSharedPreferences("gmb_prefs", android.content.Context.MODE_PRIVATE) }
    var isBatteryCardDismissed by remember {
        mutableStateOf(prefs.getBoolean("battery_card_dismissed", false))
    }
    var isBatteryExempt by remember { 
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) 
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Retrieve live VPN service details
    val status by viewModel.connectionStatus.collectAsState()
    val rx by viewModel.rxBytes.collectAsState()
    val tx by viewModel.txBytes.collectAsState()
    val secondsElapsed by viewModel.duration.collectAsState()
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsState()
    val reconnectAttempts by viewModel.reconnectAttempts.collectAsState()
    val lastReconnectReason by viewModel.lastReconnectReason.collectAsState()

    // Dialog state for renewal, voucher redeem, settings, and logout
    var showRenewDialog by remember { mutableStateOf(false) }
    var showRedeemLicenseDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val redeemLicenseState by viewModel.redeemLicenseState.collectAsState()

    // In-App Update states
    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateNotification by viewModel.updateNotification.collectAsState()

    LaunchedEffect(updateNotification) {
        updateNotification?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearUpdateNotification()
        }
    }

    // Account expiry calculation
    val session = activeSession
    val daysLeft = if (!session?.finishDate.isNullOrBlank()) {
        PersianDateHelper.calculateRemainingDays(session.finishDate)
    } else {
        session?.remainingDays ?: 0
    }
    val isExpired = session?.status == "expired" || daysLeft <= 0

    // Android VPN Intent Permission Launcher
    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpnWithPermissionGranted(context)
        } else {
            viewModel.logError("دسترسی فعال‌سازی VPN توسط کاربر تأیید نشد.")
        }
        viewModel.onVpnPermissionHandled()
    }

    // Monitor ViewModel for permission requested state
    LaunchedEffect(loginState) {
        if (loginState is LoginState.VpnPermissionRequired) {
            vpnPrepareLauncher.launch((loginState as LoginState.VpnPermissionRequired).intent)
        }
    }

    // Modern 2026 Adaptive Design System
    val colors = com.example.ui.theme.AppTheme.colors
    val isDark = colors.isDark

    val deepNavyBg = colors.background
    val surfaceElevatedColor = colors.surfaceElevated
    val borderStrokeColor = colors.cardBorder
    val brandCyan = colors.brandCyan
    val electricSky = colors.electricSky
    val brandPurple = colors.brandPurple
    val neonGreen = colors.neonGreen
    val neonOrange = colors.neonOrange
    val neonRed = colors.neonRed
    val neonYellow = colors.neonYellow

    val textPrimary = colors.textPrimary
    val textSecondary = colors.textSecondary

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
                Color(0xFFFFFFFF),
                Color(0xFFF1F5F9)
            )
        )
    }

    // Animated aura based on connection status or expiration
    val auraColor by animateColorAsState(
        targetValue = when {
            isExpired -> neonRed
            status == VpnStatus.CONNECTING -> neonYellow
            status == VpnStatus.RECONNECTING -> neonOrange
            status == VpnStatus.CONNECTED -> neonGreen
            status == VpnStatus.ERROR -> neonRed
            else -> brandCyan
        },
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "aura_color"
    )

    // Pulsing halo and rotating orbital animations for next-gen cyber feel
    val infiniteTransition = rememberInfiniteTransition(label = "halo_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (status == VpnStatus.CONNECTING || status == VpnStatus.RECONNECTING) 2200 else 12000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rotation"
    )

    Scaffold(
        containerColor = deepNavyBg,
        topBar = {
            // Modern Floating Glass Header with blur-like aesthetics and rim lighting
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.backgroundGradientTop,
                                deepNavyBg
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isDark) Color(0xCC0E1729) else Color(0xF0FFFFFF),
                    border = BorderStroke(1.dp, if (isDark) Color(0x2E38BDF8) else Color(0x330284C7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Settings button with subtle glass ring & cyber glow
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF131F35) else Color(0xFFE2E8F0))
                                .border(1.dp, if (isDark) Color(0x3338BDF8) else Color(0x330284C7), CircleShape)
                                .testTag("top_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = strings.settingsTitle,
                                tint = electricSky,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Centered App Identity with glowing beacon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_gmb_logo),
                                contentDescription = "GMB NET Logo",
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = "GMB NET",
                                fontSize = 17.sp,
                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            )
                            // Glowing status beacon
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isExpired) neonRed else if (status == VpnStatus.CONNECTED) neonGreen else brandCyan)
                            )
                        }

                        // Action button: Store & License Modal with subtle cyan pill
                        Surface(
                            onClick = { showRenewDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isExpired) neonRed.copy(alpha = 0.15f) else if (isDark) Color(0xFF0F2642) else Color(0xFFE0F2FE),
                            border = BorderStroke(1.dp, if (isExpired) neonRed.copy(alpha = 0.5f) else Color(0xFF0284C7).copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("top_shop_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = if (isExpired) strings.renew else strings.store,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExpired) Color(0xFFFCA5A5) else electricSky
                                )
                                Icon(
                                    imageVector = Icons.Default.ShoppingBag,
                                    contentDescription = strings.storeAndRenewTitleModal,
                                    tint = if (isExpired) neonRed else brandCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.backgroundGradientTop,
                            deepNavyBg,
                            colors.backgroundGradientBottom
                        )
                    )
                )
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Expired or Expiring Soon Alert Banner
                AnimatedVisibility(
                    visible = isExpired,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRenewDialog = true }
                            .border(1.5.dp, neonRed.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        if (isDark) listOf(
                                            Color(0xFF3F0B11),
                                            Color(0xFF1E0A12)
                                        ) else listOf(
                                            Color(0xFFFEE2E2),
                                            Color(0xFFFECACA)
                                        )
                                    )
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(
                                    onClick = { showRenewDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = strings.renew,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = strings.subscriptionExpiredNoticeCard,
                                            color = if (isDark) Color.White else Color(0xFF991B1B),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Right
                                        )
                                        Text(
                                            text = strings.subscriptionExpiredTouchToRenew,
                                            color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Right
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(neonRed.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockClock,
                                            contentDescription = strings.subscriptionExpiredNoticeCard,
                                            tint = neonRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Reminder banner if expiring soon (1 to 3 days left)
                AnimatedVisibility(
                    visible = !isExpired && daysLeft in 1..3,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRenewDialog = true }
                            .border(1.dp, neonYellow.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDark) Color(0xFF2A1C08) else Color(0xFFFEF3C7))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = { showRenewDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(strings.fastRenew, color = if (isDark) neonYellow else Color(0xFFB45309), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = strings.subscriptionExpiringDays(daysLeft),
                                color = if (isDark) Color(0xFFFEF08A) else Color(0xFF92400E),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Right
                            )
                        }
                    }
                }

                // 2. Central Connection Core Ring (2026 Futuristic Cyber Core)
                val buttonColor = when {
                    isExpired -> neonRed
                    status == VpnStatus.CONNECTED -> neonGreen
                    status == VpnStatus.CONNECTING -> neonYellow
                    status == VpnStatus.RECONNECTING -> neonOrange
                    status == VpnStatus.ERROR -> neonRed
                    else -> electricSky
                }

                val statusLabel = when {
                    isExpired -> strings.statusSubscriptionExpired
                    status == VpnStatus.CONNECTED -> strings.statusEncryptedActive
                    status == VpnStatus.CONNECTING -> strings.tunnelConnectingServer
                    status == VpnStatus.RECONNECTING -> {
                        if (!isNetworkAvailable) {
                            strings.tunnelInternetDisconnectedWaiting
                        } else if (reconnectAttempts > 0) {
                            strings.tunnelReconnectingAttempt(reconnectAttempts)
                        } else {
                            strings.tunnelReconnecting
                        }
                    }
                    status == VpnStatus.ERROR -> strings.tunnelErrorTapToRetry
                    else -> strings.tunnelReadyTapToStart
                }

                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Outer Breathing Halo & Rotating Orbital Ring
                    Box(
                        modifier = Modifier
                            .size(208.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient Breathing Aura
                        Box(
                            modifier = Modifier
                                .size(if (status != VpnStatus.DISCONNECTED) 208.dp else 190.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isExpired -> neonRed.copy(alpha = 0.16f)
                                        status == VpnStatus.CONNECTING -> neonYellow.copy(alpha = pulseAlpha * 0.28f)
                                        status == VpnStatus.RECONNECTING -> neonOrange.copy(alpha = pulseAlpha * 0.32f)
                                        status == VpnStatus.CONNECTED -> neonGreen.copy(alpha = pulseAlpha * 0.24f)
                                        status == VpnStatus.ERROR -> neonRed.copy(alpha = 0.22f)
                                        else -> brandCyan.copy(alpha = 0.08f)
                                    }
                                )
                        )

                        // Outer Orbit Bezel with Rotating Cyber Glow Ring
                        Box(
                            modifier = Modifier
                                .size(184.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF091122) else Color(0xFFE2E8F0))
                                .drawBehind {
                                    if (status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING || status == VpnStatus.RECONNECTING) {
                                        // Draw futuristic rotating neon arc
                                        drawArc(
                                            brush = Brush.sweepGradient(
                                                listOf(
                                                    auraColor.copy(alpha = 0.1f),
                                                    auraColor,
                                                    brandCyan,
                                                    auraColor.copy(alpha = 0.1f)
                                                )
                                            ),
                                            startAngle = orbitRotation,
                                            sweepAngle = if (status == VpnStatus.CONNECTING || status == VpnStatus.RECONNECTING) 240f else 320f,
                                            useCenter = false,
                                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    } else {
                                        // Subtle static ambient ring
                                        drawCircle(
                                            color = if (isExpired) neonRed.copy(alpha = 0.4f) else if (isDark) Color(0x3338BDF8) else Color(0x660284C7),
                                            style = Stroke(width = 1.5.dp.toPx())
                                        )
                                    }
                                }
                                .clickable {
                                    if (isExpired) {
                                        showRenewDialog = true
                                    } else {
                                        if (status == VpnStatus.CONNECTED || status == VpnStatus.RECONNECTING || status == VpnStatus.CONNECTING) {
                                            viewModel.toggleVpn(context)
                                        } else {
                                            val prep = VpnService.prepare(context)
                                            if (prep != null) {
                                                vpnPrepareLauncher.launch(prep)
                                            } else {
                                                viewModel.toggleVpn(context)
                                            }
                                        }
                                    }
                                }
                                .testTag("connect_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            // Tactile Power Disc with Radial Metallic Depth
                            Box(
                                modifier = Modifier
                                    .size(142.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isExpired -> Brush.radialGradient(
                                                listOf(neonRed.copy(alpha = 0.85f), Color(0xFF4C0519), Color(0xFF070B14))
                                            )
                                            status == VpnStatus.CONNECTED -> Brush.radialGradient(
                                                listOf(neonGreen.copy(alpha = 0.9f), Color(0xFF064E3B), Color(0xFF070B14))
                                            )
                                            status == VpnStatus.CONNECTING -> Brush.radialGradient(
                                                listOf(neonYellow.copy(alpha = 0.85f), Color(0xFF78350F), Color(0xFF070B14))
                                            )
                                            status == VpnStatus.RECONNECTING -> Brush.radialGradient(
                                                listOf(neonOrange.copy(alpha = 0.90f), Color(0xFF7C2D12), Color(0xFF070B14))
                                            )
                                            status == VpnStatus.ERROR -> Brush.radialGradient(
                                                listOf(neonRed.copy(alpha = 0.85f), Color(0xFF7F1D1D), Color(0xFF070B14))
                                            )
                                            else -> if (isDark) {
                                                Brush.radialGradient(
                                                    listOf(Color(0xFF1E2E4A), Color(0xFF0F1A2E), Color(0xFF070C18))
                                                )
                                            } else {
                                                Brush.radialGradient(
                                                    listOf(Color(0xFFF0F9FF), Color(0xFFE0F2FE), Color(0xFFBAE6FD))
                                                )
                                            }
                                        }
                                    )
                                    .border(
                                        width = 2.dp,
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                buttonColor.copy(alpha = 0.9f),
                                                buttonColor.copy(alpha = 0.25f)
                                            )
                                        ),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isExpired -> Icons.Default.Lock
                                            status == VpnStatus.CONNECTED -> Icons.Default.Shield
                                            status == VpnStatus.CONNECTING -> Icons.Default.Sync
                                            status == VpnStatus.RECONNECTING -> Icons.Default.Autorenew
                                            status == VpnStatus.ERROR -> Icons.Default.ErrorOutline
                                            else -> Icons.Default.PowerSettingsNew
                                        },
                                        contentDescription = "وضعیت اتصال",
                                        tint = if (isExpired) Color(0xFFFCA5A5) else if (status != VpnStatus.DISCONNECTED) Color.White else (if (isDark) brandCyan else Color(0xFF0284C7)),
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = when {
                                            isExpired -> "LOCKED"
                                            status == VpnStatus.CONNECTED -> "CONNECTED"
                                            status == VpnStatus.CONNECTING -> "CONNECTING"
                                            status == VpnStatus.RECONNECTING -> "RETRYING"
                                            status == VpnStatus.ERROR -> "ERROR"
                                            else -> "START"
                                        },
                                        color = if (status != VpnStatus.DISCONNECTED || isDark) Color.White else Color(0xFF0F172A),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Futuristic Status Pill with Glowing Beacon
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = if (isDark) Color(0xFF0E172A) else Color(0xFFFFFFFF),
                        border = BorderStroke(1.dp, if (isDark) auraColor.copy(alpha = 0.4f) else auraColor.copy(alpha = 0.6f)),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                color = if (isExpired) Color(0xFFDC2626) else if (status != VpnStatus.DISCONNECTED) (if (isDark) Color.White else Color(0xFF0F172A)) else (if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(buttonColor)
                            )
                        }
                    }

                    // Auto-Reconnect Status Indicator (displayed during automatic reconnection)
                    AnimatedVisibility(
                        visible = status == VpnStatus.RECONNECTING,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xCC7C2D12),
                            border = BorderStroke(1.dp, neonOrange.copy(alpha = 0.6f)),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = strings.statusReconnectingAttempt(reconnectAttempts),
                                    tint = neonOrange,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (!isNetworkAvailable) strings.standbyWaitingForInternet else strings.smartRecoveryActive(reconnectAttempts),
                                    color = Color(0xFFFFEDD5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Live Session Digital Stopwatch (displayed during active connection)
                    AnimatedVisibility(
                        visible = status == VpnStatus.CONNECTED,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xCC064E3B),
                            border = BorderStroke(1.dp, neonGreen.copy(alpha = 0.6f)),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = formatSeconds(secondsElapsed),
                                    color = Color(0xFFA7F3D0),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = strings.connectionDuration,
                                    tint = neonGreen,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = strings.connectionDuration,
                                    color = Color(0xFFD1FAE5),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Intelligent Operator & Protocol Indicator
                    val currentOperator = remember(status) { com.example.util.OperatorDetector.detectOperator(context) }
                    val activeProtocol by com.example.vpn.SshVpnService.activeProtocolName.collectAsState()
                    val operatorText = when (currentOperator) {
                        com.example.util.OperatorType.IRANCELL -> strings.operatorIrancellTag
                        com.example.util.OperatorType.RIGHTEL -> strings.operatorRightelTag
                        com.example.util.OperatorType.MCI -> strings.operatorMciTag
                        else -> strings.operatorWifiTag
                    }
                    val operatorColor = when (currentOperator) {
                        com.example.util.OperatorType.IRANCELL -> Color(0xFFF59E0B)
                        com.example.util.OperatorType.RIGHTEL -> Color(0xFF8B5CF6)
                        com.example.util.OperatorType.MCI -> Color(0xFF06B6D4)
                        else -> Color(0xFF64748B)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF131F35) else Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, operatorColor.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(operatorColor)
                            )
                            Text(
                                text = if (status == VpnStatus.CONNECTED) activeProtocol else operatorText,
                                color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.SignalCellularAlt,
                                contentDescription = null,
                                tint = operatorColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // 3. Telemetry Cockpit (Live Traffic Counters)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isDark) Color(0xFF0F1A2F) else Color(0xFFFFFFFF),
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                if (isDark) Color(0x4038BDF8) else Color(0x330284C7),
                                if (isDark) Color(0x1538BDF8) else Color(0x1A0284C7),
                                Color(0x30A855F7)
                            )
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Download Telemetry Item
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(brandCyan.copy(alpha = 0.15f))
                                    .border(1.dp, brandCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = strings.downloadLabelLong,
                                    tint = brandCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = strings.downloadLabelLong,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatBytes(rx),
                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Center Divider
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .width(1.dp)
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                        )

                        // Upload Telemetry Item
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(brandPurple.copy(alpha = 0.15f))
                                    .border(1.dp, brandPurple.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = strings.uploadLabelLong,
                                    tint = brandPurple,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = strings.uploadLabelLong,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatBytes(tx),
                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 4. Detailed Account Status Cards (2x2 Matrix)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: Username & Remaining Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InfoStatBox(
                            title = strings.usernameCard,
                            value = session?.username ?: strings.noAccount,
                            subtitle = if (isExpired) strings.expired else strings.activeSubscription,
                            icon = Icons.Default.AccountCircle,
                            iconColor = if (isExpired) neonRed else brandPurple,
                            cardGradient = cardGradient,
                            borderStrokeColor = if (isExpired) neonRed.copy(alpha = 0.5f) else borderStrokeColor,
                            modifier = Modifier.weight(1f)
                        )

                        InfoStatBox(
                            title = strings.remainingTime,
                            value = if (isExpired) strings.zeroDays else strings.daysUnit(daysLeft),
                            subtitle = if (isExpired) strings.urgentRenewNeeded else if (daysLeft > 5) strings.validSubscription else strings.endingSoon,
                            icon = Icons.Default.HourglassBottom,
                            iconColor = if (isExpired) neonRed else if (daysLeft > 5) brandCyan else neonYellow,
                            cardGradient = cardGradient,
                            borderStrokeColor = if (isExpired) neonRed.copy(alpha = 0.5f) else borderStrokeColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 2: Shamsi Expiry Date & Consumed Traffic
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val expiryShamsi = PersianDateHelper.formatToShamsiDate(
                            rawDate = session?.finishDate?.takeIf { it.isNotBlank() } ?: session?.shamsiFinishDate,
                            fallbackDays = daysLeft
                        )

                        InfoStatBox(
                            title = strings.finishDateSolar,
                            value = expiryShamsi,
                            subtitle = if (isExpired) strings.periodEnd else strings.jalaliCalendar,
                            icon = Icons.Default.CalendarMonth,
                            iconColor = if (isExpired) neonRed else Color(0xFFF59E0B),
                            cardGradient = cardGradient,
                            borderStrokeColor = if (isExpired) neonRed.copy(alpha = 0.5f) else borderStrokeColor,
                            modifier = Modifier.weight(1f)
                        )

                        // Calculate total consumed traffic
                        val serverConsumedMb = session?.consumedTrafficMb ?: 0L
                        val sessionConsumedMb = (rx + tx) / (1024 * 1024)
                        val totalConsumedMb = serverConsumedMb + sessionConsumedMb
                        val consumedDisplay = if (totalConsumedMb >= 1024) {
                            String.format(Locale.getDefault(), "%.1f GB", totalConsumedMb / 1024.0)
                        } else {
                            "$totalConsumedMb MB"
                        }
                        val totalLimitMb = session?.totalTrafficMb ?: 0L
                        val trafficSub = if (totalLimitMb > 0) {
                            strings.trafficLimit(if (totalLimitMb >= 1024) String.format(Locale.getDefault(), "%.0f GB", totalLimitMb / 1024.0) else "$totalLimitMb MB")
                        } else {
                            strings.unlimitedTraffic
                        }

                        InfoStatBox(
                            title = strings.trafficUsage,
                            value = consumedDisplay,
                            subtitle = trafficSub,
                            icon = Icons.Default.DataUsage,
                            iconColor = neonGreen,
                            cardGradient = cardGradient,
                            borderStrokeColor = borderStrokeColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Battery Optimization & Background Stability Card (Xiaomi, Samsung, Huawei, etc.)
                if (!isBatteryExempt && !isBatteryCardDismissed) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1), RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF0A0F1D) else Color(0xFFFFFFFF)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            prefs.edit().putBoolean("battery_card_dismissed", true).apply()
                                            isBatteryCardDismissed = true
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = strings.close,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                                            prefs.edit().putBoolean("battery_card_dismissed", true).apply()
                                            isBatteryCardDismissed = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = brandCyan),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = strings.batteryCardFix,
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = strings.batteryCardTitle,
                                            color = if (isDark) Color.White else Color(0xFF0F172A),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = strings.batteryCardSubtitle,
                                            color = neonOrange,
                                            fontSize = 10.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(neonGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BatteryChargingFull,
                                            contentDescription = null,
                                            tint = neonGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Feature explanation tags
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = strings.batteryCardDesc,
                                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = strings.batteryCardCpu,
                                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = strings.batteryCardAndroid,
                                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // --- DIALOG: Renewal Popup Dialog ---
    if (showRenewDialog) {
        val expiryShamsi = PersianDateHelper.formatToShamsiDate(
            rawDate = session?.finishDate?.takeIf { it.isNotBlank() } ?: session?.shamsiFinishDate,
            fallbackDays = daysLeft
        )

        AlertDialog(
            onDismissRequest = { showRenewDialog = false },
            containerColor = if (isDark) Color(0xFF0D1527) else Color(0xFFFFFFFF),
            shape = RoundedCornerShape(24.dp),
            title = null,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Glowing Alert / Subscription Icon
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                (if (isExpired) neonRed else brandCyan).copy(alpha = 0.15f)
                            )
                            .border(
                                1.5.dp,
                                (if (isExpired) neonRed else brandCyan).copy(alpha = 0.6f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isExpired) Icons.Default.LockClock else Icons.Default.WorkspacePremium,
                            contentDescription = "Subscription status",
                            tint = if (isExpired) neonRed else brandCyan,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (isExpired) strings.subscriptionExpiredTitleModal else strings.storeAndRenewTitleModal,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isExpired)
                            strings.subscriptionExpiredDescModal
                        else
                            strings.subscriptionActiveDescModal,
                        color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Account Summary Card inside Dialog
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF162036) else Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = session?.username ?: strings.noAccount,
                                    color = if (isDark) Color.White else Color(0xFF0F172A),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(strings.usernameFieldLabel, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), fontSize = 12.sp)
                            }

                            HorizontalDivider(color = if (isDark) Color(0xFF263554) else Color(0xFFCBD5E1), thickness = 0.5.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = expiryShamsi,
                                    color = if (isExpired) neonRed else neonGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(strings.finishDateFieldLabel, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), fontSize = 12.sp)
                            }

                            HorizontalDivider(color = if (isDark) Color(0xFF263554) else Color(0xFFCBD5E1), thickness = 0.5.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isExpired) strings.expired else strings.daysRemainingUnit(daysLeft),
                                    color = if (isExpired) neonRed else brandCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(strings.accountStatusFieldLabel, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Primary Action: Redeem License
                    Button(
                        onClick = {
                            showRenewDialog = false
                            viewModel.resetRedeemLicenseState()
                            showRedeemLicenseDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_redeem_voucher_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.renewWithLicenseCode,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary Action: Open Store
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gmb-net.ir"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, strings.errorOpeningBrowser, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("dialog_buy_voucher_web_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFEFF6FF)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.enterStore,
                            color = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Third Action: Refresh account data
                    OutlinedButton(
                        onClick = {
                            viewModel.refreshUserData()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.recheckAccountStatus,
                            color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    TextButton(
                        onClick = { showRenewDialog = false }
                    ) {
                        Text(strings.close, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {}
        )
    }

    // --- DIALOG: Redeem License / Voucher Dialog ---
    if (showRedeemLicenseDialog) {
        var licenseCodeInput by remember { mutableStateOf("") }
        val clipboardManager = LocalClipboardManager.current
        val isLoading = redeemLicenseState is RedeemLicenseUiState.Loading

        AlertDialog(
            onDismissRequest = {
                if (!isLoading) {
                    showRedeemLicenseDialog = false
                    viewModel.resetRedeemLicenseState()
                }
            },
            containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF),
            shape = RoundedCornerShape(22.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            if (!isLoading) {
                                showRedeemLicenseDialog = false
                                viewModel.resetRedeemLicenseState()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.close,
                            tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = strings.renewWithVoucherDialogTitle,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ConfirmationNumber,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = strings.enterVoucherPrompt,
                        color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Right,
                        lineHeight = 19.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Username indicator
                    session?.username?.let { u ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = u,
                                color = brandCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = strings.activeAccountLabel,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // License Code Input Field
                    OutlinedTextField(
                        value = licenseCodeInput,
                        onValueChange = { input ->
                            licenseCodeInput = input.uppercase(Locale.ENGLISH)
                            if (redeemLicenseState !is RedeemLicenseUiState.Idle && redeemLicenseState !is RedeemLicenseUiState.Loading) {
                                viewModel.resetRedeemLicenseState()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("license_code_input"),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            textAlign = TextAlign.Center,
                            letterSpacing = 2.sp
                        ),
                        placeholder = {
                            Text(
                                text = strings.voucherExamplePlaceholder,
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        singleLine = true,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1),
                            focusedContainerColor = if (isDark) Color(0xFF090E17) else Color(0xFFF8FAFC),
                            unfocusedContainerColor = if (isDark) Color(0xFF090E17) else Color(0xFFF8FAFC)
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (licenseCodeInput.isNotEmpty()) {
                                    IconButton(
                                        onClick = { licenseCodeInput = "" },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = strings.clearField,
                                            tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val clipText = clipboardManager.getText()?.text
                                        if (!clipText.isNullOrBlank()) {
                                            licenseCodeInput = clipText.trim().uppercase(Locale.ENGLISH)
                                            if (redeemLicenseState !is RedeemLicenseUiState.Idle) {
                                                viewModel.resetRedeemLicenseState()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("paste_voucher_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = strings.pasteFromClipboard,
                                        tint = brandCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Direct link to purchase voucher on gmb-net.ir if user doesn't have one
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gmb-net.ir"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, strings.errorOpeningBrowser, Toast.LENGTH_SHORT).show()
                                }
                            }
                            .background(if (isDark) Color(0xFF0F1B33) else Color(0xFFEFF6FF), RoundedCornerShape(10.dp))
                            .border(BorderStroke(1.dp, if (isDark) Color(0xFF2563EB).copy(alpha = 0.4f) else Color(0xFF3B82F6).copy(alpha = 0.5f)), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.dontHaveLicensePrompt,
                            color = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Status Messages (Loading, Success, Error)
                    when (val state = redeemLicenseState) {
                        is RedeemLicenseUiState.Loading -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color(0xFF10B981),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = strings.validatingLicenseOnServer,
                                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        is RedeemLicenseUiState.Success -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isDark) Color(0xFF064E3B).copy(alpha = 0.5f) else Color(0xFFECFDF5), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = state.message,
                                        color = if (isDark) Color(0xFFA7F3D0) else Color(0xFF065F46),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                if (state.daysAdded != null && state.daysAdded > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = strings.creditAddedDays(state.daysAdded),
                                        color = if (isDark) Color.White else Color(0xFF065F46),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (state.volumeGBAdded != null && state.volumeGBAdded > 0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = strings.trafficAddedGB(state.volumeGBAdded),
                                        color = if (isDark) Color.White else Color(0xFF065F46),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        is RedeemLicenseUiState.Error -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isDark) Color(0xFF450A0A).copy(alpha = 0.5f) else Color(0xFFFEF2F2), RoundedCornerShape(12.dp))
                                    .border(1.dp, neonRed.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = neonRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = state.errorMessage,
                                    color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        else -> {}
                    }

                    // Action Buttons
                    if (redeemLicenseState is RedeemLicenseUiState.Success) {
                        Button(
                            onClick = {
                                showRedeemLicenseDialog = false
                                viewModel.resetRedeemLicenseState()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("close_success_redeem_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = strings.confirmAndClose,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.redeemLicense(licenseCodeInput)
                            },
                            enabled = !isLoading && licenseCodeInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("confirm_redeem_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                disabledContainerColor = Color(0xFF10B981).copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.renewingProgress,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.confirmAndRenew,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                if (!isLoading) {
                                    showRedeemLicenseDialog = false
                                    viewModel.resetRedeemLicenseState()
                                }
                            }
                        ) {
                            Text(strings.cancelAction, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // --- DIALOG: Settings Dialog (Support, Speed Test, Sanitized Logs, Logout) ---
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            activeSession = activeSession,
            status = status,
            onDismiss = { showSettingsDialog = false },
            onLogoutRequest = {
                showLogoutDialog = true
            }
        )
    }

    // --- DIALOG: Logout Confirmation Dialog ---
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = if (isDark) Color(0xFF0D1527) else Color(0xFFFFFFFF),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = strings.logoutDialogTitle,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = strings.logoutDialogMessage,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Right,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        showSettingsDialog = false
                        viewModel.logout(context)
                        onNavigateBackToLogin()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(strings.logout, color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(strings.cancelAction, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                }
            }
        )
    }

    // --- DIALOG: In-App Update Dialog ---
    if (availableUpdate != null) {
        val update = availableUpdate!!
        val progress = updateDownloadProgress

        AlertDialog(
            onDismissRequest = {
                if (!update.isForceUpdate && progress == null) {
                    viewModel.dismissUpdateDialog()
                }
            },
            containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF),
            shape = RoundedCornerShape(22.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (update.isForceUpdate) neonRed.copy(alpha = 0.2f) else brandCyan.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (update.isForceUpdate) neonRed else brandCyan)
                    ) {
                        Text(
                            text = if (update.isForceUpdate) strings.updateForceLabel else strings.updateVersionLabel(update.latestVersionName),
                            color = if (update.isForceUpdate) neonRed else brandCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = strings.updateTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = strings.updateDesc,
                        color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Right,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Changelog Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF1E293B).copy(alpha = 0.6f) else Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = strings.updateChangelogTitle,
                                color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = update.changelog,
                                color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                                fontSize = 12.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Download Progress Indicator
                    if (progress != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = String.format(Locale.getDefault(), "%.0f%%", progress * 100),
                                    color = brandCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.updateDownloadingProgress,
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = brandCyan,
                                trackColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (progress == null) {
                    Button(
                        onClick = {
                            viewModel.downloadAndInstallUpdate(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = brandCyan),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.updateDirectInstall,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            dismissButton = {
                if (!update.isForceUpdate && progress == null) {
                    TextButton(
                        onClick = { viewModel.dismissUpdateDialog() }
                    ) {
                        Text(text = strings.updateRemindLater, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), fontSize = 12.sp)
                    }
                }
            }
        )
    }
}

@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    color: Color
) {
    val isDark = com.example.ui.theme.AppTheme.colors.isDark
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = value,
            color = if (isDark) Color.White else Color(0xFF0F172A),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val suffix = "KMGTPE"[exp - 1] + "B"
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, exp.toDouble()), suffix)
}

@Composable
fun InfoStatBox(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    cardGradient: Brush,
    borderStrokeColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = com.example.ui.theme.AppTheme.colors
    val isDark = colors.isDark

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) Color(0xFF0D172B) else Color(0xFFFFFFFF),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    iconColor.copy(alpha = 0.45f),
                    if (isDark) Color(0x1FFFFFFF) else Color(0x1A000000),
                    iconColor.copy(alpha = 0.15f)
                )
            )
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconColor.copy(alpha = 0.15f))
                        .border(1.dp, iconColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = title,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                color = if (isDark) Color.White else Color(0xFF0F172A),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                textAlign = TextAlign.Right
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = iconColor.copy(alpha = 0.12f),
                border = BorderStroke(0.5.dp, iconColor.copy(alpha = 0.25f))
            ) {
                Text(
                    text = subtitle,
                    color = iconColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Right,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

