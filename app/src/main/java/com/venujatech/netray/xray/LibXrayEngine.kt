package com.venujatech.netray.xray

import android.content.Context
import android.net.Uri
import android.net.VpnService
import android.os.Build
import libXray.DialerController
import libXray.LibXray
import libXray.ProcessFinder
import com.venujatech.netray.profiles.NativeBackend
import com.venujatech.netray.proxy.ProxyModeGuard
import com.venujatech.netray.whitelist.AppWhitelist
import com.venujatech.netray.whitelist.AppWhitelistRepository
import com.venujatech.netray.whitelist.WhitelistEnforcer
import org.json.JSONObject

enum class TunnelMode { TUN, PROXY }

/**
 * Xray engine wrapper. Supports both TUN mode (requires VpnService for fd passing and
 * socket protection) and Proxy mode (local inbounds only, no VpnService needed).
 *
 * In Proxy mode, [service] is null and [ProxyModeGuard.enterProxyMode] must be called
 * before [start] to ensure no VPN API is accidentally reached.
 */
class LibXrayEngine(private val context: Context, private val service: VpnService? = null) {

    fun start(
        link: String,
        mode: TunnelMode,
        tunFd: Int = -1,
        socksPort: Int = 10808,
        httpPort: Int = 10809,
        whitelist: AppWhitelist = AppWhitelistRepository.get(context).whitelist.value
    ) {
        if (mode == TunnelMode.PROXY) {
            ProxyModeGuard.enterProxyMode()
        } else {
            ProxyModeGuard.leaveProxyMode()
        }

        val dialerService = if (mode == TunnelMode.TUN) service else null
        VpnDialerController.activeService = dialerService
        ensureDialerRegistered()
        LibXray.registerProcessFinder(ProcessFinder { _, _, _, _, _ -> -1L }, Build.VERSION.SDK_INT.toLong())
        LibXray.setDNS(VpnDialerController, "1.1.1.1:53")
        val converted = invoke("convertShareLinksToXrayJson", JSONObject().put("text", link)).getJSONObject("data")
        val outbounds = converted.getJSONArray("outbounds")
        val allowInsecure = context.getSharedPreferences("settings", 0).getBoolean("allow_insecure_tls", false)
        val uri = Uri.parse(link)
        val host = uri.host.orEmpty()
        for (index in 0 until outbounds.length()) {
            val stream = outbounds.getJSONObject(index).optJSONObject("streamSettings") ?: continue
            if (stream.optString("security") != "tls") continue
            val tls = stream.optJSONObject("tlsSettings") ?: JSONObject().also { stream.put("tlsSettings", it) }
            val sni = tls.optString("serverName", host)
            val verifyName = uri.getQueryParameter("vcn") ?: uri.getQueryParameter("verifyPeerCertByName")
            val pin = uri.getQueryParameter("pcs") ?: uri.getQueryParameter("pinnedPeerCertSha256")
            if (!verifyName.isNullOrBlank()) tls.put("verifyPeerCertByName", verifyName)
            else if (!tls.has("verifyPeerCertByName") && host.isNotBlank() && sni != host &&
                !host.contains(':') && !host.matches(Regex("[0-9.]+"))) {
                // Match desktop: preserve SNI while verifying against the server's DNS name.
                tls.put("verifyPeerCertByName", host)
            }
            if (!pin.isNullOrBlank()) tls.put("pinnedPeerCertSha256", pin)
            tls.put("allowInsecure", allowInsecure)
        }
        val config = when (mode) {
            TunnelMode.TUN -> {
                require(service != null) { "TUN mode requires VpnService" }
                JSONObject(NativeBackend.buildTunConfig(outbounds.toString(), tunFd))
            }
            TunnelMode.PROXY -> {
                JSONObject(NativeBackend.buildProxyConfig(outbounds.toString(), socksPort, httpPort))
            }
        }
        require(!config.has("error")) { config.optString("error", "Could not prepare Xray config") }
        WhitelistEnforcer.addDirectRouting(config, whitelist, host)
        invoke("runXray", JSONObject().put("xrayJson", config.toString()))
    }

    fun stop() {
        runCatching { invoke("stopXray", JSONObject()) }
        runCatching { LibXray.resetDNS() }
        VpnDialerController.activeService = null
        ProxyModeGuard.leaveProxyMode()
    }

    private fun invoke(method: String, payload: JSONObject): JSONObject {
        val request = JSONObject().put("apiVersion", 3).put("method", method).put("payload", payload)
        val response = JSONObject(LibXray.invoke(request.toString()))
        check(response.optBoolean("success")) { response.optString("error", "Xray request failed") }
        return response
    }

    private object VpnDialerController : DialerController {
        @Volatile var activeService: VpnService? = null

        override fun protectFd(fd: Long): Boolean {
            val s = activeService ?: return true
            return s.protect(fd.toInt())
        }
    }

    companion object {
        private var registered = false
        private val lock = Any()

        private fun ensureDialerRegistered() {
            synchronized(lock) {
                if (!registered) {
                    LibXray.registerDialerController(VpnDialerController)
                    registered = true
                }
            }
        }
    }
}
