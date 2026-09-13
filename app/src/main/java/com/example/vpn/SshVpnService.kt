package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.database.VpnLog
import com.example.data.remote.ShahanPanelClient
import com.example.util.OperatorDetector
import com.example.util.OperatorType
import com.example.util.PersianDateHelper
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Properties

class SshVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var activeTransport: TunnelTransport? = null
    private var sshSession: Session? = null
    private var socksServer: LocalSocksServer? = null
    private var tunEngine: Tun2SocksEngine? = null
    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var reconnectionManager: VpnReconnectionManager? = null

    // Tracks connection metrics
    private var statsJob: Job? = null

    companion object {
        const val CHANNEL_ID = "SshVpnServiceChannel"
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"

        val connectionStatus = MutableStateFlow(VpnStatus.DISCONNECTED)
        val rxBytes = MutableStateFlow(0L)
        val txBytes = MutableStateFlow(0L)
        val connectedDuration = MutableStateFlow(0L) // in seconds

        val isNetworkAvailable = MutableStateFlow(true)
        val reconnectAttempts = MutableStateFlow(0)
        val lastReconnectReason = MutableStateFlow<String?>(null)
        val activeProtocolName = MutableStateFlow("اتصال خودکار")
        
        var currentHost = "ssh.mahdis-net.ir"
        var currentPort = 2280
        var currentUser = "mahdi"
        var currentPass = "109109"
        var currentUdpgwPort = 7302
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initReconnectionManager()
    }

    private fun initReconnectionManager() {
        val manager = VpnReconnectionManager(
            context = applicationContext,
            vpnService = this,
            scope = serviceScope,
            onLog = { message, level ->
                serviceScope.launch {
                    logToDatabase(message, level)
                }
            },
            onReconnectAction = { attempt, reason ->
                executeTunnelConnection(isReconnect = true, attempt = attempt)
            }
        )

        serviceScope.launch {
            manager.isNetworkAvailable.collect { isNetworkAvailable.value = it }
        }
        serviceScope.launch {
            manager.reconnectAttempts.collect { reconnectAttempts.value = it }
        }
        serviceScope.launch {
            manager.lastReconnectReason.collect { lastReconnectReason.value = it }
        }
        reconnectionManager = manager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START) {
            startVpn()
        } else if (action == ACTION_STOP) {
            stopVpn()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        serviceScope.cancel()
    }

    private fun startVpn() {
        if (connectionStatus.value == VpnStatus.CONNECTED || connectionStatus.value == VpnStatus.CONNECTING) {
            return
        }

        connectionStatus.value = VpnStatus.CONNECTING
        rxBytes.value = 0L
        txBytes.value = 0L
        connectedDuration.value = 0L

        createNotificationChannel()

        // Start Foreground Notification safely
        val notification = createNotification("در حال اتصال به GMB NET...", "GMB NET")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            Log.e("SshVpnService", "startForeground error, falling back", e)
            try {
                startForeground(1, notification)
            } catch (ex: Exception) {
                Log.e("SshVpnService", "startForeground failed completely", ex)
            }
        }

        reconnectionManager?.start()

        serviceJob = serviceScope.launch {
            val success = executeTunnelConnection(isReconnect = false, attempt = 1)
            if (!success) {
                // If initial attempt encounters a transient issue, let reconnection service retry automatically
                reconnectionManager?.triggerReconnect("عدم موفقیت در برقراری اتصال اولیه")
            }
        }
    }

    private suspend fun executeTunnelConnection(isReconnect: Boolean, attempt: Int): Boolean {
        if (isReconnect) {
            connectionStatus.value = VpnStatus.RECONNECTING
            updateNotification("در حال اتصال مجدد خودکار (تلاش $attempt)...", "GMB NET")
            cleanupTunnelComponents()
        } else {
            logToDatabase("شروع فرآیند اتصال به تونل امن GMB NET...", "INFO")
        }

        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val session = db.userSessionDao().getActiveSessionOnce()

            // 1. Strict Shahan Panel Expiration Verification
            if (session != null && !session.username.isNullOrBlank()) {
                logToDatabase("در حال استعلام وضعیت اشتراک و زمان باقیمانده از پنل شاهان...", "INFO")
                try {
                    val shahanService = ShahanPanelClient.create(session.apiBaseUrl.ifEmpty { ShahanPanelClient.DEFAULT_BASE_URL })
                    val userResponse = shahanService.getUserInfo(
                        token = ShahanPanelClient.API_TOKEN,
                        method = "userinfo",
                        username = session.username
                    )
                    val userData = userResponse.data
                    if (userData != null) {
                        val packageDays = when (val d = userData.days) {
                            is Number -> d.toInt()
                            is String -> d.toIntOrNull()
                            else -> null
                        }
                        val remainingDays = PersianDateHelper.calculateRemainingDays(
                            finishDateStr = userData.finishdate,
                            fallbackDays = packageDays ?: 30
                        )
                        val isExpired = userData.enable == "disabled" || userData.enable == "expired" || remainingDays <= 0
                        if (isExpired) {
                            logToDatabase("خطا: اشتراک کاربری شما در پنل شاهان به اتمام رسیده است و امکان اتصال به هیچ کانفیگی وجود ندارد.", "ERROR")
                            connectionStatus.value = VpnStatus.ERROR
                            updateNotification("اشتراک شما به پایان رسیده است", "خطای اتصال")
                            stopVpn()
                            return false
                        }
                    }
                } catch (e: Exception) {
                    Log.w("SshVpnService", "Shahan panel live verification error: ${e.message}")
                    // If network temporary error during verification, verify local session expiry
                    val localDays = if (!session.finishDate.isNullOrBlank()) {
                        PersianDateHelper.calculateRemainingDays(session.finishDate)
                    } else {
                        session.remainingDays
                    }
                    if (session.status == "expired" || localDays <= 0) {
                        logToDatabase("خطا: زمان اشتراک شما به پایان رسیده است و اجازه اتصال داده نمی‌شود.", "ERROR")
                        connectionStatus.value = VpnStatus.ERROR
                        updateNotification("اشتراک شما به پایان رسیده است", "خطای اتصال")
                        stopVpn()
                        return false
                    }
                }
            }

            // 2. Network Carrier Detection & Protocol Routing
            val useVmess = OperatorDetector.shouldUseVmess(applicationContext)
            val detectedOperator = OperatorDetector.detectOperator(applicationContext)
            val operatorTitle = when (detectedOperator) {
                OperatorType.IRANCELL -> "ایرانسل (MTN)"
                OperatorType.RIGHTEL -> "رایتل (Rightel)"
                OperatorType.MCI -> "همراه اول (MCI)"
                else -> "وای‌فای / سایر اپراتورها"
            }
            logToDatabase("اپراتور فعال اینترنت: $operatorTitle", "INFO")

            val transport: TunnelTransport
            val targetHostForVpn: String

            if (useVmess) {
                // Irancell & Rightel: Route through dedicated VMess AEAD over WebSocket config
                logToDatabase("مسیردهی به پروتکل پرسرعت VMess WebSocket (اختصاصی ایرانسل و رایتل)...", "INFO")
                updateNotification(if (isReconnect) "اتصال مجدد VMess..." else "در حال اتصال به سرور پرسرعت VMess...")
                activeProtocolName.value = "VMess ($operatorTitle)"

                val vmessTransport = VmessTunnelTransport(
                    vpnService = this,
                    config = VmessDefaultConfig.INSTANCE,
                    onTunnelDropped = {
                        reconnectionManager?.triggerReconnect("قطع اتصال لایه VMess")
                    }
                )
                transport = vmessTransport
                activeTransport = vmessTransport
                targetHostForVpn = VmessDefaultConfig.INSTANCE.serverHost
                logToDatabase("کانفیگ VMess WebSocket متصل و آماده تبادل داده است.", "SUCCESS")
            } else {
                // Hamrah-e-Aval (MCI) & others: Route through SSH Tunnel
                val host = currentHost.ifEmpty { session?.sshHost ?: "ssh.mahdis-net.ir" }
                val port = if (currentPort != 22) currentPort else (session?.sshPort ?: 2280)
                val user = currentUser.ifEmpty { session?.sshUsername ?: "mahdi" }
                val pass = currentPass.ifEmpty { session?.sshPassword ?: "109109" }
                val udpgw = if (currentUdpgwPort > 0) currentUdpgwPort else (session?.udpgwPort ?: 7302)

                logToDatabase("مسیردهی به پروتکل اختصاصی SSH Tunnel (اختصاصی همراه اول)...", "INFO")
                updateNotification(if (isReconnect) "اتصال مجدد SSH..." else "در حال برقراری تونل امن SSH...")
                activeProtocolName.value = "SSH ($operatorTitle)"

                val jsch = JSch()
                val socketFactory = object : SocketFactory {
                    override fun createSocket(h: String, p: Int): Socket {
                        val s = Socket()
                        protect(s)
                        s.tcpNoDelay = true
                        s.keepAlive = true
                        s.connect(InetSocketAddress(h, p), 15000)
                        return s
                    }
                    override fun getInputStream(s: Socket): InputStream = s.getInputStream()
                    override fun getOutputStream(s: Socket): OutputStream = s.getOutputStream()
                }

                val newSshSession = jsch.getSession(user, host, port).apply {
                    setPassword(pass)
                    setSocketFactory(socketFactory)
                    val config = Properties().apply {
                        put("StrictHostKeyChecking", "no")
                        put("ServerAliveInterval", "25")
                        put("PreferredAuthentications", "password,keyboard-interactive")
                    }
                    setConfig(config)
                    userInfo = object : UserInfo, UIKeyboardInteractive {
                        override fun getPassphrase(): String? = null
                        override fun getPassword(): String = pass
                        override fun promptPassword(message: String?): Boolean = true
                        override fun promptPassphrase(message: String?): Boolean = true
                        override fun promptYesNo(message: String?): Boolean = true
                        override fun showMessage(message: String?) {}
                        override fun promptKeyboardInteractive(
                            destination: String?, name: String?, instruction: String?,
                            prompt: Array<out String>?, echo: BooleanArray?
                        ): Array<String> = Array(prompt?.size ?: 1) { pass }
                    }
                }

                newSshSession.connect(15000)
                sshSession = newSshSession

                if (udpgw > 0) {
                    try {
                        newSshSession.setPortForwardingL(udpgw, "127.0.0.1", udpgw)
                    } catch (_: Exception) {}
                }

                val sshTransport = SshTunnelTransport(newSshSession)
                transport = sshTransport
                activeTransport = sshTransport
                targetHostForVpn = host
                logToDatabase("رمزنگاری SSH با موفقیت تایید شد.", "SUCCESS")
            }

            // 3. Start local SOCKS5 proxy server routed through the active transport
            val localSocksPort = 10808
            socksServer?.stop()
            val server = LocalSocksServer(transport, localSocksPort) { rx, tx ->
                if (rx > 0) rxBytes.value += rx
                if (tx > 0) txBytes.value += tx
            }
            server.start()
            socksServer = server
            logToDatabase("پروکسی محلی روی پورت $localSocksPort فعال شد.", "SUCCESS")

            // 4. Establish the Vpn Interface and smart packet routing
            setupVpnInterface(localSocksPort, targetHostForVpn, transport)

            if (vpnInterface != null) {
                val successMessage = if (useVmess) "اتصال با پروتکل VMess (ایرانسل/رایتل) با موفقیت برقرار شد!" else "اتصال با پروتکل SSH (همراه اول) با موفقیت برقرار شد!"
                logToDatabase(successMessage, "SUCCESS")
                connectionStatus.value = VpnStatus.CONNECTED
                updateNotification(if (useVmess) "متصل به VMess (ایرانسل/رایتل)" else "متصل به SSH (همراه اول)", "GMB NET")

                // Acquire partial wakeLock
                try {
                    val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    if (wakeLock == null || wakeLock?.isHeld != true) {
                        wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "GmbNet:VpnWakeLock")?.apply {
                            setReferenceCounted(false)
                            acquire(12 * 60 * 60 * 1000L)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("SshVpnService", "Could not acquire WakeLock: ${e.message}")
                }

                reconnectionManager?.onConnected()
                startStatsMonitoring()
                true
            } else {
                throw IOException("امکان ایجاد رابط کاربری شبکه VPN مقدور نیست")
            }

        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.message ?: "خطای ناشناخته در اتصال"
            Log.e("SshVpnService", "Error during connection setup (isReconnect=$isReconnect)", e)
            logToDatabase("خطا در برقراری ارتباط: $errorMsg", if (isReconnect) "WARN" else "ERROR")
            if (!isReconnect) {
                connectionStatus.value = VpnStatus.ERROR
                updateNotification("اتصال ناموفق")
            }
            false
        }
    }

    private suspend fun setupVpnInterface(localSocksPort: Int, host: String, transport: TunnelTransport) {
        try {
            if (vpnInterface == null) {
                val builder = Builder()
                    .setSession("GMB NET - $host")
                    .setMtu(1500)
                    .addAddress("10.0.0.2", 24)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("1.1.1.1")
                    .addRoute("0.0.0.0", 0)
                    .addRoute("198.18.0.0", 15) // Explicit routing for Fake-IP benchmark block

                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    Log.w("SshVpnService", "Could not disallow app package: ${e.message}")
                }

                vpnInterface = builder.establish()
                logToDatabase("رابط شبکه محلی VPN ایجاد شد (10.0.0.2).", "INFO")
            }
            
            tunEngine?.stop()
            val pfd = vpnInterface
            if (pfd != null) {
                val engine = Tun2SocksEngine(
                    vpnService = this,
                    vpnInterface = pfd,
                    transport = transport,
                    localSocksPort = localSocksPort,
                    onTunnelDropped = {
                        reconnectionManager?.triggerReconnect("قطع اتصال در لایه ارسال پکت‌ها")
                    }
                ) { rx, tx ->
                    if (rx > 0) rxBytes.value += rx
                    if (tx > 0) txBytes.value += tx
                }
                engine.start()
                tunEngine = engine
                logToDatabase("موتور مسیریابی هوشمند پکت‌ها فعال گردید.", "SUCCESS")
            }
        } catch (e: Exception) {
            Log.e("SshVpnService", "Failed to establish VPN interface", e)
            logToDatabase("خطا در ایجاد کارت شبکه مجازی VPN: ${e.message}", "ERROR")
            throw e
        }
    }

    private fun startStatsMonitoring() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            var seconds = connectedDuration.value
            while (isActive && connectionStatus.value == VpnStatus.CONNECTED) {
                delay(1000)
                seconds++
                connectedDuration.value = seconds
            }
        }
    }

    fun isTunnelHealthy(): Boolean {
        val transport = activeTransport ?: return false
        return transport.isConnected
    }

    fun onReconnectingNotification(attempt: Int) {
        updateNotification("قطع ارتباط - در حال اتصال مجدد خودکار (تلاش $attempt)...", "GMB NET")
    }

    fun onNetworkLostNotification() {
        updateNotification("اینترنت قطع شد - در انتظار اتصال به شبکه...", "GMB NET")
    }

    fun onWaitingForNetworkNotification() {
        updateNotification("در انتظار برقراری اینترنت برای اتصال مجدد...", "GMB NET")
    }

    fun updateUnderlyingNetwork(network: android.net.Network?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                setUnderlyingNetworks(if (network != null) arrayOf(network) else null)
            } catch (e: Exception) {
                Log.w("SshVpnService", "Could not set underlying networks: ${e.message}")
            }
        }
    }

    private fun cleanupTunnelComponents() {
        try {
            tunEngine?.stop()
        } catch (e: Exception) {}
        tunEngine = null

        try {
            socksServer?.stop()
        } catch (e: Exception) {}
        socksServer = null

        try {
            activeTransport?.close()
        } catch (e: Exception) {}
        activeTransport = null

        try {
            sshSession?.disconnect()
        } catch (e: Exception) {}
        sshSession = null
    }

    private fun stopVpn() {
        reconnectionManager?.stop()

        serviceScope.launch {
            logToDatabase("در حال قطع اتصال GMB NET...", "INFO")
        }
        connectionStatus.value = VpnStatus.DISCONNECTED
        
        cleanupTunnelComponents()

        statsJob?.cancel()
        statsJob = null

        serviceJob?.cancel()
        serviceJob = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {}
        vpnInterface = null

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null

        serviceScope.launch {
            logToDatabase("اتصال VPN متوقف شد. ارتباط بسته شد.", "INFO")
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w("SshVpnService", "stopForeground error: ${e.message}")
        }
        stopSelf()
    }

    private suspend fun logToDatabase(message: String, level: String) {
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val sanitizedMsg = com.example.util.LogSanitizer.sanitize(message)
            db.vpnLogDao().insertLog(VpnLog(message = sanitizedMsg, level = level))
        } catch (e: Exception) {
            Log.e("SshVpnService", "Failed to save log to DB", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "GMB NET Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "GMB NET Connection Status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String, title: String = "GMB NET"): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun updateNotification(contentText: String, title: String = "GMB NET") {
        val notification = createNotification(contentText, title)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(1, notification)
    }
}
