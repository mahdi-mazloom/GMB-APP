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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.AppDatabase
import com.example.util.PersianDateHelper
import com.example.viewmodel.VpnViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

data class Plan(
    val id: String,
    val title: String,
    val price: String,
    val rawPriceToman: Long,
    val duration: String,
    val days: Int,
    val monthlyEquivalent: String,
    val discountTag: String? = null,
    val isPopular: Boolean = false,
    val isBestValue: Boolean = false,
    val features: List<String> = listOf(
        "ترافیک ۱۰۰٪ نامحدود و بدون سقف مصرف",
        "آی‌پی ثابت و ضد فیلتر بدون قطعی",
        "پینگ پایین و پایدار مخصوص گیمینگ و تماس",
        "امکان اتصال همزمان ۲ کاربر"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(
    viewModel: VpnViewModel,
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val isExpired = activeSession == null || activeSession!!.remainingDays <= 0

    var selectedPlan by remember { mutableStateOf<Plan?>(null) }
    var isPurchasing by remember { mutableStateOf(false) }

    // Discount coupon state
    var couponCode by remember { mutableStateOf("") }
    var appliedDiscountPercent by remember { mutableStateOf(0) }
    var couponMessage by remember { mutableStateOf<String?>(null) }
    var isCouponSuccess by remember { mutableStateOf(false) }

    // Payment tab state: 0 = Telegram / Card to Card, 1 = USDT Crypto
    var paymentTab by remember { mutableIntStateOf(0) }

    // Modern cyber palette matching GMB NET design
    val deepNavyBg = Color(0xFF070B14)
    val surfaceDark = Color(0xFF0C1322)
    val cardBg = Color(0xFF0F192D)
    val borderStroke = Color(0xFF1E2E4A)
    val brandCyan = Color(0xFF38BDF8)
    val brandPurple = Color(0xFFA855F7)
    val brandGold = Color(0xFFF59E0B)
    val neonGreen = Color(0xFF10B981)
    val neonRed = Color(0xFFEF4444)

    val planList = remember {
        listOf(
            Plan(
                id = "1m",
                title = "اشتراک ۱ ماهه استاندارد",
                price = "۵۰۰,۰۰۰ تومان",
                rawPriceToman = 500_000L,
                duration = "۳۰ روز اعتبار کامل",
                days = 30,
                monthlyEquivalent = "معادل ۵۰۰,۰۰۰ تومان / ماه"
            ),
            Plan(
                id = "2m",
                title = "اشتراک ۲ ماهه اقتصادی",
                price = "۹۵۰,۰۰۰ تومان",
                rawPriceToman = 950_000L,
                duration = "۶۰ روز اعتبار کامل",
                days = 60,
                monthlyEquivalent = "معادل ۴۷۵,۰۰۰ تومان / ماه",
                discountTag = "۵۰ هزار تومان تخفیف"
            ),
            Plan(
                id = "3m",
                title = "اشتراک ۳ ماهه پرطرفدار",
                price = "۱,۳۰۰,۰۰۰ تومان",
                rawPriceToman = 1_300_000L,
                duration = "۹۰ روز اعتبار کامل",
                days = 90,
                monthlyEquivalent = "معادل ۴۳۳,۰۰۰ تومان / ماه",
                isPopular = true,
                discountTag = "⭐ محبوب‌ترین انتخاب"
            ),
            Plan(
                id = "6m",
                title = "اشتراک ۶ ماهه حرفه‌ای",
                price = "۲,۴۰۰,۰۰۰ تومان",
                rawPriceToman = 2_400_000L,
                duration = "۱۸۰ روز اعتبار کامل",
                days = 180,
                monthlyEquivalent = "معادل ۴۰۰,۰۰۰ تومان / ماه",
                discountTag = "⚡ ۶۰۰ هزار تومان سود خرید"
            ),
            Plan(
                id = "1y",
                title = "اشتراک ۱ ساله ویژه VIP",
                price = "۴,۲۰۰,۰۰۰ تومان",
                rawPriceToman = 4_200_000L,
                duration = "۳۶۵ روز اعتبار کامل",
                days = 365,
                monthlyEquivalent = "معادل ۳۵۰,۰۰۰ تومان / ماه",
                isBestValue = true,
                discountTag = "👑 بیشترین صرفه‌جویی (۳۵٪ تخفیف)"
            )
        )
    }

    Scaffold(
        containerColor = deepNavyBg,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(deepNavyBg)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                            tint = Color(0xFF94A3B8)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "خرید و تمدید اشتراک",
                            fontSize = 18.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "- GMB NET VIP -",
                            fontSize = 11.sp,
                            color = brandCyan,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                    }

                    // Balance placeholder for visual symmetry
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Secure",
                            tint = brandCyan.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
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
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF0F1A33), deepNavyBg),
                        radius = 1300f
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ==========================================
                // 1. SMART USER ACCOUNT STATUS HEADER
                // ==========================================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isExpired) neonRed.copy(alpha = 0.6f) else borderStroke,
                            RoundedCornerShape(20.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = surfaceDark),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    if (isExpired) {
                                        listOf(Color(0xFF280B12), Color(0xFF130A14))
                                    } else {
                                        listOf(Color(0xFF0A1E33), Color(0xFF0D172A))
                                    }
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Status Badge & Action
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isExpired) neonRed.copy(alpha = 0.2f) else neonGreen.copy(alpha = 0.15f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isExpired) neonRed.copy(alpha = 0.6f) else neonGreen.copy(alpha = 0.5f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isExpired) "اشتراک منقضی شده" else "${activeSession?.remainingDays ?: 0} روز اعتبار",
                                    color = if (isExpired) neonRed else neonGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // User Info and Expiry
                            Column(horizontalAlignment = Alignment.End) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = activeSession?.username ?: "کاربر گرامی",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = brandCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                if (isExpired) {
                                    Text(
                                        text = "اتصال مسدود است • تمدید فوری حساب",
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Right
                                    )
                                } else {
                                    Text(
                                        text = "تاریخ پایان: ${activeSession?.shamsiFinishDate ?: "۱۴۰۵/۰۶/۲۰"}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Right
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 2. TRUST & SPEED HIGHLIGHT BADGES
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeatureHighlightBadge(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AllInclusive,
                        title = "ترافیک نامحدود",
                        color = brandCyan
                    )
                    FeatureHighlightBadge(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Speed,
                        title = "سرعت گیگابیتی",
                        color = neonGreen
                    )
                    FeatureHighlightBadge(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Security,
                        title = "آی‌پی ضد فیلتر",
                        color = brandPurple
                    )
                }

                // Section title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تمدید فوری با شارژ آنی",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "پلن‌های اشتراک VPN",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // ==========================================
                // 3. PLAN CARDS LIST
                // ==========================================
                planList.forEach { plan ->
                    EnhancedPlanCard(
                        plan = plan,
                        borderStroke = borderStroke,
                        cardBg = cardBg,
                        brandCyan = brandCyan,
                        brandPurple = brandPurple,
                        brandGold = brandGold,
                        neonGreen = neonGreen,
                        onSelect = {
                            selectedPlan = plan
                            // Reset coupon when choosing a new plan
                            couponCode = ""
                            appliedDiscountPercent = 0
                            couponMessage = null
                        }
                    )
                }

                // ==========================================
                // 4. GUARANTEE & TELEGRAM SUPPORT BANNER
                // ==========================================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A101D)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "تضمین ۱۰۰٪ کیفیت و پایداری شبکه",
                                color = Color(0xFFE2E8F0),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = brandGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "سرورهای GMB NET مجهز به بهینه‌ساز مسیر تونل و پروتکل‌های رمزنگاری اختصاصی SSH/TLS با پینگ فوق‌العاده پایین می‌باشند.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://t.me/GMB_NET_Support")
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    clipboardManager.setText(AnnotatedString("@GMB_NET_Support"))
                                    Toast.makeText(context, "آیدی پشتیبانی تلگرام کپی شد: @GMB_NET_Support", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2E4A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = null,
                                tint = brandCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ارتباط با پشتیبانی تلگرام: @GMB_NET_Support",
                                color = brandCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ==========================================
            // 5. MODERN CHECKOUT BOTTOM SHEET
            // ==========================================
            if (selectedPlan != null) {
                val plan = selectedPlan!!

                // Calculate discounted price
                val discountAmount = (plan.rawPriceToman * appliedDiscountPercent) / 100
                val finalPayableToman = plan.rawPriceToman - discountAmount
                val finalPriceFormatted = NumberFormat.getNumberInstance(Locale.US).format(finalPayableToman) + " تومان"

                // Calculate new expiry date based on current session
                val currentRemainingDays = activeSession?.remainingDays?.coerceAtLeast(0) ?: 0
                val newTotalDays = currentRemainingDays + plan.days
                val newShamsiExpiryDate = PersianDateHelper.getExpiryDateShamsiFormatted(newTotalDays)

                ModalBottomSheet(
                    onDismissRequest = { selectedPlan = null },
                    containerColor = Color(0xFF0C1424),
                    scrimColor = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    dragHandle = {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .width(42.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155))
                        )
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header Title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { selectedPlan = null }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "بستن",
                                    tint = Color(0xFF94A3B8)
                                )
                            }

                            Text(
                                text = "پیش‌فاکتور و تمدید اشتراک",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = borderStroke
                        )

                        // Invoice Summary Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF1E2E4A), RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111C33)),
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
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = plan.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "پلن انتخابی:",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = activeSession?.username ?: "حساب کاربری فعلی",
                                        color = brandCyan,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "تمدید برای حساب:",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$newShamsiExpiryDate ($newTotalDays روز)",
                                        color = neonGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "تاریخ پایان جدید:",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }

                                HorizontalDivider(color = Color(0xFF1E2E4A).copy(alpha = 0.6f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        if (appliedDiscountPercent > 0) {
                                            Text(
                                                text = plan.price,
                                                color = Color(0xFF64748B),
                                                fontSize = 12.sp,
                                                textDecoration = TextDecoration.LineThrough
                                            )
                                        }
                                        Text(
                                            text = finalPriceFormatted,
                                            color = brandGold,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp
                                        )
                                    }

                                    Text(
                                        text = "مبلغ نهایی قابل پرداخت:",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ==========================================
                        // COUPON CODE BOX
                        // ==========================================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    val code = couponCode.trim().uppercase()
                                    when (code) {
                                        "GMB20" -> {
                                            appliedDiscountPercent = 20
                                            isCouponSuccess = true
                                            couponMessage = "کد تخفیف ۲۰٪ اعمال شد!"
                                        }
                                        "OFF10" -> {
                                            appliedDiscountPercent = 10
                                            isCouponSuccess = true
                                            couponMessage = "کد تخفیف ۱۰٪ اعمال شد!"
                                        }
                                        "VIP" -> {
                                            appliedDiscountPercent = 30
                                            isCouponSuccess = true
                                            couponMessage = "کد تخفیف VIP ۳۰٪ اعمال شد!"
                                        }
                                        "" -> {
                                            appliedDiscountPercent = 0
                                            isCouponSuccess = false
                                            couponMessage = "لطفاً کد تخفیف را وارد کنید."
                                        }
                                        else -> {
                                            appliedDiscountPercent = 0
                                            isCouponSuccess = false
                                            couponMessage = "کد تخفیف وارد شده معتبر نیست."
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2E4A)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("اعمال کد", color = brandCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedTextField(
                                value = couponCode,
                                onValueChange = { couponCode = it },
                                placeholder = {
                                    Text("کد تخفیف (مثال: GMB20)", color = Color(0xFF475569), fontSize = 12.sp)
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Right
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = brandCyan,
                                    unfocusedBorderColor = Color(0xFF1E2E4A),
                                    focusedContainerColor = Color(0xFF0F1829),
                                    unfocusedContainerColor = Color(0xFF0F1829)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (couponMessage != null) {
                            Text(
                                text = couponMessage ?: "",
                                color = if (isCouponSuccess) neonGreen else neonRed,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                textAlign = TextAlign.Right
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ==========================================
                        // PAYMENT METHOD TABS
                        // ==========================================
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF080D18))
                                .padding(4.dp)
                        ) {
                            PaymentTabButton(
                                title = "رمزارز تتر (USDT)",
                                isSelected = paymentTab == 1,
                                modifier = Modifier.weight(1f),
                                onClick = { paymentTab = 1 }
                            )
                            PaymentTabButton(
                                title = "کارت به کارت و تلگرام",
                                isSelected = paymentTab == 0,
                                modifier = Modifier.weight(1f),
                                onClick = { paymentTab = 0 }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (paymentTab == 0) {
                            // Method 1: Telegram & Card
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF10192C))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "شماره کارت بانکی (جهت واریز شتابی):",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0A0F1D))
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString("6037997514238890"))
                                            Toast.makeText(context, "شماره کارت کپی شد!", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "کپی",
                                        tint = brandCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "۶۰۳۷-۹۹۷۵-۱۴۲۳-۸۸۹۰",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }

                                Text(
                                    text = "بانک ملی • به نام پشتیبانی فنی گمبرون نت",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = {
                                        try {
                                            val invoiceMsg = "سلام، قصد تمدید اشتراک دارم.\nنام کاربری: ${activeSession?.username ?: "-"}\nپلن انتخابی: ${plan.title}\nمبلغ: $finalPriceFormatted"
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://t.me/GMB_NET_Support?text=" + Uri.encode(invoiceMsg))
                                            )
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            clipboardManager.setText(AnnotatedString("@GMB_NET_Support"))
                                            Toast.makeText(context, "آیدی تلگرام کپی شد!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ارسال فیش و تایید در تلگرام", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // Method 2: USDT TRC-20
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF10192C))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(neonGreen.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("شبکه TRC-20", color = neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "آدرس کیف پول تتر (USDT):",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }

                                val usdtAddress = "TY2fCgY77vT1S8TqpM9Vp7fJ3TfP73hG3s"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0A0F1D))
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(usdtAddress))
                                            Toast.makeText(context, "آدرس کیف پول تتر کپی شد!", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "کپی",
                                        tint = brandCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "TY2fCgY77vT1...73hG3s",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "لطفاً توجه فرمایید انتقال فقط از طریق شبکه Tron (TRC20) انجام گیرد.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Instant Local Activation Simulator Button
                        Button(
                            onClick = {
                                isPurchasing = true
                                scope.launch {
                                    val db = AppDatabase.getDatabase(context)
                                    val session = db.userSessionDao().getActiveSessionOnce()
                                    if (session != null) {
                                        val updatedDays = (session.remainingDays.coerceAtLeast(0)) + plan.days
                                        val updatedShamsiDate = PersianDateHelper.getExpiryDateShamsiFormatted(updatedDays)
                                        db.userSessionDao().saveSession(
                                            session.copy(
                                                remainingDays = updatedDays,
                                                shamsiFinishDate = updatedShamsiDate,
                                                status = "active"
                                            )
                                        )
                                        Toast.makeText(
                                            context,
                                            "اشتراک با موفقیت تمدید شد! ${plan.days} روز اضافه گردید.\nتاریخ جدید: $updatedShamsiDate",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(context, "حساب کاربری یافت نشد. لطفا ابتدا وارد شوید.", Toast.LENGTH_SHORT).show()
                                    }
                                    isPurchasing = false
                                    selectedPlan = null
                                }
                            },
                            enabled = !isPurchasing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = brandPurple
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("buy_simulated_button")
                        ) {
                            if (isPurchasing) {
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
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = brandGold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "تمدید و فعال‌سازی آنلاین (تست شبیه‌ساز)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(onClick = { selectedPlan = null }) {
                            Text("انصراف و بستن", color = Color(0xFF64748B), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentTabButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFF1E2E4A) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun FeatureHighlightBadge(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B1424))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = Color(0xFFCBD5E1),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EnhancedPlanCard(
    plan: Plan,
    borderStroke: Color,
    cardBg: Color,
    brandCyan: Color,
    brandPurple: Color,
    brandGold: Color,
    neonGreen: Color,
    onSelect: () -> Unit
) {
    val borderColor = when {
        plan.isBestValue -> brandGold
        plan.isPopular -> brandPurple
        else -> borderStroke
    }

    val glowBrush = when {
        plan.isBestValue -> Brush.verticalGradient(listOf(Color(0xFF261D09), Color(0xFF0F1523)))
        plan.isPopular -> Brush.verticalGradient(listOf(Color(0xFF221133), Color(0xFF0F1523)))
        else -> Brush.verticalGradient(listOf(cardBg, Color(0xFF090E1A)))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (plan.isPopular || plan.isBestValue) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(22.dp)
            )
            .shadow(
                elevation = if (plan.isBestValue || plan.isPopular) 12.dp else 4.dp,
                spotColor = if (plan.isBestValue) brandGold.copy(alpha = 0.3f) else if (plan.isPopular) brandPurple.copy(alpha = 0.3f) else Color.Transparent,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(22.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(glowBrush)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Row: Discount Tag & Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (plan.discountTag != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (plan.isBestValue) brandGold.copy(alpha = 0.2f) else brandPurple.copy(alpha = 0.2f)
                                )
                                .border(
                                    1.dp,
                                    if (plan.isBestValue) brandGold.copy(alpha = 0.7f) else brandPurple.copy(alpha = 0.7f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = plan.discountTag,
                                color = if (plan.isBestValue) brandGold else brandPurple,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = plan.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Price Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = plan.price,
                            color = if (plan.isBestValue) brandGold else if (plan.isPopular) brandPurple else brandCyan,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = plan.monthlyEquivalent,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = plan.duration,
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "ترافیک نامحدود",
                                color = neonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(neonGreen)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color(0xFF1E2E4A).copy(alpha = 0.7f)
                )

                // Features checklist
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    plan.features.forEach { feature ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = feature,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Right
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = neonGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Select and Renew Action Button
                Button(
                    onClick = onSelect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (plan.isBestValue) Color(0xFFB45309) else if (plan.isPopular) Color(0xFF7C3AED) else Color(0xFF0369A1)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "انتخاب و تمدید آنلاین",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ShoppingCartCheckout,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
