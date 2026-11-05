package com.venujatech.netray.xray

import android.net.VpnService
import android.os.Build
import libXray.LibXray
import libXray.ProcessFinder
import com.venujatech.netray.profiles.NativeBackend
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Proxy

/** Keeps the unstable gomobile surface isolated from Android VPN lifecycle code. */
class LibXrayEngine(private val service: VpnService) {
    private val api = Class.forName("libXray.LibXray")
    private val controller = Class.forName("libXray.DialerController")
    private val callback = Proxy.newProxyInstance(controller.classLoader, arrayOf(controller)) { proxy, method, args ->
        when {
            method.name.equals("protectFd", true) -> service.protect((args?.firstOrNull() as Number).toInt())
            method.name == "toString" -> "NetrayDialerController"
            method.name == "hashCode" -> System.identityHashCode(proxy)
            method.name == "equals" -> proxy === args?.firstOrNull()
            else -> false
        }
    }

    fun start(link: String, tunFd: Int) {
        // libXray calls this handler during TUN traffic even without per-app routing.
        LibXray.registerProcessFinder(ProcessFinder { _, _, _, _, _ -> -1L }, Build.VERSION.SDK_INT.toLong())
        api.getMethod("registerDialerController", controller).invoke(null, callback)
        api.getMethod("setDNS", controller, String::class.java).invoke(null, callback, "1.1.1.1:53")
        val converted = invoke("convertShareLinksToXrayJson", JSONObject().put("text", link)).getJSONObject("data")
        val config = JSONObject(NativeBackend.buildTunConfig(converted.getJSONArray("outbounds").toString(), tunFd))
        require(!config.has("error")) { config.optString("error", "Could not prepare Xray config") }
        invoke("runXray", JSONObject().put("xrayJson", config.toString()))
    }

    fun stop() {
        runCatching { invoke("stopXray", JSONObject()) }
        runCatching { api.getMethod("resetDNS").invoke(null) }
        runCatching { api.getMethod("registerDialerController", controller).invoke(null, null) }
    }

    private fun invoke(method: String, payload: JSONObject): JSONObject {
        val request = JSONObject().put("apiVersion", 3).put("method", method).put("payload", payload)
        val response = JSONObject(api.getMethod("invoke", String::class.java).invoke(null, request.toString()) as String)
        check(response.optBoolean("success")) { response.optString("error", "Xray request failed") }
        return response
    }
}
