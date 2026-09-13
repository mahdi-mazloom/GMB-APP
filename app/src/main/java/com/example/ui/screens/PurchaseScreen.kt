package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.LocalAppStrings
import com.example.util.PersianDateHelper
import com.example.viewmodel.RedeemLicenseUiState
import com.example.viewmodel.VpnViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(
    viewModel: VpnViewModel,
    onNavigateBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val isDark = com.example.ui.theme.AppTheme.colors.isDark
    val appColors = com.example.ui.theme.AppTheme.colors
    val deepDarkBg = appColors.background
    val cardBg = appColors.surfaceCard
    val borderSubtle = appColors.cardBorder
    val brandCyan = appColors.brandCyan
    val neonGreen = appColors.neonGreen
    val neonRed = appColors.neonRed
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary
    val textMuted = appColors.textMuted

    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val isExpired = activeSession == null || (activeSession?.remainingDays ?: 0) <= 0
    val redeemLicenseState by viewModel.redeemLicenseState.collectAsStateWithLifecycle()

    var licenseCodeInput by remember { mutableStateOf("") }
    val isLoading = redeemLicenseState is RedeemLicenseUiState.Loading

    Scaffold(
        containerColor = deepDarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = strings.purchaseTitle,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = strings.purchaseSubtitle,
                            fontSize = 11.sp,
                            color = brandCyan
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF162032) else Color(0xFFE2E8F0))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = strings.back,
                            tint = textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = deepDarkBg
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. Current Account Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                if (isExpired) neonRed.copy(alpha = 0.5f) else brandCyan.copy(alpha = 0.4f),
                                Color(0xFF1E293B)
                            )
                        ),
                        RoundedCornerShape(20.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (isExpired) neonRed.copy(alpha = 0.2f) else neonGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (isExpired) strings.expired else strings.activeSubscription,
                                color = if (isExpired) neonRed else neonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = activeSession?.username ?: strings.guestUser,
                                color = textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = brandCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = borderSubtle, thickness = 0.8.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(strings.remainingCreditLabel, color = textMuted, fontSize = 11.sp)
                            Text(
                                text = if (isExpired) strings.zeroDays else strings.daysUnit(activeSession?.remainingDays ?: 0),
                                color = if (isExpired) neonRed else textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(strings.remainingTrafficLabel, color = textMuted, fontSize = 11.sp)
                            val totalMb = activeSession?.totalTrafficMb ?: 0L
                            val consumedMb = activeSession?.consumedTrafficMb ?: 0L
                            val remainingMb = maxOf(0L, totalMb - consumedMb)
                            val remainingGB = if (totalMb <= 0L) strings.unlimitedLabel else "${remainingMb / 1024L} GB"
                            Text(
                                text = remainingGB,
                                color = brandCyan,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(strings.expiryDateLabel, color = textMuted, fontSize = 11.sp)
                            val rawDate = activeSession?.finishDate?.takeIf { it.isNotBlank() } ?: activeSession?.shamsiFinishDate
                            val expDate = PersianDateHelper.formatToShamsiDate(rawDate, activeSession?.remainingDays)
                            Text(
                                text = expDate,
                                color = textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 2. Buy Voucher Code Card (gmb-net.ir)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.5.dp,
                        Brush.horizontalGradient(
                            listOf(Color(0xFF3B82F6), Color(0xFF06B6D4))
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .testTag("buy_voucher_website_card"),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF0B192E) else Color(0xFFF0F9FF)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (isDark) Color(0xFF3B82F6).copy(alpha = 0.2f) else Color(0xFFDBEAFE),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = strings.officialWebsiteCard,
                                color = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = strings.buyVoucherTitle,
                                color = textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Text(
                        text = strings.buyVoucherDesc,
                        color = textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Button to Open Website
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gmb-net.ir"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, strings.browserErrorWithUrl("https://gmb-net.ir"), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_website_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.goToSiteAndBuy,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Copy website URL row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0xFF081220) else Color(0xFFE0F2FE))
                            .clickable {
                                clipboardManager.setText(AnnotatedString("https://gmb-net.ir"))
                                Toast.makeText(context, strings.websiteCopiedToast, Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = strings.copyWebsiteUrl,
                                color = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = "gmb-net.ir",
                            color = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 3. Redeem License / Voucher Code Form
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(neonGreen.copy(alpha = 0.5f), Color(0xFF06B6D4).copy(alpha = 0.3f))
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .testTag("redeem_license_form_card"),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = neonGreen.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = strings.instantRechargeCard,
                                color = neonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = strings.submitLicenseCard,
                                color = textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.ConfirmationNumber,
                                contentDescription = null,
                                tint = neonGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Text(
                        text = strings.enterVoucherInBox,
                        color = textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Text Field for License Code
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
                            .testTag("screen_license_code_input"),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            textAlign = TextAlign.Center,
                            letterSpacing = 2.sp
                        ),
                        placeholder = {
                            Text(
                                text = strings.licenseInputPlaceholder,
                                color = textMuted,
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
                            focusedBorderColor = neonGreen,
                            unfocusedBorderColor = borderSubtle,
                            focusedContainerColor = if (isDark) Color(0xFF070C16) else Color(0xFFF8FAFC),
                            unfocusedContainerColor = if (isDark) Color(0xFF070C16) else Color(0xFFF8FAFC)
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = neonGreen,
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
                                            tint = textMuted,
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
                                        .testTag("screen_paste_license_button")
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

                    // State Display: Loading, Success, Error
                    AnimatedVisibility(
                        visible = redeemLicenseState !is RedeemLicenseUiState.Idle,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
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
                                        color = neonGreen,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = strings.communicatingWithServer,
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            is RedeemLicenseUiState.Success -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isDark) Color(0xFF064E3B).copy(alpha = 0.5f) else Color(0xFFECFDF5), RoundedCornerShape(12.dp))
                                        .border(1.dp, neonGreen, RoundedCornerShape(12.dp))
                                        .padding(14.dp),
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
                                            tint = neonGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    if (state.daysAdded != null && state.daysAdded > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = strings.addedDaysToSubscription(state.daysAdded),
                                            color = if (isDark) Color.White else Color(0xFF065F46),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (state.volumeGBAdded != null && state.volumeGBAdded > 0) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = strings.addedVolumeToAccount(state.volumeGBAdded),
                                            color = if (isDark) Color.White else Color(0xFF065F46),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
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
                            }
                            else -> {}
                        }
                    }

                    // Confirm and Redeem Button
                    Button(
                        onClick = {
                            viewModel.redeemLicense(licenseCodeInput)
                        },
                        enabled = !isLoading && licenseCodeInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("screen_confirm_redeem_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = neonGreen,
                            disabledContainerColor = neonGreen.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.checkingAndApplying,
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
                                text = strings.confirmAndRenewSubscription,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 4. Instructions Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderSubtle, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF090E1A) else Color(0xFFF8FAFC)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.purchaseStepsTitle,
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    InstructionStepItem(
                        step = strings.step1Number,
                        title = strings.step1Title,
                        desc = strings.step1Desc,
                        textPrimary = textPrimary,
                        textMuted = textMuted,
                        isDark = isDark,
                        brandCyan = brandCyan
                    )
                    InstructionStepItem(
                        step = strings.step2Number,
                        title = strings.step2Title,
                        desc = strings.step2Desc,
                        textPrimary = textPrimary,
                        textMuted = textMuted,
                        isDark = isDark,
                        brandCyan = brandCyan
                    )
                    InstructionStepItem(
                        step = strings.step3Number,
                        title = strings.step3Title,
                        desc = strings.step3Desc,
                        textPrimary = textPrimary,
                        textMuted = textMuted,
                        isDark = isDark,
                        brandCyan = brandCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InstructionStepItem(
    step: String,
    title: String,
    desc: String,
    textPrimary: Color,
    textMuted: Color,
    isDark: Boolean,
    brandCyan: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right
            )
            Text(
                text = desc,
                color = textMuted,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Right
            )
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                color = brandCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
