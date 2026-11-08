package com.venujatech.netray.proxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.venujatech.netray.R
import com.venujatech.netray.MainActivity
import com.venujatech.netray.profiles.ProfileStore
import com.venujatech.netray.proxy.ProxyConfig.DEFAULT_HTTP_PORT
import com.venujatech.netray.proxy.ProxyConfig.DEFAULT_SOCKS_PORT
import com.venujatech.netray.proxy.ProxyConfig.MAX_PORT
import com.venujatech.netray.proxy.ProxyConfig.MIN_PORT
import com.venujatech.netray.xray.LibXrayEngine
import com.venujatech.netray.xray.TunnelMode
import com.venujatech.netray.whitelist.AppWhitelistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors

/**
 * Proxy-mode service. Extends [android.app.Service] (NOT [android.net.VpnService]).
 *
 * Runs Xray-core with local inbounds only (SOCKS5 + HTTP on 127.0.0.1). No TUN interface,
 * no VpnService consent, no VPN key icon. Apps must be individually configured to use the
 * local proxy (browser Wi-Fi proxy, Telegram SOCKS5, etc.).
 */
class NetrayProxyService : Service() {
    private val binder = LocalBinder()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "NetrayProxy").apply { isDaemon = true } }
    private var engine: LibXrayEngine? = null

    inner class LocalBinder : android.os.Binder() { val service get() = this@NetrayProxyService }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            stopProxy()
            return START_NOT_STICKY
        }
        val profileId = when (intent?.action) {
            ACTION_CONNECT -> intent.getStringExtra(EXTRA_PROFILE)
            null -> getSharedPreferences(STATE_PREFS, MODE_PRIVATE).getString(EXTRA_PROFILE, null)
            else -> null
        } ?: return START_NOT_STICKY
        val socksPort = intent?.getIntExtra(EXTRA_SOCKS_PORT, DEFAULT_SOCKS_PORT) ?: DEFAULT_SOCKS_PORT
        val httpPort = intent?.getIntExtra(EXTRA_HTTP_PORT, DEFAULT_HTTP_PORT) ?: DEFAULT_HTTP_PORT

        if (state.value is State.Connected || state.value is State.Connecting || state.value is State.Stopping) {
            return START_STICKY
        }

        getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
            .putString(EXTRA_PROFILE, profileId)
            .putInt(EXTRA_SOCKS_PORT, socksPort)
            .putInt(EXTRA_HTTP_PORT, httpPort)
            .apply()

        Log.i("NetrayProxy", "Proxy connection requested profile=$profileId socks=$socksPort http=$httpPort")
        state.value = State.Connecting

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification("Connecting"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification("Connecting"))
        }

        worker.execute { connect(profileId, socksPort, httpPort) }
        return START_STICKY
    }

    private fun connect(profileId: String, socksPort: Int, httpPort: Int) {
        try {
            // Validate ports before starting
            ProxyConfig.validatePort(socksPort)
            ProxyConfig.validatePort(httpPort)
            ProxyConfig.checkNoCollision(socksPort, httpPort)

            val profile = ProfileStore(this).load().first { it.id == profileId }

            Log.i("NetrayProxy", "Starting Xray in Proxy mode (SOCKS5 $socksPort, HTTP $httpPort)")
            val xray = LibXrayEngine(this@NetrayProxyService)
            engine = xray
            val whitelist = AppWhitelistRepository.get(this@NetrayProxyService).whitelist.value
            xray.start(profile.link, TunnelMode.PROXY, tunFd = -1, socksPort = socksPort, httpPort = httpPort, whitelist = whitelist)

            // Readiness check through the local HTTP proxy inbound (not direct HTTPS)
            val ready = httpProxyReady(httpPort, timeoutMs = 5000)
            check(ready) { "Proxy readiness check failed; local inbound may not be accepting connections" }

            if (state.value is State.Stopping) { releaseProxy(); return }
            Log.i("NetrayProxy", "Proxy connectivity check passed")
            state.value = State.Connected
            connectedAt = System.currentTimeMillis()
            updateNotification("Connected")
        } catch (e: Exception) {
            Log.e("NetrayProxy", "Proxy connection failed: " + (e.cause ?: e).javaClass.simpleName)
            Log.e("NetrayProxy", com.venujatech.netray.ConnectionLogs.sanitize(e.cause?.message ?: e.message ?: "Unknown error"))
            releaseProxy()
            if (state.value !is State.Stopping) {
                state.value = State.Error(e.cause?.message ?: e.message ?: "Could not start proxy")
                updateNotification("Connection failed")
            }
        }
    }

    /**
     * Readiness check through the local HTTP proxy inbound. Routes an external HTTP request
     * through the local proxy (127.0.0.1:$httpPort) to verify Xray is accepting and forwarding
     * connections. This tests the real proxied path, not a loopback-to-self.
     */
    private fun httpProxyReady(httpPort: Int, timeoutMs: Int): Boolean {
        val proxy = java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", httpPort))
        return runCatching {
            val connection = java.net.URL("https://1.1.1.1/cdn-cgi/trace").openConnection(proxy) as java.net.HttpURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.useCaches = false
            connection.setRequestProperty("Host", "1.1.1.1")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Connection", "close")
            val code = connection.responseCode
            Log.i("NetrayProxy", "Readiness check HTTP response: $code")
            code in 200..399
        }.onFailure { e ->
            Log.e("NetrayProxy", "Readiness check exception: " + (e.cause ?: e).message)
        }.getOrDefault(false)
    }

    private fun stopProxy() {
        Log.i("NetrayProxy", "Disconnect requested; stopping Xray and releasing local ports")
        state.value = State.Stopping
        worker.execute {
            releaseProxy()
            getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit().remove(EXTRA_PROFILE).remove(EXTRA_SOCKS_PORT).remove(EXTRA_HTTP_PORT).apply()
            Handler(Looper.getMainLooper()).post {
                state.value = State.Disconnected
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun releaseProxy() {
        runCatching { engine?.stop() }; engine = null
        ProxyModeGuard.leaveProxyMode()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        releaseProxy()
        state.value = State.Disconnected
        super.onDestroy()
    }

    private fun notification(status: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Proxy connection", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val disconnect = Intent(this, NetrayProxyService::class.java).setAction(ACTION_DISCONNECT)
        val pending = PendingIntent.getService(this, 1, disconnect, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openApp = Intent(this, MainActivity::class.java)
        val contentPending = PendingIntent.getActivity(this, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_vpn_status)
            .setContentTitle("Netray Proxy")
            .setContentText(status)
            .setOngoing(true)
            .setContentIntent(contentPending)
            .addAction(0, "Disconnect", pending)
            .build()
    }

    private fun updateNotification(status: String) = getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(status))

    sealed interface State {
        data object Disconnected : State
        data object Connecting : State
        data object Connected : State
        data object Stopping : State
        data class Error(val message: String) : State
    }

    companion object {
        const val ACTION_CONNECT = "com.venujatech.netray.PROXY_CONNECT"
        const val ACTION_DISCONNECT = "com.venujatech.netray.PROXY_DISCONNECT"
        const val EXTRA_PROFILE = "profile_id"
        const val EXTRA_SOCKS_PORT = "socks_port"
        const val EXTRA_HTTP_PORT = "http_port"
        private const val CHANNEL = "proxy"
        private const val NOTIFICATION_ID = 42
        private const val STATE_PREFS = "proxy_state"
        val state = MutableStateFlow<State>(State.Disconnected)
        @Volatile var connectedAt: Long = 0
    }
}

/**
 * Proxy port configuration, validation, and collision detection.
 */
object ProxyConfig {
    const val DEFAULT_SOCKS_PORT = 10808
    const val DEFAULT_HTTP_PORT = 10809
    const val MIN_PORT = 1024
    const val MAX_PORT = 65535

    fun validatePort(port: Int) {
        check(port in MIN_PORT..MAX_PORT) { "Port $port is out of valid range ($MIN_PORT-$MAX_PORT)" }
    }

    fun checkNoCollision(socksPort: Int, httpPort: Int) {
        check(socksPort != httpPort) { "SOCKS5 port ($socksPort) and HTTP port ($httpPort) must not collide" }
    }

    fun loadPorts(prefs: SharedPreferences): IntArray {
        val socks = prefs.getInt("proxy_socks_port", DEFAULT_SOCKS_PORT).coerceIn(MIN_PORT, MAX_PORT)
        val http = prefs.getInt("proxy_http_port", DEFAULT_HTTP_PORT).coerceIn(MIN_PORT, MAX_PORT)
        return intArrayOf(socks, http)
    }

    fun savePorts(prefs: SharedPreferences, socksPort: Int, httpPort: Int) {
        prefs.edit().putInt("proxy_socks_port", socksPort).putInt("proxy_http_port", httpPort).apply()
    }
}
