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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import java.util.Locale

@Composable
fun MainScreen(
    viewModel: VpnViewModel,
    onNavigateBackToLogin: () -> Unit,
    onNavigateToPurchase: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

    // Dialog state for renewal, voucher redeem, and logout
    var showRenewDialog by remember { mutableStateOf(false) }
    var showRedeemLicenseDialog by remember { mutableStateOf(false) }
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

    // Modern High-Tech Space-Dark Theme Colors
    val deepNavyBg = Color(0xFF070B14)
    val borderStrokeColor = Color(0xFF1E293B)
    val brandCyan = Color(0xFF38BDF8)
    val brandPurple = Color(0xFFA855F7)
    val neonGreen = Color(0xFF10B981)
    val neonOrange = Color(0xFFF97316)
    val neonRed = Color(0xFFEF4444)
    val neonYellow = Color(0xFFFACC15)

    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F182D),
            Color(0xFF0A0F1E)
        )
    )

    // Animated aura based on connection status or expiration
    val auraColor by animateColorAsState(
        targetValue = when {
            isExpired -> neonRed
            status == VpnStatus.CONNECTING -> neonYellow
            status == VpnStatus.CONNECTED -> neonGreen
            status == VpnStatus.ERROR -> neonRed
            else -> brandCyan
        },
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "aura_color"
    )

    // Pulsing halo animation for connecting status
    val infiniteTransition = rememberInfiniteTransition(label = "halo_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Scaffold(
        containerColor = deepNavyBg,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(deepNavyBg)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logout button with confirmation dialog
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "خروج از حساب",
                            tint = Color(0xFF94A3B8)
                        )
                    }

                    // Centered App Identity
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.gmb_logo),
                            contentDescription = "GMB NET Logo",
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Text(
                            text = "GMB NET",
                            fontSize = 17.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isExpired) neonRed else if (status == VpnStatus.CONNECTED) neonGreen else brandCyan)
                        )
                    }

                    // Action button: Store & License Modal
                    IconButton(
                        onClick = { showRenewDialog = true },
                        modifier = Modifier.testTag("top_shop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = "فروشگاه و تمدید اشتراک",
                            tint = if (isExpired) neonRed else Color(0xFF38BDF8)
                        )
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
                            Color(0xFF0F172A),
                            deepNavyBg
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
                                        listOf(
                                            Color(0xFF3F0B11),
                                            Color(0xFF1E0A12)
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
                                        text = "تمدید اشتراک",
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
                                            text = "اشتراک شما به پایان رسیده است",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Right
                                        )
                                        Text(
                                            text = "اتصال مسدود است • لمس جهت تمدید آنلاین",
                                            color = Color(0xFFFCA5A5),
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
                                            contentDescription = "Expired notice",
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
                                .background(Color(0xFF2A1C08))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = { showRenewDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("تمدید سریع", color = neonYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "تنها $daysLeft روز از اشتراک شما باقی مانده است",
                                color = Color(0xFFFEF08A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Right
                            )
                        }
                    }
                }

                // 2. Central Connection Core Ring
                val buttonColor = when {
                    isExpired -> neonRed
                    status == VpnStatus.CONNECTED -> neonGreen
                    status == VpnStatus.CONNECTING -> neonYellow
                    status == VpnStatus.ERROR -> neonRed
                    else -> Color(0xFF475569)
                }

                val statusLabel = when {
                    isExpired -> "اشتراک منقضی شده است (اتصال مسدود)"
                    status == VpnStatus.CONNECTED -> "اتصال امن برقرار است"
                    status == VpnStatus.CONNECTING -> "در حال برقراری ارتباط با سرور..."
                    status == VpnStatus.ERROR -> "خطا در اتصال - جهت تلاش مجدد لمس کنید"
                    else -> "برای اتصال امن لمس کنید"
                }

                Column(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Outer Glow Halo
                    Box(
                        modifier = Modifier
                            .size(186.dp)
                            .clip(CircleShape)
                            .background(
                                if (isExpired) {
                                    neonRed.copy(alpha = 0.15f)
                                } else if (status == VpnStatus.CONNECTING) {
                                    neonYellow.copy(alpha = pulseAlpha * 0.25f)
                                } else if (status == VpnStatus.CONNECTED) {
                                    neonGreen.copy(alpha = 0.20f)
                                } else if (status == VpnStatus.ERROR) {
                                    neonRed.copy(alpha = 0.20f)
                                } else {
                                    Color.Transparent
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(174.dp)
                                .clip(CircleShape)
                                .background(buttonColor.copy(alpha = if (isExpired) 0.12f else if (status != VpnStatus.DISCONNECTED) 0.15f else 0.08f))
                                .border(
                                    width = if (isExpired || status != VpnStatus.DISCONNECTED) 3.dp else 2.dp,
                                    color = if (isExpired) neonRed.copy(alpha = 0.8f) else if (status != VpnStatus.DISCONNECTED) auraColor.copy(alpha = 0.8f) else borderStrokeColor,
                                    shape = CircleShape
                                )
                                .clickable {
                                    if (isExpired) {
                                        // Block connection and immediately open renewal popup
                                        showRenewDialog = true
                                    } else {
                                        if (status == VpnStatus.CONNECTED) {
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
                            Box(
                                modifier = Modifier
                                    .size(132.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isExpired -> Brush.radialGradient(
                                                listOf(neonRed.copy(alpha = 0.75f), Color(0xFF450A0A), Color(0xFF0A0F1E))
                                            )
                                            status == VpnStatus.CONNECTED -> Brush.radialGradient(
                                                listOf(neonGreen.copy(alpha = 0.9f), Color(0xFF064E3B), Color(0xFF0A0F1E))
                                            )
                                            status == VpnStatus.CONNECTING -> Brush.radialGradient(
                                                listOf(neonYellow.copy(alpha = 0.85f), Color(0xFF78350F), Color(0xFF0A0F1E))
                                            )
                                            status == VpnStatus.ERROR -> Brush.radialGradient(
                                                listOf(neonRed.copy(alpha = 0.85f), Color(0xFF7F1D1D), Color(0xFF0A0F1E))
                                            )
                                            else -> Brush.verticalGradient(
                                                listOf(Color(0xFF131B2E), Color(0xFF0A0F1E))
                                            )
                                        }
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isExpired || status != VpnStatus.DISCONNECTED) buttonColor else borderStrokeColor,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = when {
                                            isExpired -> Icons.Default.Lock
                                            status == VpnStatus.CONNECTED -> Icons.Default.CheckCircle
                                            status == VpnStatus.CONNECTING -> Icons.Default.Sync
                                            status == VpnStatus.ERROR -> Icons.Default.ErrorOutline
                                            else -> Icons.Default.PowerSettingsNew
                                        },
                                        contentDescription = "وضعیت اتصال",
                                        tint = if (isExpired) Color(0xFFFCA5A5) else if (status != VpnStatus.DISCONNECTED) Color.White else buttonColor,
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = when {
                                            isExpired -> "LOCKED"
                                            status == VpnStatus.CONNECTED -> "ON"
                                            status == VpnStatus.CONNECTING -> "WAIT"
                                            status == VpnStatus.ERROR -> "ERR"
                                            else -> "OFF"
                                        },
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = statusLabel,
                        color = if (isExpired) neonRed else if (status != VpnStatus.DISCONNECTED) auraColor else Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    // Protocol badge
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isExpired) "جهت فعال‌سازی، اشتراک را تمدید کنید" else "پروتکل اختصاصی • رمزگذاری پیشرفته",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                }

                // 3. Live Traffic & Duration Counters
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderStrokeColor, RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cardGradient)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            StatItem(
                                icon = Icons.Default.ArrowDownward,
                                title = "دانلود",
                                value = formatBytes(rx),
                                color = brandCyan
                            )
                            StatItem(
                                icon = Icons.Default.Timer,
                                title = "مدت زمان",
                                value = formatSeconds(secondsElapsed),
                                color = brandPurple
                            )
                            StatItem(
                                icon = Icons.Default.ArrowUpward,
                                title = "آپلود",
                                value = formatBytes(tx),
                                color = neonOrange
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
                            title = "نام کاربری",
                            value = session?.username ?: "بدون حساب",
                            subtitle = if (isExpired) "منقضی شده" else "اشتراک فعال",
                            icon = Icons.Default.AccountCircle,
                            iconColor = if (isExpired) neonRed else brandPurple,
                            cardGradient = cardGradient,
                            borderStrokeColor = if (isExpired) neonRed.copy(alpha = 0.5f) else borderStrokeColor,
                            modifier = Modifier.weight(1f)
                        )

                        InfoStatBox(
                            title = "زمان باقی‌مانده",
                            value = if (isExpired) "۰ روز" else "$daysLeft روز",
                            subtitle = if (isExpired) "نیازمند تمدید فوری" else if (daysLeft > 5) "اعتبار معتبر" else "رو به پایان",
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
                            title = "تاریخ پایان (شمسی)",
                            value = expiryShamsi,
                            subtitle = if (isExpired) "پایان دوره" else "تقویم جلالی",
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
                            "سقف: ${if (totalLimitMb >= 1024) String.format(Locale.getDefault(), "%.0f GB", totalLimitMb / 1024.0) else "$totalLimitMb MB"}"
                        } else {
                            "ترافیک نامحدود"
                        }

                        InfoStatBox(
                            title = "حجم مصرفی",
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
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F1D)),
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
                                            contentDescription = "بستن",
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
                                            text = "رفع محدودیت باتری",
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
                                            text = "پایداری پس‌زمینه و مصرف بهینه",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "جلوگیری از قطع اتصال هنگام خاموشی صفحه",
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
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "MTU ۱۴۰۰ ضدافت‌سرعت",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "پردازش کم‌مصرف CPU",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "سازگار با اندروید ۷ تا ۱۵",
                                        color = Color(0xFF94A3B8),
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
            containerColor = Color(0xFF0D1527),
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
                        text = if (isExpired) "پایان اعتبار اشتراک" else "فروشگاه و تمدید اشتراک",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isExpired)
                            "اعتبار اشتراک شما به پایان رسیده است. جهت تمدید با کد لایسنس یا خرید اشتراک از فروشگاه اقدام فرمایید."
                        else
                            "جهت خرید اشتراک یا تمدید با کد لایسنس، گزینه مورد نظر را انتخاب کنید.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Account Summary Card inside Dialog
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF162036)),
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
                                    text = session?.username ?: "بدون حساب",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("نام کاربری:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }

                            HorizontalDivider(color = Color(0xFF263554), thickness = 0.5.dp)

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
                                Text("تاریخ پایان:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }

                            HorizontalDivider(color = Color(0xFF263554), thickness = 0.5.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isExpired) "منقضی شده" else "$daysLeft روز باقی‌مانده",
                                    color = if (isExpired) neonRed else brandCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("وضعیت حساب:", color = Color(0xFF94A3B8), fontSize = 12.sp)
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
                            text = "تمدید با کد لایسنس",
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
                                Toast.makeText(context, "خطا در باز کردن مرورگر", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("dialog_buy_voucher_web_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF0F172A)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ورود به فروشگاه",
                            color = Color(0xFF93C5FD),
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
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "استعلام مجدد وضعیت حساب",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    TextButton(
                        onClick = { showRenewDialog = false }
                    ) {
                        Text("بستن", color = Color(0xFF94A3B8), fontSize = 12.sp)
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
            containerColor = Color(0xFF0F172A),
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
                            contentDescription = "بستن",
                            tint = Color(0xFF94A3B8)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "تمدید با کد لایسنس / ووچر",
                            color = Color.White,
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
                        text = "کد لایسنس یا ووچر خریداری‌شده را جهت تمدید اعتبار اشتراک در کادر زیر وارد نمایید:",
                        color = Color(0xFFCBD5E1),
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
                                .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
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
                                text = "حساب کاربری فعال:",
                                color = Color(0xFF94A3B8),
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
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            letterSpacing = 2.sp
                        ),
                        placeholder = {
                            Text(
                                text = "مثال: GMB-VOUCHER-XXXX",
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
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF090E17),
                            unfocusedContainerColor = Color(0xFF090E17)
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
                                            contentDescription = "پاک کردن",
                                            tint = Color(0xFF94A3B8),
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
                                        contentDescription = "جای‌گذاری از کلیپ‌بورد",
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
                                    Toast.makeText(context, "خطا در باز کردن مرورگر", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .background(Color(0xFF0F1B33), RoundedCornerShape(10.dp))
                            .border(BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.4f)), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "کد لایسنس ندارید؟ خرید آنی از فروشگاه",
                            color = Color(0xFF93C5FD),
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
                                    .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
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
                                    text = "در حال ارسال و اعتبارسنجی لایسنس در سرور...",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        is RedeemLicenseUiState.Success -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF064E3B).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
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
                                        color = Color(0xFFA7F3D0),
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
                                        text = "اعتبار اضافه شده: ${state.daysAdded} روز",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (state.volumeGBAdded != null && state.volumeGBAdded > 0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "حجم اضافه شده: ${state.volumeGBAdded} گیگابایت",
                                        color = Color.White,
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
                                    .background(Color(0xFF450A0A).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
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
                                    color = Color(0xFFFCA5A5),
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
                                text = "تایید و بستن",
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
                                    text = "در حال تمدید...",
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
                                    text = "تایید و تمدید اشتراک",
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
                            Text("انصراف", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // --- DIALOG: Logout Confirmation Dialog ---
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = Color(0xFF0D1527),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "خروج از حساب کاربری",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "آیا اطمینان دارید که می‌خواهید از حساب خارج شوید؟ در صورت خروج، اتصال شما قطع خواهد شد.",
                    color = Color(0xFFCBD5E1),
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
                        viewModel.logout(context)
                        onNavigateBackToLogin()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("خروج", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("انصراف", color = Color(0xFF94A3B8))
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
            containerColor = Color(0xFF0F172A),
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
                            text = if (update.isForceUpdate) "بروزرسانی الزامی" else "نسخه ${update.latestVersionName}",
                            color = if (update.isForceUpdate) neonRed else brandCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "بروزرسانی GMB NET",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "نسخه جدیدی از برنامه با بهینه‌سازی سرعت و پایداری منتشر شد.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Right,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Changelog Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "تغییرات این نسخه:",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = update.changelog,
                                color = Color(0xFFE2E8F0),
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
                                    text = "در حال دانلود فایل نصب APK...",
                                    color = Color(0xFF94A3B8),
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
                                trackColor = Color(0xFF1E293B)
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
                            text = "دانلود و نصب مستقیم",
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
                        Text(text = "بعداً یادآوری کن", color = Color(0xFF94A3B8), fontSize = 12.sp)
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = value,
            color = Color.White,
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
    Card(
        modifier = modifier
            .border(1.dp, borderStrokeColor, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradient)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(iconColor.copy(alpha = 0.12f))
                            .border(1.dp, iconColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = iconColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Text(
                        text = title,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Right
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    color = iconColor.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Right,
                    maxLines = 1
                )
            }
        }
    }
}

