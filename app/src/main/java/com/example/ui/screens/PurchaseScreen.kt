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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.AppDatabase
import com.example.data.database.UserSession
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

    // Modal bottom sheet state - ALWAYS skip partially expanded so it opens FULLY and never halfway!
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Receipt & tracking state
    var trackingCode by remember { mutableStateOf("") }
    var senderCardLast4 by remember { mutableStateOf("") }
    var cardCopied by remember { mutableStateOf(false) }
    var shabaCopied by remember { mutableStateOf(false) }
    var amountTomanCopied by remember { mutableStateOf(false) }
    var amountRialCopied by remember { mutableStateOf(false) }

    // Direct checkout user info: English username and phone number
    var checkoutUsername by remember(activeSession) { mutableStateOf(activeSession?.username ?: "") }
    var checkoutPhone by remember { mutableStateOf("") }

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
                            // Reset coupon & payment states when choosing a new plan
                            couponCode = ""
                            appliedDiscountPercent = 0
                            couponMessage = null
                            trackingCode = ""
                            senderCardLast4 = ""
                            cardCopied = false
                            shabaCopied = false
                            amountTomanCopied = false
                            amountRialCopied = false
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
            // 5. FULL-HEIGHT MODERN CHECKOUT BOTTOM SHEET
            // ==========================================
            if (selectedPlan != null) {
                val plan = selectedPlan!!

                // Calculate discounted price
                val discountAmount = (plan.rawPriceToman * appliedDiscountPercent) / 100
                val finalPayableToman = plan.rawPriceToman - discountAmount
                val finalPriceFormatted = NumberFormat.getNumberInstance(Locale.US).format(finalPayableToman) + " تومان"
                val finalPriceRialFormatted = NumberFormat.getNumberInstance(Locale.US).format(finalPayableToman * 10) + " ریال"

                // Calculate new expiry date based on current session
                val currentRemainingDays = activeSession?.remainingDays?.coerceAtLeast(0) ?: 0
                val newTotalDays = currentRemainingDays + plan.days
                val newShamsiExpiryDate = PersianDateHelper.getExpiryDateShamsiFormatted(newTotalDays)

                ModalBottomSheet(
                    onDismissRequest = { selectedPlan = null },
                    sheetState = sheetState,
                    containerColor = Color(0xFF0A0F1D),
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
                    modifier = Modifier.fillMaxHeight(0.96f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header Title & Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { selectedPlan = null },
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
                                    text = "پیش‌فاکتور و تمدید اشتراک",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "پرداخت شتابی امن و شارژ آنی حساب",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(brandCyan.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = plan.duration.substringBefore(" "),
                                    color = brandCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // ==========================================
                        // INVOICE SUMMARY CARD
                        // ==========================================
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFF1E2E4A),
                                            brandCyan.copy(alpha = 0.4f),
                                            Color(0xFF1E2E4A)
                                        )
                                    ),
                                    RoundedCornerShape(18.dp)
                                ),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F182B)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Plan Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(brandPurple.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${plan.days} روزه",
                                                color = brandPurple,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = plan.title,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Text(
                                        text = "پلن انتخابی:",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }

                                // User Account & Delivery Information
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFF1E2E4A), RoundedCornerShape(14.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1424)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = neonGreen.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "تحویل آنی ۲۴ ساعته",
                                                    color = neonGreen,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = "مشخصات تحویل اشتراک:",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Username (English only)
                                        OutlinedTextField(
                                            value = checkoutUsername,
                                            onValueChange = { input ->
                                                checkoutUsername = input.filter { ch -> 
                                                    (ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9') || ch == '_' 
                                                }
                                            },
                                            label = { Text("نام کاربری به انگلیسی", fontSize = 11.sp) },
                                            placeholder = { Text("مثال: user123", color = Color(0xFF475569), fontSize = 11.sp) },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Left
                                            ),
                                            supportingText = {
                                                Text(
                                                    text = if (checkoutUsername.length < 3) "حداقل ۳ حرف و فقط حروف انگلیسی و اعداد" else "✓ نام کاربری به انگلیسی وارد شد",
                                                    color = if (checkoutUsername.length < 3) Color(0xFFF59E0B) else neonGreen,
                                                    fontSize = 10.sp
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = brandCyan,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = brandCyan,
                                                unfocusedBorderColor = Color(0xFF1E2E4A),
                                                focusedContainerColor = Color(0xFF080D1A),
                                                unfocusedContainerColor = Color(0xFF080D1A)
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Phone Number (Iranian mobile)
                                        OutlinedTextField(
                                            value = checkoutPhone,
                                            onValueChange = { input ->
                                                checkoutPhone = input.filter { it.isDigit() }.take(11)
                                            },
                                            label = { Text("شماره تلفن همراه (جهت ارسال پیامک و تحویل)", fontSize = 11.sp) },
                                            placeholder = { Text("مثال: ۰۹۱۲۳۴۵۶۷۸۹", color = Color(0xFF475569), fontSize = 11.sp) },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Left
                                            ),
                                            supportingText = {
                                                val isPhoneValid = checkoutPhone.startsWith("09") && checkoutPhone.length == 11
                                                Text(
                                                    text = if (isPhoneValid) "✓ شماره همراه تایید شد" else "شماره ۱۱ رقمی شروع با ۰۹ جهت دریافت پیامک مشخصات",
                                                    color = if (isPhoneValid) neonGreen else Color(0xFF94A3B8),
                                                    fontSize = 10.sp
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.PhoneAndroid,
                                                    contentDescription = null,
                                                    tint = brandCyan,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = brandCyan,
                                                unfocusedBorderColor = Color(0xFF1E2E4A),
                                                focusedContainerColor = Color(0xFF080D1A),
                                                unfocusedContainerColor = Color(0xFF080D1A)
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                // Visual Expiry Transition Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF090E1A))
                                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // New expiry
                                        Column(horizontalAlignment = Alignment.Start) {
                                            Text(
                                                text = "تاریخ پایان جدید:",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                text = "$newShamsiExpiryDate ($newTotalDays روز)",
                                                color = neonGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(16.dp)
                                        )

                                        // Current expiry
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "اعتبار فعلی:",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                text = if (currentRemainingDays <= 0) "منقضی شده" else "$currentRemainingDays روز",
                                                color = if (currentRemainingDays <= 0) neonRed else Color(0xFFCBD5E1),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF1E2E4A).copy(alpha = 0.8f))

                                // Pricing Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.Start) {
                                        if (appliedDiscountPercent > 0) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(neonGreen.copy(alpha = 0.2f))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "$appliedDiscountPercent٪ تخفیف",
                                                        color = neonGreen,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Text(
                                                    text = plan.price,
                                                    color = Color(0xFF64748B),
                                                    fontSize = 12.sp,
                                                    textDecoration = TextDecoration.LineThrough
                                                )
                                            }
                                        }
                                        Text(
                                            text = finalPriceFormatted,
                                            color = brandGold,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 18.sp
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

                        // ==========================================
                        // COUPON CODE WITH QUICK CHIPS
                        // ==========================================
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Quick chips row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        "GMB20" to "۲۰٪ تخفیف",
                                        "VIP" to "۳۰٪ تخفیف VIP",
                                        "OFF10" to "۱۰٪ تخفیف"
                                    ).forEach { (code, label) ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (couponCode == code && isCouponSuccess) brandCyan.copy(alpha = 0.2f) else Color(0xFF162032))
                                                .border(
                                                    1.dp,
                                                    if (couponCode == code && isCouponSuccess) brandCyan else Color(0xFF22314E),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    couponCode = code
                                                    when (code) {
                                                        "GMB20" -> {
                                                            appliedDiscountPercent = 20
                                                            isCouponSuccess = true
                                                            couponMessage = "کد تخفیف ۲۰٪ اعمال شد!"
                                                        }
                                                        "VIP" -> {
                                                            appliedDiscountPercent = 30
                                                            isCouponSuccess = true
                                                            couponMessage = "کد تخفیف ویژه VIP ۳۰٪ اعمال شد!"
                                                        }
                                                        "OFF10" -> {
                                                            appliedDiscountPercent = 10
                                                            isCouponSuccess = true
                                                            couponMessage = "کد تخفیف ۱۰٪ اعمال شد!"
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (couponCode == code && isCouponSuccess) brandCyan else Color(0xFF94A3B8),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "کد تخفیف:",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }

                            // Coupon Input Row
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
                                                couponMessage = "کد تخفیف ۲۰٪ با موفقیت اعمال شد!"
                                            }
                                            "OFF10" -> {
                                                appliedDiscountPercent = 10
                                                isCouponSuccess = true
                                                couponMessage = "کد تخفیف ۱۰٪ با موفقیت اعمال شد!"
                                            }
                                            "VIP" -> {
                                                appliedDiscountPercent = 30
                                                isCouponSuccess = true
                                                couponMessage = "کد تخفیف VIP ۳۰٪ با موفقیت اعمال شد!"
                                            }
                                            "" -> {
                                                appliedDiscountPercent = 0
                                                isCouponSuccess = false
                                                couponMessage = "لطفاً کد تخفیف را وارد کنید."
                                            }
                                            else -> {
                                                appliedDiscountPercent = 0
                                                isCouponSuccess = false
                                                couponMessage = "کد تخفیف وارد شده نامعتبر است."
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
                                        focusedContainerColor = Color(0xFF090E1A),
                                        unfocusedContainerColor = Color(0xFF090E1A)
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
                                        .padding(horizontal = 4.dp),
                                    textAlign = TextAlign.Right
                                )
                            }
                        }

                        // ==========================================
                        // PAYMENT METHOD TABS
                        // ==========================================
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF090E1A))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                                .padding(4.dp)
                        ) {
                            PaymentTabButton(
                                title = "رمزارز تتر (USDT)",
                                isSelected = paymentTab == 1,
                                modifier = Modifier.weight(1f),
                                onClick = { paymentTab = 1 }
                            )
                            PaymentTabButton(
                                title = "کارت به کارت شتابی (آنی)",
                                isSelected = paymentTab == 0,
                                modifier = Modifier.weight(1f),
                                onClick = { paymentTab = 0 }
                            )
                        }

                        if (paymentTab == 0) {
                            // ==========================================
                            // METHOD 1: COMPREHENSIVE CARD TO CARD
                            // ==========================================
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 1. Realistic Iranian Debit Bank Card
                                IranianBankCardView(
                                    cardNumberFormatted = "۶۰۳۷ - ۹۹۷۵ - ۱۴۲۳ - ۸۸۹۰",
                                    rawCardNumber = "6037997514238890",
                                    bankName = "بانک ملی ایران",
                                    cardHolder = "پشتیبانی فنی گمبرون نت (GMB NET)",
                                    isCopied = cardCopied,
                                    onCopyCard = {
                                        clipboardManager.setText(AnnotatedString("6037997514238890"))
                                        cardCopied = true
                                        Toast.makeText(context, "شماره کارت کپی شد: ۶۰۳۷۹۹۷۵۱۴۲۳۸۸۹۰", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                // 2. Shaba / IBAN Card
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFF1E2E4A), RoundedCornerShape(14.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1527)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                clipboardManager.setText(AnnotatedString("IR820170000000123456789012"))
                                                shabaCopied = true
                                                Toast.makeText(context, "شماره شبا کپی شد!", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (shabaCopied) neonGreen.copy(alpha = 0.2f) else Color(0xFF1E293B))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (shabaCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                                    contentDescription = null,
                                                    tint = if (shabaCopied) neonGreen else brandCyan,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = if (shabaCopied) "کپی شد" else "کپی شبا",
                                                    color = if (shabaCopied) neonGreen else brandCyan,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "شماره شبا (انتقال پایا / ساتنا):",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "IR82 0170 0000 0012 3456 7890 12",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }
                                }

                                // 3. Exact Amount Quick Copier (Toman & Rial)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFF1E2E4A), RoundedCornerShape(14.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2F)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "جهت واریز بدون خطا در همراه بانک",
                                                color = Color(0xFF64748B),
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                text = "کپی مبلغ دقیق واریز:",
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Toman button
                                            Button(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(finalPayableToman.toString()))
                                                    amountTomanCopied = true
                                                    Toast.makeText(context, "مبلغ $finalPayableToman تومان کپی شد!", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (amountTomanCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                                        contentDescription = null,
                                                        tint = if (amountTomanCopied) neonGreen else brandGold,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "کپی تومان: ${NumberFormat.getNumberInstance(Locale.US).format(finalPayableToman)}",
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            // Rial button
                                            Button(
                                                onClick = {
                                                    val rialAmount = finalPayableToman * 10
                                                    clipboardManager.setText(AnnotatedString(rialAmount.toString()))
                                                    amountRialCopied = true
                                                    Toast.makeText(context, "مبلغ $rialAmount ریال کپی شد!", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (amountRialCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                                        contentDescription = null,
                                                        tint = if (amountRialCopied) neonGreen else brandCyan,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "کپی ریال: ${NumberFormat.getNumberInstance(Locale.US).format(finalPayableToman * 10)}",
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 4. Mobile Banking Apps quick guidance
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("آپ", "۷۲۴", "همراه کارت", "بلو", "بام").forEach { app ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF162032))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(app, color = Color(0xFF94A3B8), fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    Text(
                                        text = "پشتیبانی از کلیه همراه بانک‌ها:",
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp
                                    )
                                }

                                // 5. Payment Receipt Registration & Tracking Box
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFF1E2E4A), RoundedCornerShape(16.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1425)),
                                    shape = RoundedCornerShape(16.dp)
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
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(neonGreen.copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("شارژ آنی", color = neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text(
                                                text = "ثبت اطلاعات فیش و شماره پیگیری واریز:",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Tracking code input
                                        OutlinedTextField(
                                            value = trackingCode,
                                            onValueChange = { trackingCode = it },
                                            label = { Text("شماره پیگیری / کد ارجاع تراکنش", fontSize = 11.sp) },
                                            placeholder = { Text("مثال: ۱۲۳۴۵۶۷۸", color = Color(0xFF475569), fontSize = 11.sp) },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Right
                                            ),
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    val clip = clipboardManager.getText()?.text
                                                    if (!clip.isNullOrBlank()) {
                                                        trackingCode = clip.trim()
                                                        Toast.makeText(context, "کد چسبانده شد", Toast.LENGTH_SHORT).show()
                                                    }
                                                }) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentPaste,
                                                        contentDescription = "Paste",
                                                        tint = brandCyan,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = brandCyan,
                                                unfocusedBorderColor = Color(0xFF1E2E4A),
                                                focusedContainerColor = Color(0xFF090F1C),
                                                unfocusedContainerColor = Color(0xFF090F1C)
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Sender 4 digits
                                        OutlinedTextField(
                                            value = senderCardLast4,
                                            onValueChange = { if (it.length <= 4) senderCardLast4 = it },
                                            label = { Text("۴ رقم آخر کارت واریزکننده (اختیاری)", fontSize = 11.sp) },
                                            placeholder = { Text("مثال: ۵۴۲۱", color = Color(0xFF475569), fontSize = 11.sp) },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Right
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = brandCyan,
                                                unfocusedBorderColor = Color(0xFF1E2E4A),
                                                focusedContainerColor = Color(0xFF090F1C),
                                                unfocusedContainerColor = Color(0xFF090F1C)
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Action: Send to Telegram support with formatted invoice
                                        Button(
                                            onClick = {
                                                try {
                                                    val invoiceMsg = buildString {
                                                        append("سلام، درخواست خرید/تمدید اشتراک GMB NET:\n")
                                                        append("👤 نام کاربری (انگلیسی): ${checkoutUsername.ifBlank { activeSession?.username ?: "-" }}\n")
                                                        if (checkoutPhone.isNotBlank()) append("📱 شماره تلفن: $checkoutPhone\n")
                                                        append("📦 پلن انتخابی: ${plan.title}\n")
                                                        append("💰 مبلغ واریز: $finalPriceFormatted\n")
                                                        if (trackingCode.isNotBlank()) append("🔢 کد پیگیری: $trackingCode\n")
                                                        if (senderCardLast4.isNotBlank()) append("💳 ۴ رقم آخر کارت: $senderCardLast4\n")
                                                        append("📅 تاریخ تمدید: ${PersianDateHelper.getExpiryDateShamsiFormatted(0)}")
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
                                            Text("ارسال فیش و شماره پیگیری به تلگرام پشتیبانی", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else {
                            // ==========================================
                            // METHOD 2: USDT CRYPTO TRC-20
                            // ==========================================
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF10192C))
                                    .border(1.dp, Color(0xFF1E2E4A), RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(neonGreen.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text("شبکه TRC-20 (Tron)", color = neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "کیف پول تتر (USDT):",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }

                                // Estimated dollar price
                                val estimatedUsdt = String.format(Locale.US, "%.2f", finalPayableToman / 60000.0)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "~ $estimatedUsdt USDT",
                                        color = brandGold,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "مبلغ معادل تتر:",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.sp
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
                                        text = "TY2fCgY77vT1...73hG3s",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "لطفاً توجه فرمایید انتقال فقط از طریق شبکه Tron (TRC20) انجام گیرد. پس از انتقال، شناسه هش تراکنش (TxID) را برای پشتیبانی ارسال فرمایید.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Right,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = {
                                        try {
                                            val invoiceMsg = "سلام، مبلغ $estimatedUsdt تتر جهت تمدید اکانت ${activeSession?.username ?: "-"} واریز شد."
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
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ارسال هش تراکنش تتر به پشتیبانی تلگرام", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // ==========================================
                        // INSTANT CONFIRMATION & RENEWAL BUTTON
                        // ==========================================
                        Button(
                            onClick = {
                                isPurchasing = true
                                scope.launch {
                                    val db = AppDatabase.getDatabase(context)
                                    val session = db.userSessionDao().getActiveSessionOnce()
                                    val finalUsername = checkoutUsername.trim().ifBlank { 
                                        session?.username ?: "user_${System.currentTimeMillis() % 10000}" 
                                    }
                                    if (session != null) {
                                        val updatedDays = (session.remainingDays.coerceAtLeast(0)) + plan.days
                                        val updatedShamsiDate = PersianDateHelper.getExpiryDateShamsiFormatted(updatedDays)
                                        db.userSessionDao().saveSession(
                                            session.copy(
                                                username = finalUsername,
                                                sshUsername = finalUsername,
                                                remainingDays = updatedDays,
                                                shamsiFinishDate = updatedShamsiDate,
                                                status = "active"
                                            )
                                        )
                                        Toast.makeText(
                                            context,
                                            "اشتراک شما با موفقیت فعال شد!\nنام کاربری: $finalUsername\nاعتبار: $updatedDays روز (${updatedShamsiDate})",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        // Create a brand new active session for the new user!
                                        val newSession = UserSession(
                                            id = 1,
                                            username = finalUsername,
                                            token = "token_${System.currentTimeMillis()}",
                                            remainingDays = plan.days,
                                            finishDate = "",
                                            shamsiFinishDate = PersianDateHelper.getExpiryDateShamsiFormatted(plan.days),
                                            consumedTrafficMb = 0L,
                                            totalTrafficMb = 100L * 1024L,
                                            status = "active",
                                            sshHost = "gmb.server-vip.net",
                                            sshPort = 443,
                                            sshUsername = finalUsername,
                                            sshPassword = "gmb_pass_${finalUsername}",
                                            apiBaseUrl = "https://gmb.server-vip.net"
                                        )
                                        db.userSessionDao().saveSession(newSession)
                                        Toast.makeText(
                                            context,
                                            "اشتراک جدید شما برای نام کاربری $finalUsername با موفقیت تحویل و فعال شد!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    isPurchasing = false
                                    selectedPlan = null
                                }
                            },
                            enabled = !isPurchasing && checkoutUsername.trim().length >= 3 && (checkoutPhone.isBlank() || (checkoutPhone.startsWith("09") && checkoutPhone.length == 11)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7C3AED)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
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
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = neonGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "تایید پرداخت و فعال‌سازی فوری اشتراک",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        TextButton(onClick = { selectedPlan = null }) {
                            Text("انصراف و بستن پیش‌فاکتور", color = Color(0xFF64748B), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IranianBankCardView(
    cardNumberFormatted: String = "۶۰۳۷ - ۹۹۷۵ - ۱۴۲۳ - ۸۸۹۰",
    rawCardNumber: String = "6037997514238890",
    bankName: String = "بانک ملی ایران",
    cardHolder: String = "پشتیبانی فنی گمبرون نت (GMB NET)",
    isCopied: Boolean,
    onCopyCard: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(205.dp)
            .shadow(16.dp, RoundedCornerShape(22.dp), spotColor = Color(0xFF38BDF8).copy(alpha = 0.35f))
            .border(
                1.5.dp,
                Brush.linearGradient(
                    listOf(
                        Color(0xFF38BDF8).copy(alpha = 0.8f),
                        Color(0xFF818CF8).copy(alpha = 0.5f),
                        Color(0xFFF59E0B).copy(alpha = 0.7f)
                    )
                ),
                RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0F1E3D),
                            Color(0xFF0B1426),
                            Color(0xFF14274E)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            // Subtle decorative background circles
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .offset(x = 100.dp, y = (-50).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF38BDF8).copy(alpha = 0.05f))
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .offset(x = (-40).dp, y = 80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFA855F7).copy(alpha = 0.04f))
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Bank Name, Shetab logo badge & Contactless symbol
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Contactless wave icon
                        Icon(
                            imageVector = Icons.Default.Contactless,
                            contentDescription = "Contactless",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )

                        // Shetab / Debit card badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B).copy(alpha = 0.7f))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "شتاب",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = bankName,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Middle Row: Gold Chip + Quick Copy Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Golden EMV Chip Graphic
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFFFDE68A),
                                        Color(0xFFF59E0B),
                                        Color(0xFFD97706)
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFFB45309), RoundedCornerShape(6.dp))
                            .padding(2.dp)
                    ) {
                        // Inner chip grid line decoration
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            HorizontalDivider(color = Color(0xFF92400E).copy(alpha = 0.5f), thickness = 1.dp)
                            HorizontalDivider(color = Color(0xFF92400E).copy(alpha = 0.5f), thickness = 1.dp)
                        }
                    }

                    // Quick copy badge button directly on the card
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCopied) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFF38BDF8).copy(alpha = 0.2f))
                            .border(
                                1.dp,
                                if (isCopied) Color(0xFF10B981) else Color(0xFF38BDF8),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onCopyCard() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = if (isCopied) Color(0xFF10B981) else Color(0xFF38BDF8),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isCopied) "کپی شد ✓" else "کپی شماره کارت",
                                color = if (isCopied) Color(0xFF10B981) else Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Card Number display (High-contrast, large, beautifully spaced)
                Text(
                    text = cardNumberFormatted,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.8.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Bottom Row: Cardholder Name & Debit label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DEBIT CARD",
                        color = Color(0xFF64748B),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = cardHolder,
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
