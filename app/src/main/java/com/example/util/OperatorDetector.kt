package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OperatorType(
    val id: String,
    val persianName: String,
    val defaultProtocol: String,
    val isVmess: Boolean
) {
    IRANCELL("irancell", "ایرانسل (MTN)", "پروتکل پرسرعت VMess", true),
    RIGHTEL("rightel", "رایتل (Rightel)", "پروتکل پرسرعت VMess", true),
    MCI("mci", "همراه اول (MCI)", "پروتکل امن SSH Tunnel", false),
    WIFI_OR_OTHER("other", "وای‌فای / سایر اپراتورها", "پروتکل امن SSH Tunnel", false)
}

enum class RoutingPreference(val id: String, val title: String) {
    AUTO("auto", "تشخیص خودکار اپراتور (هوشمند)"),
    FORCE_IRANCELL("force_irancell", "اجبار به ایرانسل (VMess)"),
    FORCE_RIGHTEL("force_rightel", "اجبار به رایتل (VMess)"),
    FORCE_MCI("force_mci", "اجبار به همراه اول (SSH)")
}

object OperatorDetector {
    private const val PREFS_NAME = "operator_routing_prefs"
    private const val KEY_ROUTING_PREF = "routing_preference"

    private val _currentRoutingPref = MutableStateFlow(RoutingPreference.AUTO)
    val currentRoutingPref: StateFlow<RoutingPreference> = _currentRoutingPref.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_ROUTING_PREF, RoutingPreference.AUTO.id)
        _currentRoutingPref.value = RoutingPreference.values().find { it.id == saved } ?: RoutingPreference.AUTO
    }

    fun setRoutingPreference(context: Context, pref: RoutingPreference) {
        _currentRoutingPref.value = pref
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROUTING_PREF, pref.id)
            .apply()
    }

    /**
     * Inspects TelephonyManager and ConnectivityManager to detect the active Iranian mobile carrier.
     */
    fun detectOperator(context: Context): OperatorType {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

            val netOperator = tm?.networkOperator ?: ""
            val simOperator = tm?.simOperator ?: ""
            val netOperatorName = (tm?.networkOperatorName ?: "").lowercase()
            val simOperatorName = (tm?.simOperatorName ?: "").lowercase()

            // Irancell MCC+MNC identifiers: 43235, 43232, 43238, 43239, 43299
            if (netOperator.startsWith("43235") || netOperator.startsWith("43232") ||
                netOperator.startsWith("43238") || netOperator.startsWith("43239") ||
                netOperator.startsWith("43299") ||
                simOperator.startsWith("43235") || simOperator.startsWith("43232") ||
                simOperator.startsWith("43238") || simOperator.startsWith("43239") ||
                netOperatorName.contains("irancell") || netOperatorName.contains("mtn") ||
                simOperatorName.contains("irancell") || simOperatorName.contains("mtn")
            ) {
                return OperatorType.IRANCELL
            }

            // Rightel MCC+MNC identifiers: 43220, 43221
            if (netOperator.startsWith("43220") || netOperator.startsWith("43221") ||
                simOperator.startsWith("43220") || simOperator.startsWith("43221") ||
                netOperatorName.contains("rightel") || simOperatorName.contains("rightel")
            ) {
                return OperatorType.RIGHTEL
            }

            // MCI (Hamrah-e-Aval) MCC+MNC identifiers: 43211, 43214, 43219
            if (netOperator.startsWith("43211") || netOperator.startsWith("43214") ||
                netOperator.startsWith("43219") ||
                simOperator.startsWith("43211") || simOperator.startsWith("43214") ||
                simOperator.startsWith("43219") ||
                netOperatorName.contains("mci") || netOperatorName.contains("hamrah") ||
                simOperatorName.contains("mci") || simOperatorName.contains("hamrah")
            ) {
                return OperatorType.MCI
            }

            // Fallback: check if currently connected to WiFi
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(activeNet)
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return OperatorType.WIFI_OR_OTHER
            }

            return OperatorType.WIFI_OR_OTHER
        } catch (_: Exception) {
            return OperatorType.WIFI_OR_OTHER
        }
    }

    /**
     * Determines whether the connection should use VMess (for Irancell & Rightel)
     * or SSH (for Hamrah-e-Aval and others).
     */
    fun shouldUseVmess(context: Context): Boolean {
        return when (_currentRoutingPref.value) {
            RoutingPreference.FORCE_IRANCELL, RoutingPreference.FORCE_RIGHTEL -> true
            RoutingPreference.FORCE_MCI -> false
            RoutingPreference.AUTO -> {
                val detected = detectOperator(context)
                detected.isVmess // true for Irancell and Rightel, false for Hamrah-e-Aval & WiFi
            }
        }
    }

    /**
     * Returns an informative Persian summary of the active operator and chosen transport.
     */
    fun getActiveRoutingSummary(context: Context): Pair<OperatorType, String> {
        val detected = detectOperator(context)
        val isVmess = shouldUseVmess(context)
        val protocolDesc = if (isVmess) "پروتکل پرسرعت VMess (مخصوص ایرانسل و رایتل)" else "پروتکل امن SSH Tunnel (مخصوص همراه اول)"
        return Pair(detected, protocolDesc)
    }
}
