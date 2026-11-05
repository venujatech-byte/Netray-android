package com.venujatech.netray.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.venujatech.netray.R
import com.venujatech.netray.profiles.ProfileStore
import com.venujatech.netray.xray.LibXrayEngine
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors

class NetrayVpnService : VpnService() {
    private val binder = LocalBinder()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "NetrayVpn").apply { isDaemon = true } }
    private var tun: android.os.ParcelFileDescriptor? = null
    private var engine: LibXrayEngine? = null

    inner class LocalBinder : Binder() { val service get() = this@NetrayVpnService }
    override fun onBind(intent: Intent?): IBinder? = if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) { stopTunnel(); return START_NOT_STICKY }
        val profileId = when (intent?.action) {
            ACTION_CONNECT -> intent.getStringExtra(EXTRA_PROFILE)
            null -> getSharedPreferences(STATE_PREFS, MODE_PRIVATE).getString(EXTRA_PROFILE, null)
            else -> null
        } ?: return START_NOT_STICKY
        if (state.value is State.Connected || state.value is State.Connecting || state.value is State.Stopping) return START_STICKY
        getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit().putString(EXTRA_PROFILE, profileId).apply()
        android.util.Log.i("NetrayVpn", "Connection requested")
        state.value = State.Connecting
        startForeground(NOTIFICATION_ID, notification("Connecting"))
        worker.execute { connect(profileId) }
        return START_STICKY
    }

    private fun connect(profileId: String) {
        try {
            val profile = ProfileStore(this).load().first { it.id == profileId }
            val descriptor = Builder().setSession("Netray").setMtu(1500)
                .addAddress("10.0.0.2", 30).addRoute("0.0.0.0", 0).addDnsServer("1.1.1.1")
                .addAddress("fd00::2", 126).addRoute("::", 0).addDnsServer("2606:4700:4700::1111")
                .establish() ?: error("Android did not establish the VPN")
            android.util.Log.i("NetrayVpn", "Android VPN interface established")
            tun = descriptor
            val xray = LibXrayEngine(this)
            engine = xray
            android.util.Log.i("NetrayVpn", "Starting Xray")
            xray.start(profile.link, descriptor.fd)
            android.util.Log.i("NetrayVpn", "Xray started; checking tunnel connectivity")
            val connection = (java.net.URL("https://1.1.1.1/cdn-cgi/trace").openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                useCaches = false
            }
            // An IP endpoint avoids Android DNS lookup delays outside connectTimeout.
            val ready = try { connection.responseCode in 200..399 } finally { connection.disconnect() }
            check(ready) { "Tunnel started but the connectivity check failed" }
            if (state.value is State.Stopping) { releaseTunnel(); return }
            android.util.Log.i("NetrayVpn", "Tunnel connectivity check passed")
            state.value = State.Connected
            connectedAt = android.os.SystemClock.elapsedRealtime()
            updateNotification("Connected")
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
            getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit().remove(EXTRA_PROFILE).apply()
            releaseTunnel()
            Handler(Looper.getMainLooper()).post {
                state.value = State.Disconnected
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun releaseTunnel() {
        engine?.stop(); engine = null
        runCatching { tun?.close() }; tun = null
    }

    override fun onRevoke() { stopTunnel(); super.onRevoke() }
    override fun onDestroy() { worker.shutdownNow(); releaseTunnel(); state.value = State.Disconnected; super.onDestroy() }

    private fun notification(status: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "VPN connection", NotificationManager.IMPORTANCE_LOW))
        val disconnect = Intent(this, NetrayVpnService::class.java).setAction(ACTION_DISCONNECT)
        val pending = android.app.PendingIntent.getService(this, 1, disconnect, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_vpn_status)
            .setContentTitle("Netray").setContentText(status).setOngoing(true)
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
        private const val CHANNEL = "vpn"
        private const val NOTIFICATION_ID = 41
        private const val STATE_PREFS = "connection_state"
        val state = MutableStateFlow<State>(State.Disconnected)
        @Volatile var connectedAt: Long = 0
    }
}
