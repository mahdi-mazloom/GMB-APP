package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.UserSession
import com.example.data.database.VpnLog
import com.example.data.remote.ShahanPanelClient
import com.example.util.AppUpdateInfo
import com.example.util.AppUpdateManager
import com.example.util.PersianDateHelper
import com.example.vpn.SshVpnService
import com.example.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VpnViewModel(private val database: AppDatabase) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    val activeSession: StateFlow<UserSession?> = database.userSessionDao().getActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val logsList: StateFlow<List<VpnLog>> = database.vpnLogDao().getLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI exposed states connected directly to SshVpnService metrics
    val connectionStatus = SshVpnService.connectionStatus
    val rxBytes = SshVpnService.rxBytes
    val txBytes = SshVpnService.txBytes
    val duration = SshVpnService.connectedDuration

    private val _apiBaseUrl = MutableStateFlow(ShahanPanelClient.DEFAULT_BASE_URL)
    val apiBaseUrl: StateFlow<String> = _apiBaseUrl.asStateFlow()

    // In-App Update States
    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow<Float?>(null)
    val updateDownloadProgress: StateFlow<Float?> = _updateDownloadProgress.asStateFlow()

    private val _updateNotification = MutableStateFlow<String?>(null)
    val updateNotification: StateFlow<String?> = _updateNotification.asStateFlow()

    init {
        // Load stored Url on launch if any, or seed default active session
        viewModelScope.launch(Dispatchers.IO) {
            val session = database.userSessionDao().getActiveSessionOnce()
            if (session != null) {
                _apiBaseUrl.value = session.apiBaseUrl
                SshVpnService.currentHost = session.sshHost
                SshVpnService.currentPort = session.sshPort
                SshVpnService.currentUser = session.sshUsername
                SshVpnService.currentPass = session.sshPassword
                SshVpnService.currentUdpgwPort = session.udpgwPort
                // Automatically refresh user traffic and expiry from server
                refreshUserData()
            }
            // Check for updates in background
            delay(2000)
            checkForAppUpdates(currentVersionCode = 1, isManual = false)
        }
    }

    fun updateApiUrl(newUrl: String) {
        val cleanUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        _apiBaseUrl.value = cleanUrl
    }

    fun refreshUserData() {
        viewModelScope.launch(Dispatchers.IO) {
            val session = database.userSessionDao().getActiveSessionOnce() ?: return@launch
            try {
                val service = ShahanPanelClient.create(_apiBaseUrl.value)
                val userResponse = service.getUserInfo(
                    token = ShahanPanelClient.API_TOKEN,
                    method = "userinfo",
                    username = session.username
                )

                val userData = userResponse.data ?: return@launch
                val packageDays = when (val d = userData.days) {
                    is Number -> d.toInt()
                    is String -> d.toIntOrNull()
                    else -> null
                }
                val remainingDays = PersianDateHelper.calculateRemainingDays(
                    finishDateStr = userData.finishdate,
                    fallbackDays = packageDays ?: 30
                )
                val shamsiFinish = PersianDateHelper.formatToShamsiDate(
                    rawDate = userData.finishdate,
                    fallbackDays = remainingDays
                )
                val totalTrafficMb = userData.traffic?.toLongOrNull() ?: 0L

                var consumedTrafficMb = session.consumedTrafficMb
                try {
                    val trafficResponse = service.getUserTraffic(
                        token = ShahanPanelClient.API_TOKEN,
                        method = "getusertraffic",
                        username = session.username
                    )
                    consumedTrafficMb = ShahanPanelClient.parseConsumedTrafficMb(trafficResponse.string())
                } catch (e: Exception) {
                    // traffic fetch optional
                }

                val sshConfig = ShahanPanelClient.parseSshConfig(
                    rawConfig = userData.sshnapsternetv,
                    fallbackUser = session.sshUsername,
                    fallbackPass = session.sshPassword
                )

                val updatedSession = session.copy(
                    remainingDays = remainingDays,
                    finishDate = userData.finishdate ?: "",
                    shamsiFinishDate = shamsiFinish,
                    consumedTrafficMb = consumedTrafficMb,
                    totalTrafficMb = totalTrafficMb,
                    status = userData.enable ?: "active",
                    sshHost = sshConfig.host,
                    sshPort = sshConfig.port,
                    udpgwPort = sshConfig.udpgwPort
                )
                database.userSessionDao().saveSession(updatedSession)
                SshVpnService.currentHost = updatedSession.sshHost
                SshVpnService.currentPort = updatedSession.sshPort
                SshVpnService.currentUdpgwPort = updatedSession.udpgwPort
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun login(username: String, password: String, isDemo: Boolean = false) {
        if (username.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("لطفاً نام کاربری و رمز عبور را وارد کنید")
            return
        }

        _loginState.value = LoginState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val service = ShahanPanelClient.create(_apiBaseUrl.value)
                val userResponse = service.getUserInfo(
                    token = ShahanPanelClient.API_TOKEN,
                    method = "userinfo",
                    username = username.trim()
                )

                val userData = userResponse.data
                if (userData == null || userData.username.isNullOrBlank()) {
                    _loginState.value = LoginState.Error("کاربری با نام کاربری '${username.trim()}' در سرور یافت نشد.")
                    return@launch
                }

                // Verify password against server credentials
                val serverPass = userData.password ?: ""
                if (serverPass.trim() != password.trim()) {
                    _loginState.value = LoginState.Error("رمز عبور وارد شده نادرست است.")
                    return@launch
                }

                // Check remaining days & Shamsi expiry date
                val packageDays = when (val d = userData.days) {
                    is Number -> d.toInt()
                    is String -> d.toIntOrNull()
                    else -> null
                }
                val remainingDays = PersianDateHelper.calculateRemainingDays(
                    finishDateStr = userData.finishdate,
                    fallbackDays = packageDays ?: 30
                )
                val shamsiFinish = PersianDateHelper.formatToShamsiDate(
                    rawDate = userData.finishdate,
                    fallbackDays = remainingDays
                )
                val totalTrafficMb = userData.traffic?.toLongOrNull() ?: 0L

                // Fetch real consumed traffic from getusertraffic
                var consumedTrafficMb = 0L
                try {
                    val trafficResponse = service.getUserTraffic(
                        token = ShahanPanelClient.API_TOKEN,
                        method = "getusertraffic",
                        username = username.trim()
                    )
                    consumedTrafficMb = ShahanPanelClient.parseConsumedTrafficMb(trafficResponse.string())
                } catch (e: Exception) {
                    // ignore
                }

                val sshConfig = ShahanPanelClient.parseSshConfig(
                    rawConfig = userData.sshnapsternetv,
                    fallbackUser = username.trim(),
                    fallbackPass = password.trim()
                )

                val userSession = UserSession(
                    username = userData.username,
                    token = ShahanPanelClient.API_TOKEN,
                    remainingDays = remainingDays,
                    finishDate = userData.finishdate ?: "",
                    shamsiFinishDate = shamsiFinish,
                    consumedTrafficMb = consumedTrafficMb,
                    totalTrafficMb = totalTrafficMb,
                    status = userData.enable ?: "active",
                    sshHost = sshConfig.host,
                    sshPort = sshConfig.port,
                    sshUsername = sshConfig.username,
                    sshPassword = sshConfig.password,
                    apiBaseUrl = _apiBaseUrl.value,
                    udpgwPort = sshConfig.udpgwPort
                )

                database.userSessionDao().saveSession(userSession)
                SshVpnService.currentHost = userSession.sshHost
                SshVpnService.currentPort = userSession.sshPort
                SshVpnService.currentUser = userSession.sshUsername
                SshVpnService.currentPass = userSession.sshPassword
                SshVpnService.currentUdpgwPort = userSession.udpgwPort
                database.vpnLogDao().insertLog(VpnLog(message = "احراز هویت موفق: ${userSession.username} | سرور: ${userSession.sshHost}:${userSession.sshPort}", level = "SUCCESS"))

                _loginState.value = LoginState.Success
            } catch (netEx: Exception) {
                val errMsg = netEx.localizedMessage ?: "خطا در برقراری ارتباط با سرور"
                _loginState.value = LoginState.Error("خطا در ارتباط با سرور: $errMsg")
            }
        }
    }

    fun logout(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            stopVpnService(context) // Make sure VPN stops
            database.userSessionDao().clearSession()
            database.vpnLogDao().clearLogs()
            _loginState.value = LoginState.Idle
        }
    }

    fun toggleVpn(context: Context) {
        if (connectionStatus.value == VpnStatus.CONNECTED) {
            stopVpnService(context)
        } else if (connectionStatus.value == VpnStatus.DISCONNECTED || connectionStatus.value == VpnStatus.ERROR) {
            startVpnService(context)
        }
    }

    fun startVpnService(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = database.userSessionDao().getActiveSessionOnce()
            val daysLeft = if (!session?.finishDate.isNullOrBlank()) {
                PersianDateHelper.calculateRemainingDays(session.finishDate)
            } else {
                session?.remainingDays ?: 0
            }
            val isExpired = session?.status == "expired" || daysLeft <= 0

            if (isExpired) {
                logError("امکان اتصال وجود ندارد: اشتراک کاربر به پایان رسیده است.")
                SshVpnService.connectionStatus.value = VpnStatus.DISCONNECTED
                stopVpnService(context)
                return@launch
            }

            val host = session?.sshHost ?: "ssh.mahdis-net.ir"
            val port = session?.sshPort ?: 2280
            val user = session?.sshUsername ?: "mahdi"
            val pass = session?.sshPassword ?: "109109"
            val udpgw = session?.udpgwPort ?: 7302

            // Sync with service
            SshVpnService.currentHost = host
            SshVpnService.currentPort = port
            SshVpnService.currentUser = user
            SshVpnService.currentPass = pass
            SshVpnService.currentUdpgwPort = udpgw

            // Check if VpnService can prepare or needs permission
            val intent = VpnService.prepare(context)
            if (intent == null) {
                // VPN already holds permissions, start immediately
                val startIntent = Intent(context, SshVpnService::class.java).apply {
                    action = SshVpnService.ACTION_START
                }
                try {
                    androidx.core.content.ContextCompat.startForegroundService(context, startIntent)
                } catch (e: Exception) {
                    context.startService(startIntent)
                }
            } else {
                // Return status and UI will handle permission launcher
                _loginState.value = LoginState.VpnPermissionRequired(intent)
            }
        }
    }

    fun startVpnWithPermissionGranted(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = database.userSessionDao().getActiveSessionOnce()
            val daysLeft = if (!session?.finishDate.isNullOrBlank()) {
                PersianDateHelper.calculateRemainingDays(session.finishDate)
            } else {
                session?.remainingDays ?: 0
            }
            val isExpired = session?.status == "expired" || daysLeft <= 0

            if (isExpired) {
                logError("امکان اتصال وجود ندارد: اشتراک کاربر به پایان رسیده است.")
                SshVpnService.connectionStatus.value = VpnStatus.DISCONNECTED
                stopVpnService(context)
                return@launch
            }

            val startIntent = Intent(context, SshVpnService::class.java).apply {
                action = SshVpnService.ACTION_START
            }
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, startIntent)
            } catch (e: Exception) {
                context.startService(startIntent)
            }
        }
    }

    fun onVpnPermissionHandled() {
        if (_loginState.value is LoginState.VpnPermissionRequired) {
            _loginState.value = LoginState.Idle
        }
    }

    private fun stopVpnService(context: Context) {
        val stopIntent = Intent(context, SshVpnService::class.java).apply {
            action = SshVpnService.ACTION_STOP
        }
        context.startService(stopIntent)
    }

    fun dismissUpdateDialog() {
        _availableUpdate.value = null
        _updateDownloadProgress.value = null
    }

    fun clearUpdateNotification() {
        _updateNotification.value = null
    }

    fun checkForAppUpdates(currentVersionCode: Int = 1, isManual: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingUpdate.value = true
            val result = AppUpdateManager.checkUpdate(currentVersionCode)
            _isCheckingUpdate.value = false
            result.onSuccess { update ->
                _availableUpdate.value = update
                if (update != null) {
                    _updateNotification.value = "نسخه جدید ${update.latestVersionName} در دسترس است!"
                    database.vpnLogDao().insertLog(
                        VpnLog(message = "نسخه جدید برنامه یافت شد: نسخه ${update.latestVersionName}", level = "INFO")
                    )
                } else if (isManual) {
                    _updateNotification.value = "شما در حال استفاده از آخرین نسخه هستید."
                }
            }.onFailure { e ->
                if (isManual) {
                    _updateNotification.value = "خطا در بررسی بروزرسانی سرور"
                }
            }
        }
    }

    fun triggerSimulatedUpdateForTesting() {
        // Allows the user or admin to preview the update experience immediately
        _availableUpdate.value = AppUpdateInfo(
            latestVersionCode = 2,
            latestVersionName = "1.2.0",
            isForceUpdate = false,
            changelog = "• افزایش چشمگیر سرعت و پایداری اتصال\n• پشتیبانی از اتصال بدون قطعی تلگرام و واتساپ\n• حل مشکل DNS و کاهش پینگ سرورها\n• بهینه‌سازی مصرف باتری و اینترنت",
            downloadUrl = "https://github.com/torproject/tor/releases/download/tor-browser-13.0.1/tor-browser-android-13.0.1.apk",
            releaseDate = "۱۴۰۳/۰۶/۱۶"
        )
    }

    fun downloadAndInstallUpdate(context: Context) {
        val update = _availableUpdate.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _updateDownloadProgress.value = 0.01f
            val result = AppUpdateManager.downloadAndInstall(
                context = context,
                downloadUrl = update.downloadUrl,
                onProgress = { progress ->
                    _updateDownloadProgress.value = progress
                }
            )
            result.onFailure { error ->
                _updateDownloadProgress.value = null
                _updateNotification.value = "خطا در دانلود و نصب: ${error.message}"
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            database.vpnLogDao().clearLogs()
        }
    }

    fun logError(message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.vpnLogDao().insertLog(VpnLog(message = message, level = "ERROR"))
        }
    }

    // Factory to construct safe dependency injection structure
    class Factory(private val database: AppDatabase) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VpnViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return VpnViewModel(database) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val error: String) : LoginState()
    data class VpnPermissionRequired(val intent: Intent) : LoginState()
}
