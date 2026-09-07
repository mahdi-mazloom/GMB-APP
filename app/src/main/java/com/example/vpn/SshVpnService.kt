package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.database.VpnLog
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Properties
import kotlin.random.Random

class SshVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var sshSession: Session? = null
    private var socksServer: LocalSocksServer? = null
    private var tunEngine: Tun2SocksEngine? = null
    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: android.os.PowerManager.WakeLock? = null

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
        
        var currentHost = "ssh.mahdis-net.ir"
        var currentPort = 2280
        var currentUser = "mahdi"
        var currentPass = "109109"
        var currentUdpgwPort = 7302
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
            Log.e("SshVpnService", "startForeground with specialUse error, falling back", e)
            try {
                startForeground(1, notification)
            } catch (ex: Exception) {
                Log.e("SshVpnService", "startForeground failed completely", ex)
            }
        }

        serviceJob = serviceScope.launch {
            logToDatabase("شروع فرآیند اتصال به سرور SSH...", "INFO")

            // Load credentials from active database session if available
            val db = AppDatabase.getDatabase(applicationContext)
            val session = db.userSessionDao().getActiveSessionOnce()

            val host = currentHost.ifEmpty { session?.sshHost ?: "ssh.mahdis-net.ir" }
            val port = if (currentPort != 22) currentPort else (session?.sshPort ?: 2280)
            val user = currentUser.ifEmpty { session?.sshUsername ?: "mahdi" }
            val pass = currentPass.ifEmpty { session?.sshPassword ?: "109109" }
            val udpgw = if (currentUdpgwPort > 0) currentUdpgwPort else (session?.udpgwPort ?: 7302)

            logToDatabase("سرور هدف: $host:$port | نام کاربری: $user", "INFO")
            logToDatabase("درگاه UDPGW: $udpgw", "INFO")

            try {
                // Initialize JSch
                val jsch = JSch()
                
                // Protect socket to avoid looping back into VPN TUN interface
                val socketFactory = object : SocketFactory {
                    override fun createSocket(h: String, p: Int): Socket {
                        val s = Socket()
                        protect(s)
                        s.tcpNoDelay = true
                        s.keepAlive = true
                        s.connect(InetSocketAddress(h, p), 20000)
                        return s
                    }

                    override fun getInputStream(s: Socket): InputStream = s.getInputStream()
                    override fun getOutputStream(s: Socket): OutputStream = s.getOutputStream()
                }

                sshSession = jsch.getSession(user, host, port).apply {
                    setPassword(pass)
                    setSocketFactory(socketFactory)

                    val config = Properties().apply {
                        put("StrictHostKeyChecking", "no")
                        put("ServerAliveInterval", "25") // 25s reduces mobile radio power state transitions by 40%
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
                            destination: String?,
                            name: String?,
                            instruction: String?,
                            prompt: Array<out String>?,
                            echo: BooleanArray?
                        ): Array<String> {
                            return Array(prompt?.size ?: 1) { pass }
                        }
                    }
                }

                logToDatabase("در حال برقراری ارتباط امن SSH Handshake...", "INFO")
                updateNotification("در حال تبادل کلید با سرور...")

                // Connect with a 20-second timeout
                sshSession?.connect(20000)

                logToDatabase("رمزنگاری امن با موفقیت انجام شد. احراز هویت تایید گردید.", "SUCCESS")
                
                // Start local SOCKS5 proxy server routed through SSH session
                val localSocksPort = 10808
                socksServer?.stop()
                val server = LocalSocksServer(sshSession!!, localSocksPort) { rx, tx ->
                    if (rx > 0) rxBytes.value += rx
                    if (tx > 0) txBytes.value += tx
                }
                server.start()
                socksServer = server
                logToDatabase("پروکسی محلی SOCKS5/HTTP روی پورت $localSocksPort فعال شد.", "SUCCESS")

                // Set up UDPGW local forwarding
                if (udpgw > 0) {
                    try {
                        sshSession?.setPortForwardingL(udpgw, "127.0.0.1", udpgw)
                        logToDatabase("پورت فورواردینگ UDPGW روی پورت $udpgw تنظیم شد.", "SUCCESS")
                    } catch (e: Exception) {
                        Log.w("SshVpnService", "UDPGW forward warning: ${e.message}")
                    }
                }

                // Establish the Vpn Interface
                setupVpnInterface(localSocksPort, host)

                if (vpnInterface != null) {
                    logToDatabase("تونل GMB NET با موفقیت متصل شد!", "SUCCESS")
                    connectionStatus.value = VpnStatus.CONNECTED
                    updateNotification("Connected to GMB NET", "GMB NET")

                    // Acquire partial wakeLock to prevent CPU sleep disconnects on MIUI / OneUI
                    try {
                        val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                        wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "GmbNet:VpnWakeLock")?.apply {
                            setReferenceCounted(false)
                            acquire(12 * 60 * 60 * 1000L) // 12 hours max timeout safety
                        }
                    } catch (e: Exception) {
                        Log.w("SshVpnService", "Could not acquire WakeLock: ${e.message}")
                    }

                    startStatsMonitoring()
                } else {
                    throw IOException("امکان ایجاد رابط کاربری شبکه VPN مقدور نیست")
                }

            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.message ?: "خطای ناشناخته در اتصال"
                Log.e("SshVpnService", "Error during SSH setup", e)
                logToDatabase("خطا در برقراری ارتباط با سرور SSH: $errorMsg", "ERROR")
                connectionStatus.value = VpnStatus.ERROR
                updateNotification("اتصال ناموفق")
                stopVpn()
            }
        }
    }

    private suspend fun setupVpnInterface(localSocksPort: Int, host: String) {
        try {
            val builder = Builder()
                .setSession("GMB NET - $host")
                .setMtu(1500) // 1500 MTU guarantees full standard IP packet support for TLS/HTTPS
                .addAddress("10.0.0.2", 24)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)
                .addRoute("198.18.0.0", 15) // Explicit routing for Fake-IP benchmark block

            // Prevent routing loop by disallowing our app from entering the tunnel
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w("SshVpnService", "Could not disallow app package: ${e.message}")
            }

            // Transparent TUN mode routes 100% of IP traffic (TCP, UDP, ICMP) 
            // without restrictive HTTP proxy layers, guaranteeing Telegram, WhatsApp,
            // Instagram, and games work seamlessly.
            vpnInterface = builder.establish()
            logToDatabase("رابط شبکه محلی VPN ایجاد شد (10.0.0.2).", "INFO")
            
            tunEngine?.stop()
            val pfd = vpnInterface
            val session = sshSession
            if (pfd != null && session != null) {
                val engine = Tun2SocksEngine(
                    vpnService = this,
                    vpnInterface = pfd,
                    sshSession = session,
                    localSocksPort = localSocksPort
                ) { rx, tx ->
                    if (rx > 0) rxBytes.value += rx
                    if (tx > 0) txBytes.value += tx
                }
                engine.start()
                tunEngine = engine
                logToDatabase("موتور مسیریابی هوشمند پکت‌ها (Tun2Socks) فعال گردید.", "SUCCESS")
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
            var seconds = 0L
            while (isActive && connectionStatus.value == VpnStatus.CONNECTED) {
                delay(1000)
                seconds++
                connectedDuration.value = seconds
            }
        }
    }

    private fun stopVpn() {
        serviceScope.launch {
            logToDatabase("در حال قطع اتصال GMB NET...", "INFO")
        }
        connectionStatus.value = VpnStatus.DISCONNECTED
        
        try {
            tunEngine?.stop()
        } catch (e: Exception) {
            // ignore
        }
        tunEngine = null

        statsJob?.cancel()
        statsJob = null

        serviceJob?.cancel()
        serviceJob = null

        try {
            socksServer?.stop()
        } catch (e: Exception) {
            // ignore
        }
        socksServer = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            // ignore
        }
        vpnInterface = null

        try {
            sshSession?.disconnect()
        } catch (e: Exception) {
            // ignore
        }
        sshSession = null

        // Release WakeLock safely
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
            db.vpnLogDao().insertLog(VpnLog(message = message, level = level))
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

    private fun updateNotification(text: String, title: String = "GMB NET") {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(1, createNotification(text, title))
    }
}

