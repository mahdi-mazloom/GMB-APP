package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.database.UserSession
import com.example.data.database.VpnLog
import com.example.util.LogSanitizer
import com.example.util.QualityGrade
import com.example.util.SpeedTestResult
import com.example.util.SpeedTestStage
import com.example.vpn.VpnStatus
import com.example.viewmodel.VpnViewModel
import com.example.ui.theme.AppThemeMode
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.LocalAppStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Theme colors matching Cyber Obsidian Design System and Modern Light Mode
private val deepDarkBg: Color
    @Composable
    get() = com.example.ui.theme.AppTheme.colors.background

private val cardBg: Color
    @Composable
    get() = com.example.ui.theme.AppTheme.colors.surfaceElevated

private val cardBorder: Color
    @Composable
    get() = com.example.ui.theme.AppTheme.colors.cardBorder

private val brandCyan: Color
    @Composable
    get() = com.example.ui.theme.AppTheme.colors.brandCyan

private val skyCyan: Color
    @Composable
    get() = com.example.ui.theme.AppTheme.colors.electricSky

private val neonRed: Color
    @Composable
    get() = com.example.ui.theme.AppTheme.colors.neonRed

private val neonGreen: Color
    @Composable
    get() = com.example.ui.theme.AppTheme.colors.neonGreen

