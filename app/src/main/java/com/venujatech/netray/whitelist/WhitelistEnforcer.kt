package com.venujatech.netray.whitelist

import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

object WhitelistEnforcer {
    fun excludeApps(builder: VpnService.Builder, context: Context, packages: List<String>): List<String> =
        buildList {
            for (packageName in packages.distinct()) {
                if (packageName == context.packageName) continue
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (_: PackageManager.NameNotFoundException) {
                    add(packageName)
                }
            }
        }

    fun addDirectRouting(config: JSONObject, whitelist: AppWhitelist, endpoint: String?) {
        if (whitelist.rules.isEmpty()) return
        val outbounds = config.optJSONArray("outbounds") ?: JSONArray().also { config.put("outbounds", it) }
        if ((0 until outbounds.length()).none { outbounds.optJSONObject(it)?.optString("tag") == "direct" }) {
            outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        }
        val primaryTag = (0 until outbounds.length())
            .mapNotNull { outbounds.optJSONObject(it)?.optString("tag") }
            .firstOrNull { it.isNotBlank() && it != "direct" && it != "blocked" }

        val routing = config.optJSONObject("routing") ?: JSONObject().also { config.put("routing", it) }
        val previousRules = routing.optJSONArray("rules") ?: JSONArray()
        val rules = JSONArray()
        (endpointValues(outbounds) + listOfNotNull(endpoint)).distinct().forEach { value ->
            endpointRule(value, primaryTag)?.let { rules.put(it) }
        }

        val domainMatchers = whitelist.rules.filterNot(WhitelistRule::isIp)
            .map(WhitelistRule::matcher).distinct()
        val ipMatchers = whitelist.rules.filter(WhitelistRule::isIp)
            .map(WhitelistRule::matcher).distinct()
        if (domainMatchers.isNotEmpty()) {
            rules.put(JSONObject().put("type", "field").put("domain", JSONArray(domainMatchers)).put("outboundTag", "direct"))
            routing.put("domainStrategy", "IPIfNonMatch")
        }
        if (ipMatchers.isNotEmpty()) {
            rules.put(JSONObject().put("type", "field").put("ip", JSONArray(ipMatchers)).put("outboundTag", "direct"))
        }
        for (index in 0 until previousRules.length()) rules.put(previousRules.get(index))
        routing.put("rules", rules)

        val inbounds = config.optJSONArray("inbounds") ?: return
        for (index in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(index) ?: continue
            if (inbound.optString("protocol") == "tun") {
                inbound.put("sniffing", JSONObject()
                    .put("enabled", true)
                    .put("destOverride", JSONArray(listOf("http", "tls", "quic")))
                    .put("routeOnly", true))
            } else {
                val sniffing = inbound.optJSONObject("sniffing") ?: continue
                val protocols = sniffing.optJSONArray("destOverride") ?: JSONArray()
                if ((0 until protocols.length()).none { protocols.optString(it) == "quic" }) protocols.put("quic")
                sniffing.put("destOverride", protocols)
            }
        }
    }

    private fun endpointValues(outbounds: JSONArray): List<String> = buildList {
        for (index in 0 until outbounds.length()) {
            val settings = outbounds.optJSONObject(index)?.optJSONObject("settings") ?: continue
            settings.optString("address").takeIf(String::isNotBlank)?.let(::add)
            settings.optString("server").takeIf(String::isNotBlank)?.let(::add)
            for (key in listOf("vnext", "servers", "peers")) {
                val rows = settings.optJSONArray(key) ?: continue
                for (rowIndex in 0 until rows.length()) {
                    val row = rows.optJSONObject(rowIndex) ?: continue
                    row.optString("address").takeIf(String::isNotBlank)?.let(::add)
                    row.optString("endpoint").takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }
    }

    private fun endpointRule(endpoint: String, outboundTag: String?): JSONObject? {
        if (outboundTag.isNullOrBlank()) return null
        val raw = endpoint.trim()
        val host = when {
            raw.startsWith("[") -> raw.substringAfter('[').substringBefore(']')
            raw.count { it == ':' } > 1 -> raw
            else -> runCatching { URI("https://$raw").host }.getOrNull()
                ?: raw.substringBefore(':')
        }.trim('[', ']')
        if (host.isBlank()) return null
        val normalizedHost = host.lowercase()
        val numericIp = normalizedHost.contains(':') || normalizedHost.matches(Regex("[0-9.]+"))
        val matcher = if (numericIp) "ip" else "domain"
        val value = if (numericIp) normalizedHost else "full:$normalizedHost"
        return JSONObject().put("type", "field").put(matcher, JSONArray().put(value)).put("outboundTag", outboundTag)
    }
}
