package com.venujatech.netray.vpn

import java.net.Inet6Address
import java.net.InetAddress

object DnsServerConfig {
    const val DEFAULT_PRIMARY = "1.1.1.1"
    const val DEFAULT_SECONDARY = "1.0.0.1"

    fun normalize(value: String): String? {
        val input = value.trim()
        val parts = input.split('.')
        if (parts.size == 4 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
            val octets = parts.map { it.toIntOrNull()?.takeIf { octet -> octet in 0..255 } ?: return null }
            return octets.joinToString(".")
        }
        if (':' !in input || !input.matches(Regex("[0-9a-fA-F:.]+"))) return null
        val address = runCatching { InetAddress.getByName(input) }.getOrNull() as? Inet6Address ?: return null
        return requireNotNull(address.hostAddress).substringBefore('%').lowercase()
    }

    fun endpoint(address: String): String = if (':' in address) "[$address]:53" else "$address:53"

    fun activeServers(primary: String, secondary: String, ipv6Enabled: Boolean): List<String> {
        val servers = listOf(primary, secondary).mapNotNull(::normalize).distinct()
            .filter { ipv6Enabled || ':' !in it }
        return servers.ifEmpty { listOf(DEFAULT_PRIMARY, DEFAULT_SECONDARY) }
    }
}
