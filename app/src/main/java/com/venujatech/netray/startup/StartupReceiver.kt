package com.venujatech.netray.startup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.venujatech.netray.MainActivity
import com.venujatech.netray.R
import com.venujatech.netray.profiles.ProfileStore
import com.venujatech.netray.proxy.NetrayProxyService
import com.venujatech.netray.proxy.ProxyConfig
import com.venujatech.netray.vpn.NetrayVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleBoot(context.applicationContext)
            } catch (_: Exception) {
                showOpenNotification(context, "Open Netray to finish starting.")
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleBoot(context: Context) {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("run_at_startup", false)) return
        showOpenNotification(context, "Netray started after device boot.")
        if (!preferences.getBoolean("connect_at_startup", false)) {
            showOpenNotification(context, "Netray is ready. Tap to open the app.")
            return
        }

        val profiles = ProfileStore(context).load()
        val selectedId = preferences.getString("selected_profile_id", null)
            ?.takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.firstOrNull()?.id
        if (selectedId == null) {
            showOpenNotification(context, "Add a connection profile before enabling startup connect.")
            return
        }

        if (preferences.getInt("connection_mode", 0) == NetrayVpnService.MODE_PROXY) {
            val ports = ProxyConfig.loadPorts(preferences)
            ContextCompat.startForegroundService(
                context,
                Intent(context, NetrayProxyService::class.java)
                    .setAction(NetrayProxyService.ACTION_CONNECT)
                    .putExtra(NetrayProxyService.EXTRA_PROFILE, selectedId)
                    .putExtra(NetrayProxyService.EXTRA_SOCKS_PORT, ports[0])
                    .putExtra(NetrayProxyService.EXTRA_HTTP_PORT, ports[1])
            )
            return
        }

        if (VpnService.prepare(context) != null) {
            showOpenNotification(context, "Open Netray to grant VPN permission for startup connect.")
            return
        }
        ContextCompat.startForegroundService(
            context,
            Intent(context, NetrayVpnService::class.java)
                .setAction(NetrayVpnService.ACTION_CONNECT)
                .putExtra(NetrayVpnService.EXTRA_PROFILE, selectedId)
                .putExtra(NetrayVpnService.EXTRA_MODE, NetrayVpnService.MODE_TUN)
        )
    }

    private fun showOpenNotification(context: Context, message: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Startup", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_vpn_status)
                .setContentTitle("Netray")
                .setContentText(message)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    private companion object {
        const val CHANNEL = "startup"
        const val NOTIFICATION_ID = 43
    }
}