private val amberWarning: Color
    @Composable
    get() = com.example.ui.theme.AppTheme.colors.neonYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: VpnViewModel,
    activeSession: UserSession?,
    status: VpnStatus,
    onDismiss: () -> Unit,
    onLogoutRequest: () -> Unit
) {
    val context = LocalContext.current
    val isDark = com.example.ui.theme.AppTheme.colors.isDark
    val strings = LocalAppStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(strings.tabSettingsAndAccount, strings.tabSpeedTest, strings.tabLogs)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(26.dp))
                .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(26.dp)),
            color = deepDarkBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                if (isDark) listOf(Color(0xFF0E1A33), Color(0xFF0A1224)) else listOf(Color(0xFFE2E8F0), Color(0xFFF1F5F9))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF131F35) else Color(0xFFE2E8F0))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.close,
                            tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = strings.settingsTitle,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = skyCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Tab Header
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = if (isDark) Color(0xFF0A1224) else Color(0xFFF1F5F9),
                    contentColor = skyCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = brandCyan,
                            height = 3.dp
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == index) brandCyan else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                                )
                            },
                            icon = {
                                val icon = when (index) {
                                    0 -> Icons.Default.HeadsetMic
                                    1 -> Icons.Default.Speed
                                    else -> Icons.Default.Terminal
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (selectedTab == index) brandCyan else (if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }

                HorizontalDivider(color = if (isDark) Color(0x1F38BDF8) else Color(0xFFCBD5E1))

                // Content View based on Tab
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    when (selectedTab) {
                        0 -> SupportAndAccountSection(
                            viewModel = viewModel,
                            activeSession = activeSession,
                            status = status,
                            onLogoutRequest = onLogoutRequest,
                            onOpenSpeedTest = { selectedTab = 1 },
                            onOpenLogs = { selectedTab = 2 }
                        )
                        1 -> SpeedTestSection(viewModel = viewModel, status = status)
                        2 -> LogsSection(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 1. SUPPORT & ACCOUNT SECTION
// -------------------------------------------------------------
@Composable
private fun SupportAndAccountSection(
    viewModel: VpnViewModel,
    activeSession: UserSession?,
    status: VpnStatus,
    onLogoutRequest: () -> Unit,
    onOpenSpeedTest: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val isDark = com.example.ui.theme.AppTheme.colors.isDark
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // User Account Status Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            border = BorderStroke(1.dp, if (isDark) Color(0x2E38BDF8) else Color(0xFFCBD5E1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (status == VpnStatus.CONNECTED) neonGreen.copy(alpha = 0.15f) else (if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (status == VpnStatus.CONNECTED) neonGreen else Color(0xFF94A3B8))
                            )
                            Text(
                                text = if (status == VpnStatus.CONNECTED) strings.statusConnected else strings.statusDisconnected,
                                fontSize = 11.sp,
                                color = if (status == VpnStatus.CONNECTED) neonGreen else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Username & Avatar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = activeSession?.username ?: strings.userGreetingDefault,
                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.activeGmbAccount,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF1E3A5F) else Color(0xFFE0F2FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = skyCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = if (isDark) Color(0x1F38BDF8) else Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(10.dp))

                // Logout Action Button
                Button(
                    onClick = onLogoutRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = neonRed.copy(alpha = 0.16f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, neonRed.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("settings_logout_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.logoutButton,
                            color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = strings.logoutButton,
                            tint = neonRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Language Selection Section (Persian, English, Iraqi Arabic)
        // -------------------------------------------------------------
        val currentLanguage by viewModel.appLanguage.collectAsState()

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            border = BorderStroke(1.dp, if (isDark) Color(0x2E38BDF8) else Color(0xFFCBD5E1)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_language_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title and Active Language Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeLangBadge = when (currentLanguage) {
                        AppLanguage.FA -> "${currentLanguage.flagEmoji} ${strings.langFaTitle}"
                        AppLanguage.EN -> "${currentLanguage.flagEmoji} ${strings.langEnTitle}"
                        AppLanguage.AR_IQ -> "${currentLanguage.flagEmoji} ${strings.langArIqTitle}"
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = brandCyan.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = activeLangBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = brandCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = strings.languageSectionTitle,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF162544) else Color(0xFFE0F2FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = brandCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Text(
                    text = strings.languageSectionSubtitle,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                // 3 Language Options: Persian (فارسی), English, Iraqi Arabic (العربية - العراق)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val languages = listOf(
                        AppLanguage.FA,
                        AppLanguage.EN,
                        AppLanguage.AR_IQ
                    )

                    languages.forEach { lang ->
                        val isSelected = currentLanguage == lang
                        val animatedBorderColor by animateColorAsState(
                            targetValue = if (isSelected) brandCyan else (if (isDark) Color(0x2238BDF8) else Color(0xFFCBD5E1)),
                            label = "langBorder_${lang.code}"
                        )
                        val animatedBgColor by animateColorAsState(
                            targetValue = when {
                                isSelected && isDark -> Color(0xFF102A45)
                                isSelected && !isDark -> Color(0xFFE0F2FE)
                                !isSelected && isDark -> Color(0xFF0B1424)
                                else -> Color(0xFFF1F5F9)
                            },
                            label = "langBg_${lang.code}"
                        )

                        Surface(
                            onClick = {
                                viewModel.setAppLanguage(lang)
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = animatedBgColor,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = animatedBorderColor
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("language_option_${lang.code}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) brandCyan.copy(alpha = 0.22f) else (if (isDark) Color(0xFF142036) else Color(0xFFE2E8F0))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lang.flagEmoji,
                                        fontSize = 18.sp
                                    )
                                }

                                Text(
                                    text = lang.titleNative,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) (if (isDark) Color.White else Color(0xFF0369A1)) else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = when (lang) {
                                        AppLanguage.FA -> strings.langFaDesc
                                        AppLanguage.EN -> strings.langEnDesc
                                        AppLanguage.AR_IQ -> strings.langArIqDesc
                                    },
                                    fontSize = 9.sp,
                                    color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = strings.activeBadge,
                                            tint = brandCyan,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = strings.activeBadge,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = brandCyan
                                        )
                                    } else {
                                        Text(
                                            text = "—",
                                            fontSize = 10.sp,
                                            color = if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Theme & Dark Mode Selection Section
        // -------------------------------------------------------------
        val currentThemeMode by viewModel.appThemeMode.collectAsState()

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            border = BorderStroke(1.dp, if (isDark) Color(0x2E38BDF8) else Color(0xFFCBD5E1)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_dark_mode_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title and Active Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = when (currentThemeMode) {
                        AppThemeMode.LIGHT -> strings.themeLightBadge
                        AppThemeMode.DARK -> strings.themeDarkBadge
                        AppThemeMode.SYSTEM -> strings.themeSystemBadge
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = skyCyan.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = skyCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = strings.themeSectionTitle,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF162544) else Color(0xFFE0F2FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (currentThemeMode) {
                                    AppThemeMode.LIGHT -> Icons.Default.LightMode
                                    AppThemeMode.DARK -> Icons.Default.DarkMode
                                    AppThemeMode.SYSTEM -> Icons.Default.AutoAwesome
                                },
                                contentDescription = null,
                                tint = brandCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Text(
                    text = strings.themeSectionSubtitle,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                // 3 Options: Light Mode, Dark Mode, Automatic
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(
                        Triple(AppThemeMode.LIGHT, strings.themeLightTitle, Icons.Default.LightMode),
                        Triple(AppThemeMode.DARK, strings.themeDarkTitle, Icons.Default.DarkMode),
                        Triple(AppThemeMode.SYSTEM, strings.themeSystemTitle, Icons.Default.AutoAwesome)
                    )

                    options.forEach { (mode, title, icon) ->
                        val isSelected = currentThemeMode == mode
                        val animatedBorderColor by animateColorAsState(
                            targetValue = if (isSelected) brandCyan else (if (isDark) Color(0x2238BDF8) else Color(0xFFCBD5E1)),
                            label = "themeBorder_${mode.key}"
                        )
                        val animatedBgColor by animateColorAsState(
                            targetValue = when {
                                isSelected && isDark -> Color(0xFF102A45)
                                isSelected && !isDark -> Color(0xFFE0F2FE)
                                !isSelected && isDark -> Color(0xFF0B1424)
                                else -> Color(0xFFF1F5F9)
                            },
                            label = "themeBg_${mode.key}"
                        )

                        Surface(
                            onClick = {
                                viewModel.setAppThemeMode(mode)
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = animatedBgColor,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = animatedBorderColor
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_option_${mode.key}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) brandCyan.copy(alpha = 0.22f) else (if (isDark) Color(0xFF142036) else Color(0xFFE2E8F0))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = title,
                                        tint = if (isSelected) brandCyan else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) (if (isDark) Color.White else Color(0xFF0369A1)) else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                                    textAlign = TextAlign.Center
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = strings.activeBadge,
                                            tint = brandCyan,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = strings.activeBadge,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = brandCyan
                                        )
                                    } else {
                                        Text(
                                            text = when (mode) {
                                                AppThemeMode.LIGHT -> strings.themeLightTitle
                                                AppThemeMode.DARK -> strings.themeDarkTitle
                                                AppThemeMode.SYSTEM -> strings.themeSystemTitle
                                            },
                                            fontSize = 10.sp,
                                            color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Tools Row (Speed Test & Logs shortcut)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onOpenSpeedTest,
                shape = RoundedCornerShape(16.dp),
                color = cardBg,
                border = BorderStroke(1.dp, if (isDark) Color(0x2E38BDF8) else Color(0xFFCBD5E1)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0xFF0F2B48) else Color(0xFFE0F2FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = brandCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = strings.speedTestShortcutTitle,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.speedTestShortcutDesc,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Surface(
                onClick = onOpenLogs,
                shape = RoundedCornerShape(16.dp),
                color = cardBg,
                border = BorderStroke(1.dp, if (isDark) Color(0x2E38BDF8) else Color(0xFFCBD5E1)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0xFF162544) else Color(0xFFF3E8FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = strings.logsShortcutTitle,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.logsShortcutDesc,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Support Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            border = BorderStroke(1.dp, if (isDark) Color(0x2E38BDF8) else Color(0xFFCBD5E1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.supportSectionSubtitle,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.supportSectionTitle,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.HeadsetMic,
                            contentDescription = null,
                            tint = skyCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Telegram Support Button
                SupportItemButton(
                    title = strings.telegramSupportTitle,
                    subtitle = strings.telegramSupportDesc,
                    icon = Icons.Default.Send,
                    iconColor = Color(0xFF38BDF8),
                    isDark = isDark,
                    onAction = {
                        openUrl(context, "https://t.me/dr_database", strings)
                    },
                    onCopy = {
                        clipboard.setText(AnnotatedString("@dr_database"))
                        Toast.makeText(context, "@dr_database", Toast.LENGTH_SHORT).show()
                    }
                )

                // Telegram Official Channel Button
                SupportItemButton(
                    title = strings.telegramChannelTitle,
                    subtitle = strings.telegramChannelDesc,
                    icon = Icons.Default.Bolt,
                    iconColor = brandCyan,
                    isDark = isDark,
                    onAction = {
                        openUrl(context, "https://t.me/vpn_gmb", strings)
                    },
                    onCopy = {
                        clipboard.setText(AnnotatedString("@vpn_gmb"))
                        Toast.makeText(context, "@vpn_gmb", Toast.LENGTH_SHORT).show()
                    }
                )

                // Website Official Portal
                SupportItemButton(
                    title = strings.officialWebsiteTitle,
                    subtitle = strings.officialWebsiteDesc,
                    icon = Icons.Default.Language,
                    iconColor = Color(0xFF60A5FA),
                    isDark = isDark,
                    onAction = {
                        openUrl(context, "https://gmb-net.ir", strings)
                    },
                    onCopy = {
                        clipboard.setText(AnnotatedString("https://gmb-net.ir"))
                        Toast.makeText(context, "gmb-net.ir", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Help & Troubleshooting Tips
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isDark) Color(0xFF091322) else Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, if (isDark) Color(0x1F38BDF8) else Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Help,
                        contentDescription = null,
                        tint = amberWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.helpTipsTitle,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = strings.helpTipsBody,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    fontSize = 11.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun SupportItemButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    isDark: Boolean = true,
    onAction: () -> Unit,
    onCopy: () -> Unit
) {
    val strings = LocalAppStrings.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color(0xFF0B1424) else Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, if (isDark) Color(0x2638BDF8) else Color(0xFFCBD5E1)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Copy action button
            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF14223A) else Color(0xFFE2E8F0))
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = strings.copyLabel,
                    tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    modifier = Modifier.size(15.dp)
                )
            }

            // Main Info & Click
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = title,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right
                    )
                    Text(
                        text = subtitle,
                        color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Right
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
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
            }
        }
    }
}

// -------------------------------------------------------------
// 2. SPEED TEST SECTION
// -------------------------------------------------------------
@Composable
private fun SpeedTestSection(
    viewModel: VpnViewModel,
    status: VpnStatus
) {
    val isDark = com.example.ui.theme.AppTheme.colors.isDark
    val strings = LocalAppStrings.current
    val speedState by viewModel.speedTestState.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Speedometer / Gauge Main Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = cardBg,
            border = BorderStroke(1.dp, if (isDark) Color(0x3338BDF8) else Color(0xFFCBD5E1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Central Gauge Ring
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                if (isDark) {
                                    listOf(
                                        Color(0xFF0F2642),
                                        Color(0xFF081224)
                                    )
                                } else {
                                    listOf(
                                        Color(0xFFE0F2FE),
                                        Color(0xFFF1F5F9)
                                    )
                                }
                            )
                        )
                        .border(
                            BorderStroke(
                                3.dp,
                                if (speedState.isRunning) brandCyan.copy(alpha = pulseAlpha) else (if (isDark) Color(0x4038BDF8) else Color(0xFFBAE6FD))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (speedState.downloadSpeedMbps != null) {
                                String.format(Locale.US, "%.1f", speedState.downloadSpeedMbps)
                            } else if (speedState.isRunning) {
                                "..."
                            } else {
                                "0.0"
                            },
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${strings.speedTestUnit} ${strings.downloadSpeedLabel}",
                            fontSize = 11.sp,
                            color = brandCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Indicator when running
                if (speedState.isRunning) {
                    LinearProgressIndicator(
                        progress = { speedState.progress },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = brandCyan,
                        trackColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Stage text
                Text(
                    text = speedState.stageText,
                    color = if (speedState.isRunning) skyCyan else (if (isDark) Color(0xFFCBD5E1) else Color(0xFF64748B)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Metrics Grid (Ping, Jitter, VPN Status)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Ping Metric
            MetricCard(
                modifier = Modifier.weight(1f),
                title = strings.speedTestPing,
                value = if (speedState.pingMs != null) "${speedState.pingMs} ms" else "--",
                icon = Icons.Default.NetworkCheck,
                isDark = isDark,
                valueColor = when {
                    speedState.pingMs == null -> Color(0xFF94A3B8)
                    speedState.pingMs!! < 90 -> neonGreen
                    speedState.pingMs!! < 170 -> amberWarning
                    else -> neonRed
                }
            )

            // Jitter Metric
            MetricCard(
                modifier = Modifier.weight(1f),
                title = strings.speedTestJitter,
                value = if (speedState.jitterMs != null) "${speedState.jitterMs} ms" else "--",
                icon = Icons.Default.Bolt,
                isDark = isDark,
                valueColor = skyCyan
            )

            // Connection Status
            MetricCard(
                modifier = Modifier.weight(1f),
                title = strings.speedTestTunnelStatus,
                value = if (status == VpnStatus.CONNECTED) strings.statusConnected else strings.statusDisconnected,
                icon = Icons.Default.Security,
                isDark = isDark,
                valueColor = if (status == VpnStatus.CONNECTED) neonGreen else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
            )
        }

        // Quality Rating Result Box
        if (speedState.qualityGrade != QualityGrade.UNKNOWN) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = when (speedState.qualityGrade) {
                    QualityGrade.EXCELLENT -> neonGreen.copy(alpha = 0.12f)
                    QualityGrade.GOOD -> skyCyan.copy(alpha = 0.12f)
                    QualityGrade.MODERATE -> amberWarning.copy(alpha = 0.12f)
                    else -> neonRed.copy(alpha = 0.12f)
                },
                border = BorderStroke(
                    1.dp,
                    when (speedState.qualityGrade) {
                        QualityGrade.EXCELLENT -> neonGreen.copy(alpha = 0.4f)
                        QualityGrade.GOOD -> skyCyan.copy(alpha = 0.4f)
                        QualityGrade.MODERATE -> amberWarning.copy(alpha = 0.4f)
                        else -> neonRed.copy(alpha = 0.4f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = when (speedState.qualityGrade) {
                            QualityGrade.EXCELLENT -> Icons.Default.CheckCircle
                            QualityGrade.GOOD -> Icons.Default.CheckCircle
                            QualityGrade.MODERATE -> Icons.Default.Bolt
                            else -> Icons.Default.ErrorOutline
                        },
                        contentDescription = null,
                        tint = when (speedState.qualityGrade) {
                            QualityGrade.EXCELLENT -> neonGreen
                            QualityGrade.GOOD -> skyCyan
                            QualityGrade.MODERATE -> amberWarning
                            else -> neonRed
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = speedState.qualityRating,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Action Button: Start / Stop Test
        Button(
            onClick = {
                if (speedState.isRunning) {
                    viewModel.cancelSpeedTest()
                } else {
                    viewModel.startSpeedTest()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("speed_test_action_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (speedState.isRunning) neonRed else Color(0xFF0284C7)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (speedState.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.speedTestStop,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (speedState.stage == SpeedTestStage.FINISHED) strings.speedTestRetest else strings.speedTestStart,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    isDark: Boolean = true,
    valueColor: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color(0xFF0A1324) else Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, if (isDark) Color(0x2638BDF8) else Color(0xFFCBD5E1)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = skyCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = title,
                color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B),
                fontSize = 10.sp
            )
        }
    }
}

// -------------------------------------------------------------
// 3. LOGS SECTION (STRICT NO-SERVER-ADDRESS RULE)
// -------------------------------------------------------------
@Composable
private fun LogsSection(viewModel: VpnViewModel) {
    val isDark = com.example.ui.theme.AppTheme.colors.isDark
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val rawLogs by viewModel.logsList.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedLevelFilter by remember { mutableStateOf("ALL") }
    var showClearConfirm by remember { mutableStateOf(false) }

    // STRICT SANITIZATION: Sanitize all log messages so no server IP or host is revealed!
    val sanitizedLogs = remember(rawLogs) {
        rawLogs.map { log ->
            log.copy(message = LogSanitizer.sanitize(log.message))
        }
    }

    val filteredLogs = remember(sanitizedLogs, searchQuery, selectedLevelFilter) {
        sanitizedLogs.filter { log ->
            val matchesSearch = searchQuery.isBlank() || log.message.contains(searchQuery, ignoreCase = true)
            val matchesLevel = when (selectedLevelFilter) {
                "ALL" -> true
                "ERROR" -> log.level == "ERROR"
                "SUCCESS" -> log.level == "SUCCESS"
                "INFO" -> log.level == "INFO"
                else -> true
            }
            matchesSearch && matchesLevel
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Controls Row: Search & Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clear logs button
            IconButton(
                onClick = { showClearConfirm = true },
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                    .testTag("clear_logs_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = strings.clearLogsTitle,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Copy logs button
            IconButton(
                onClick = {
                    if (sanitizedLogs.isEmpty()) {
                        Toast.makeText(context, strings.noLogsFound, Toast.LENGTH_SHORT).show()
                    } else {
                        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
                        val textToCopy = sanitizedLogs.joinToString("\n") { log ->
                            "[${timeFmt.format(Date(log.timestamp))}] [${log.level}] ${log.message}"
                        }
                        clipboard.setText(AnnotatedString(textToCopy))
                        Toast.makeText(context, strings.logsCopiedToast, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                    .testTag("copy_logs_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = strings.copyLabel,
                    tint = skyCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Search TextField
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                placeholder = {
                    Text(
                        text = strings.searchLogsPlaceholder,
                        color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = if (isDark) Color(0xFF0B1424) else Color(0xFFF1F5F9),
                    unfocusedContainerColor = if (isDark) Color(0xFF0B1424) else Color(0xFFF1F5F9),
                    focusedBorderColor = skyCyan,
                    unfocusedBorderColor = if (isDark) Color(0x2638BDF8) else Color(0xFFCBD5E1),
                    focusedTextColor = if (isDark) Color.White else Color(0xFF0F172A),
                    unfocusedTextColor = if (isDark) Color.White else Color(0xFF0F172A)
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        // Level Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf<Pair<String, String>>(
                "ALL" to strings.filterAll,
                "ERROR" to strings.filterErrors,
                "SUCCESS" to strings.filterSuccess,
                "INFO" to strings.filterInfo
            ).forEach { (code, label) ->
                val isSelected = selectedLevelFilter == code
                Surface(
                    onClick = { selectedLevelFilter = code },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) skyCyan.copy(alpha = 0.2f) else (if (isDark) Color(0xFF0D182E) else Color(0xFFF1F5F9)),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) skyCyan else (if (isDark) Color(0x1F38BDF8) else Color(0xFFCBD5E1))
                    )
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) brandCyan else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Console Container
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDark) Color(0xFF040812) else Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, if (isDark) Color(0x2638BDF8) else Color(0xFFCBD5E1)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF334155) else Color(0xFF94A3B8),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.noLogsFound,
                            color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { log ->
                            LogItemRow(log = log, timeFormat = timeFormat, isDark = isDark)
                        }
                    }
                }
            }
        }

        // Privacy indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Color(0xFF059669),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = strings.privacyNotice,
                fontSize = 10.sp,
                color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B)
            )
        }
    }

    // Confirm Clear Dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = if (isDark) Color(0xFF0D1527) else Color.White,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    text = strings.clearLogsTitle,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = strings.clearLogsConfirm,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearLogs()
                        showClearConfirm = false
                        Toast.makeText(context, strings.logsClearedToast, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(strings.yesDelete, color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(strings.cancel, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                }
            }
        )
    }
}

@Composable
private fun LogItemRow(log: VpnLog, timeFormat: SimpleDateFormat, isDark: Boolean = true) {
    val levelColor = when (log.level) {
        "ERROR" -> Color(0xFFEF4444)
        "SUCCESS" -> Color(0xFF10B981)
        "WARN" -> Color(0xFFF59E0B)
        else -> Color(0xFF38BDF8)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isDark) Color(0xFF091120) else Color(0xFFF1F5F9))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Timestamp
        Text(
            text = timeFormat.format(Date(log.timestamp)),
            fontSize = 10.sp,
            color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B),
            fontFamily = FontFamily.Monospace
        )

        // Level Tag
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = levelColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = log.level,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = levelColor,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        // Message
        Text(
            text = log.message,
            fontSize = 11.sp,
            color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Right
        )
    }
}

private fun openUrl(context: Context, url: String, strings: AppStrings? = null) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        val msg = strings?.openUrlErrorToast?.invoke(url) ?: "Error opening link: $url"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
