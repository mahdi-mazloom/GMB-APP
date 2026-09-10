package com.example.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.min
import kotlin.random.Random

/**
 * Intelligent Reconnection Service for GMB NET VPN.
 * Continuously monitors:
 * 1. Physical Android network transitions (Wi-Fi, Cellular, Airplane mode, disconnects/reconnects).
 * 2. Underlying SSH tunnel and socket liveness via active watchdog & keepalive probes.
 * Automatically recovers and re-establishes the tunnel with exponential backoff and zero manual intervention.
 */
class VpnReconnectionManager(
    private val context: Context,
    private val vpnService: SshVpnService,
    private val scope: CoroutineScope,
    private val onLog: (message: String, level: String) -> Unit,
    private val onReconnectAction: suspend (attempt: Int, reason: String) -> Boolean
) {
    companion object {
        private const val TAG = "VpnReconnectionManager"
        private const val BASE_DELAY_MS = 1500L
        private const val MAX_DELAY_MS = 12000L
        private const val WATCHDOG_INTERVAL_MS = 2500L
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var watchdogJob: Job? = null
    private var reconnectJob: Job? = null

    @Volatile
    private var isUserDesiredActive = false

    @Volatile
    private var isReconnecting = false

    @Volatile
    private var activeNetwork: Network? = null

    val isNetworkAvailable = MutableStateFlow(true)
    val reconnectAttempts = MutableStateFlow(0)
    val lastReconnectReason = MutableStateFlow<String?>(null)

    fun start() {
        isUserDesiredActive = true
        reconnectAttempts.value = 0
        registerNetworkCallback()
        startWatchdog()
    }

    fun stop() {
        isUserDesiredActive = false
        isReconnecting = false
        reconnectJob?.cancel()
        reconnectJob = null
        stopWatchdog()
        unregisterNetworkCallback()
        reconnectAttempts.value = 0
        lastReconnectReason.value = null
    }

    fun onConnected() {
        reconnectAttempts.value = 0
        isReconnecting = false
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun registerNetworkCallback() {
        if (connectivityManager == null || networkCallback != null) return

        try {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val wasAvailable = isNetworkAvailable.value
                    activeNetwork = network
                    isNetworkAvailable.value = true

                    Log.i(TAG, "Network became available: $network")
                    vpnService.updateUnderlyingNetwork(network)

                    if (isUserDesiredActive) {
                        if (!wasAvailable) {
                            onLog("شبکه اینترنت مجدداً وصل شد. راه‌اندازی خودکار اتصال امن...", "INFO")
                            triggerReconnect("بازیابی اتصال شبکه اینترنت")
                        } else if (SshVpnService.connectionStatus.value != VpnStatus.CONNECTED && !isReconnecting) {
                            triggerReconnect("در دسترس قرار گرفتن شبکه جدید")
                        }
                    }
                }

                override fun onLost(network: Network) {
                    Log.w(TAG, "Network lost: $network")
                    if (activeNetwork == network) {
                        activeNetwork = null
                    }

                    val hasActive = checkCurrentInternetConnectivity()
                    isNetworkAvailable.value = hasActive

                    if (!hasActive && isUserDesiredActive) {
                        onLog("ارتباط شبکه اینترنت دستگاه قطع شد. در انتظار اتصال مجدد...", "WARN")
                        vpnService.onNetworkLostNotification()
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                    if (hasInternet && activeNetwork != network) {
                        val isPreviousCellular = activeNetwork != null
                        activeNetwork = network
                        vpnService.updateUnderlyingNetwork(network)

                        if (isUserDesiredActive && SshVpnService.connectionStatus.value == VpnStatus.CONNECTED) {
                            // Interface handover detected (e.g. Wi-Fi <-> Cellular)
                            onLog("تغییر رابط شبکه اینترنت شناسایی شد (Wi-Fi / Cellular Handover). بررسی پایداری تونل...", "INFO")
                            scope.launch {
                                delay(1200)
                                if (!vpnService.isTunnelHealthy()) {
                                    triggerReconnect("جابه‌جایی شبکه بین وای‌فای و دیتا")
                                }
                            }
                        }
                    }
                }
            }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(request, callback)
            networkCallback = callback
            isNetworkAvailable.value = checkCurrentInternetConnectivity()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { cb ->
            try {
                connectivityManager?.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister network callback: ${e.message}")
            }
            networkCallback = null
        }
    }

    private fun checkCurrentInternetConnectivity(): Boolean {
        return try {
            val cm = connectivityManager ?: return true
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch(Dispatchers.IO) {
            while (isActive && isUserDesiredActive) {
                delay(WATCHDOG_INTERVAL_MS)

                if (!isUserDesiredActive) break

                val status = SshVpnService.connectionStatus.value
                if (status == VpnStatus.CONNECTED) {
                    val healthy = vpnService.isTunnelHealthy()
                    if (!healthy) {
                        Log.w(TAG, "Watchdog detected dead tunnel!")
                        onLog("قطع اتصال تونل امن شناسایی شد (Watchdog Alert)! تلاش برای اتصال مجدد...", "WARN")
                        triggerReconnect("قطع ناگهانی ارتباط تونل سرور")
                    }
                }
            }
        }
    }

    private fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    fun triggerReconnect(reason: String) {
        if (!isUserDesiredActive || isReconnecting) return

        lastReconnectReason.value = reason
        isReconnecting = true

        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            var attempt = 0
            while (isActive && isUserDesiredActive) {
                attempt++
                reconnectAttempts.value = attempt

                // If device has no internet at all, wait until network becomes available
                while (isActive && isUserDesiredActive && !checkCurrentInternetConnectivity()) {
                    isNetworkAvailable.value = false
                    onLog("اینترنت در دسترس نیست. سرویس اتصال خودکار منتظر بازگشت شبکه می‌ماند...", "WARN")
                    vpnService.onWaitingForNetworkNotification()
                    delay(3000)
                }

                if (!isUserDesiredActive) break

                isNetworkAvailable.value = true
                val delayMs = calculateBackoff(attempt)
                onLog("تلاش شماره $attempt برای برقراری مجدد اتصال (دلیل: $reason)...", "INFO")
                vpnService.onReconnectingNotification(attempt)

                val success = try {
                    onReconnectAction(attempt, reason)
                } catch (e: Exception) {
                    Log.e(TAG, "Reconnection attempt $attempt threw exception", e)
                    false
                }

                if (success) {
                    onLog("اتصال مجدد خودکار با موفقیت انجام شد!", "SUCCESS")
                    reconnectAttempts.value = 0
                    isReconnecting = false
                    break
                } else {
                    onLog("تلاش شماره $attempt ناموفق بود. تلاش مجدد در ${(delayMs / 1000)} ثانیه...", "WARN")
                    delay(delayMs)
                }
            }
        }
    }

    private fun calculateBackoff(attempt: Int): Long {
        val exp = min(attempt - 1, 4)
        val base = BASE_DELAY_MS * (1 shl exp)
        val jitter = Random.nextLong(200, 800)
        return min(base + jitter, MAX_DELAY_MS)
    }
}
