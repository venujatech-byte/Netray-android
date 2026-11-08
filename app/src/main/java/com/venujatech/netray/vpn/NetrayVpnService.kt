package com.venujatech.netray.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.venujatech.netray.R
import com.venujatech.netray.profiles.ProfileStore
import com.venujatech.netray.proxy.ProxyModeGuard
import com.venujatech.netray.whitelist.AppWhitelistRepository
import com.venujatech.netray.whitelist.WhitelistEnforcer
import com.venujatech.netray.whitelist.AppWhitelist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.venujatech.netray.xray.LibXrayEngine
import com.venujatech.netray.xray.TunnelMode
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors

class NetrayVpnService : VpnService() {
    private val binder = LocalBinder()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "NetrayVpn").apply { isDaemon = true } }
    private var tun: android.os.ParcelFileDescriptor? = null
    private var engine: LibXrayEngine? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var appliedWhitelist: AppWhitelist? = null

    inner class LocalBinder : Binder() { val service get() = this@NetrayVpnService }
    override fun onBind(intent: Intent?): IBinder? = if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else binder

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            AppWhitelistRepository.get(this@NetrayVpnService).whitelist.collect { current ->
                if (state.value is State.Connected && appliedWhitelist != null && current != appliedWhitelist) {
                    updateNotification("Whitelist changed — reconnect to apply")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) { stopTunnel(); return START_NOT_STICKY }
        val profileId = when (intent?.action) {
            ACTION_CONNECT -> intent.getStringExtra(EXTRA_PROFILE)
            null -> getSharedPreferences(STATE_PREFS, MODE_PRIVATE).getString(EXTRA_PROFILE, null)
            else -> null
        } ?: return START_NOT_STICKY
        val mode = when (intent?.getIntExtra(EXTRA_MODE, MODE_TUN)) {
            MODE_PROXY -> TunnelMode.PROXY
            else -> TunnelMode.TUN
        }
        if (state.value is State.Connected || state.value is State.Connecting || state.value is State.Stopping) return START_STICKY
        getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit().putString(EXTRA_PROFILE, profileId).putInt(EXTRA_MODE, when (mode) { TunnelMode.TUN -> MODE_TUN; TunnelMode.PROXY -> MODE_PROXY }).apply()
        android.util.Log.i("NetrayVpn", "Connection requested mode=$mode")
        state.value = State.Connecting
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification("Connecting"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification("Connecting"))
        }
        worker.execute { connect(profileId, mode) }
        return START_STICKY
    }

    private fun connect(profileId: String, mode: TunnelMode) {
        var unavailableWhitelistApps = emptyList<String>()
        try {
            ProxyModeGuard.assertNoVpnApi("NetrayVpnService.connect")
            ProxyModeGuard.leaveProxyMode()

            val profile = ProfileStore(this).load().first { it.id == profileId }

            when (mode) {
                TunnelMode.TUN -> {
                    val whitelist = AppWhitelistRepository.get(this).whitelist.value
                    appliedWhitelist = whitelist
                    val builder = Builder().setSession("Netray").setMtu(1500)
                        .addAddress("10.0.0.2", 30).addRoute("0.0.0.0", 0).addDnsServer("1.1.1.1")
                        .addAddress("fd00::2", 126).addRoute("::", 0).addDnsServer("2606:4700:4700::1111")
                    val missingApps = WhitelistEnforcer.excludeApps(builder, this, whitelist.appPackages)
                    unavailableWhitelistApps = missingApps
                    if (missingApps.isNotEmpty()) Log.w("NetrayVpn", "Skipped ${missingApps.size} uninstalled or unavailable whitelist app(s)")
                    val descriptor = builder.establish() ?: error("Android did not establish the VPN")
                    android.util.Log.i("NetrayVpn", "Android VPN interface established")
                    tun = descriptor
                    val xray = LibXrayEngine(this, this)
                    engine = xray
                    android.util.Log.i("NetrayVpn", "Starting Xray in TUN mode")
                    xray.start(profile.link, TunnelMode.TUN, descriptor.fd, whitelist = whitelist)
                }
                TunnelMode.PROXY -> {
                    // Defensive: Proxy mode should use NetrayProxyService, not this VpnService.
                    ProxyModeGuard.enterProxyMode()
                    android.util.Log.i("NetrayVpn", "Starting Xray in Proxy mode (no TUN)")
                    val xray = LibXrayEngine(this, null)
                    engine = xray
                    xray.start(profile.link, TunnelMode.PROXY)
                }
            }

            android.util.Log.i("NetrayVpn", "Xray started; checking tunnel connectivity")
            // A single probe host can reset or filter VPN traffic; accept any known HTTPS check.
            val probeErrors = mutableListOf<String>()
            val ready = listOf("https://1.1.1.1/cdn-cgi/trace", "https://www.google.com/generate_204").any { address ->
                val connection = (java.net.URL(address).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    useCaches = false
                }
                try {
                    connection.responseCode in 200..399
                } catch (e: java.io.IOException) {
                    probeErrors += (e.cause ?: e).javaClass.simpleName
                    false
                } finally { connection.disconnect() }
            }
            check(ready) { "Tunnel connectivity check failed (${probeErrors.distinct().joinToString(", ")})" }
            if (state.value is State.Stopping) { releaseTunnel(); return }
            android.util.Log.i("NetrayVpn", "Tunnel connectivity check passed")
            state.value = State.Connected
            connectedAt = android.os.SystemClock.elapsedRealtime()
            val changed = mode == TunnelMode.TUN && AppWhitelistRepository.get(this).whitelist.value != appliedWhitelist
            updateNotification(when {
                changed -> "Whitelist changed — reconnect to apply"
                unavailableWhitelistApps.isNotEmpty() -> "Some whitelist apps unavailable; review list"
                else -> "Connected"
            })
        } catch (e: Exception) {
            android.util.Log.e("NetrayVpn", "Connection failed: " + (e.cause ?: e).javaClass.simpleName)
            android.util.Log.e("NetrayVpn", com.venujatech.netray.ConnectionLogs.sanitize(e.cause?.message ?: e.message ?: "Unknown error"))
            releaseTunnel()
            if (state.value !is State.Stopping) {
                state.value = State.Error(e.cause?.message ?: e.message ?: "Could not start VPN")
                updateNotification("Connection failed")
            }
        }
    }

    private fun stopTunnel() {
        android.util.Log.i("NetrayVpn", "Disconnect requested; stopping Xray and closing VPN")
        state.value = State.Stopping
        worker.execute {
            releaseTunnel()
            getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit().remove(EXTRA_PROFILE).remove(EXTRA_MODE).apply()
            Handler(Looper.getMainLooper()).post {
                state.value = State.Disconnected
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun releaseTunnel() {
        runCatching { engine?.stop() }; engine = null
        runCatching { tun?.close() }; tun = null
    }

    override fun onRevoke() { stopTunnel(); super.onRevoke() }
    override fun onDestroy() { serviceScope.cancel(); worker.shutdownNow(); releaseTunnel(); appliedWhitelist = null; state.value = State.Disconnected; super.onDestroy() }

    private fun notification(status: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "VPN connection", NotificationManager.IMPORTANCE_LOW))
        val disconnect = Intent(this, NetrayVpnService::class.java).setAction(ACTION_DISCONNECT)
        val pending = android.app.PendingIntent.getService(this, 1, disconnect, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val openApp = Intent(this, com.venujatech.netray.MainActivity::class.java)
        val contentPending = android.app.PendingIntent.getActivity(this, 0, openApp, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_vpn_status)
            .setContentTitle("Netray").setContentText(status).setOngoing(true)
            .setContentIntent(contentPending)
            .addAction(0, "Disconnect", pending).build()
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
        const val ACTION_CONNECT = "com.venujatech.netray.CONNECT"
        const val ACTION_DISCONNECT = "com.venujatech.netray.DISCONNECT"
        const val EXTRA_PROFILE = "profile_id"
        const val EXTRA_MODE = "tunnel_mode"
        const val MODE_TUN = 0
        const val MODE_PROXY = 1
        private const val CHANNEL = "vpn"
        private const val NOTIFICATION_ID = 41
        private const val STATE_PREFS = "connection_state"
        val state = MutableStateFlow<State>(State.Disconnected)
        @Volatile var connectedAt: Long = 0
    }
}
