package com.venujatech.netray.whitelist

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.IDN

data class WhitelistRule(val value: String, val matcher: String, val isIp: Boolean)

data class AppWhitelist(
    val rules: List<WhitelistRule> = emptyList(),
    val appPackages: List<String> = emptyList(),
    val loadError: String? = null
)

object WhitelistRuleParser {
    fun parse(raw: String): WhitelistRule {
        val input = raw.trim().trim('"', '\'', '`').trim()
        require(input.isNotEmpty()) { "Enter a domain, URL, IP address, or CIDR range." }
        require(input.length <= 2_048) { "Whitelist entries must be 2,048 characters or shorter." }

        if ('/' in input && "://" !in input) {
            val parts = input.split('/')
            val addressPart = parts.first()
            val looksLikeIp = addressPart.count { it == ':' } > 1 || addressPart.matches(Regex("[0-9.]+"))
            if (looksLikeIp) {
                require(parts.size == 2) { "Enter a valid IP/CIDR range." }
                val address = parseIp(addressPart) ?: throw IllegalArgumentException("CIDR must start with a valid IPv4 or IPv6 address.")
                val prefix = parts[1].toIntOrNull()
                    ?: throw IllegalArgumentException("CIDR prefix must be a number.")
                val bits = address.size * 8
                require(prefix in 0..bits) { "CIDR prefix must be between 0 and $bits." }
                val network = address.copyOf()
                for (index in network.indices) {
                    val remaining = prefix - index * 8
                    val mask = when {
                        remaining >= 8 -> 0xff
                        remaining <= 0 -> 0
                        else -> (0xff shl (8 - remaining)) and 0xff
                    }
                    network[index] = (network[index].toInt() and 0xff and mask).toByte()
                }
                val canonical = "${formatIp(network)}/$prefix"
                return WhitelistRule(canonical, canonical, true)
            }
        }

        parseIp(input)?.let {
            val canonical = formatIp(it)
            return WhitelistRule(canonical, canonical, true)
        }

        val schemeAt = input.indexOf("://")
        val wildcard = if (schemeAt >= 0) {
            val authority = input.substring(schemeAt + 3)
            authority.startsWith("*.")
        } else input.startsWith("*.")
        val urlInput = if (schemeAt >= 0) {
            require(input.startsWith("http://", true) || input.startsWith("https://", true)) {
                "Only HTTP and HTTPS URLs are supported."
            }
            if (wildcard) input.replace("://*.", "://", ignoreCase = true) else input
        } else {
            "https://${input.removePrefix("*.")}"
        }
        val uri = runCatching { URI(urlInput) }.getOrNull()
            ?: throw IllegalArgumentException("Enter a valid domain or URL.")
        require(uri.rawUserInfo == null) { "URLs with usernames or passwords are not supported." }
        val rawHost = uri.host?.trim('[', ']')?.trimEnd('.')
            ?: throw IllegalArgumentException("Enter a valid domain or URL host.")
        parseIp(rawHost)?.let {
            val canonical = formatIp(it)
            return WhitelistRule(canonical, canonical, true)
        }
        val host = rawHost.let {
            runCatching { IDN.toASCII(it, IDN.USE_STD3_ASCII_RULES).lowercase() }.getOrNull()
        }?.takeIf(::validDomain)
            ?: throw IllegalArgumentException("Enter a valid domain or URL host.")
        val ip = parseIp(host)
        if (ip != null) {
            val canonical = formatIp(ip)
            return WhitelistRule(canonical, canonical, true)
        }
        return WhitelistRule(if (wildcard) "*.$host" else host, "domain:$host", false)
    }

    fun overlaps(first: WhitelistRule, second: WhitelistRule): Boolean {
        if (first.matcher == second.matcher) return true
        if (!first.isIp && !second.isIp) {
            val firstHost = first.matcher.removePrefix("domain:")
            val secondHost = second.matcher.removePrefix("domain:")
            return firstHost.endsWith(".$secondHost") || secondHost.endsWith(".$firstHost")
        }
        if (first.isIp && second.isIp) {
            val firstRange = parseRange(first.matcher) ?: return false
            val secondRange = parseRange(second.matcher) ?: return false
            if (firstRange.address.size != secondRange.address.size) return false
            val commonPrefix = minOf(firstRange.prefix, secondRange.prefix)
            return samePrefix(firstRange.address, secondRange.address, commonPrefix)
        }
        return false
    }

    private data class IpRange(val address: ByteArray, val prefix: Int)

    private fun parseRange(value: String): IpRange? {
        val parts = value.split('/')
        val address = parseIp(parts[0]) ?: return null
        val bits = address.size * 8
        val prefix = parts.getOrNull(1)?.toIntOrNull() ?: bits
        return IpRange(address, prefix)
    }

    private fun samePrefix(first: ByteArray, second: ByteArray, prefix: Int): Boolean {
        val fullBytes = prefix / 8
        for (index in 0 until fullBytes) if (first[index] != second[index]) return false
        val remaining = prefix % 8
        if (remaining == 0) return true
        val mask = (0xff shl (8 - remaining)) and 0xff
        return (first[fullBytes].toInt() and mask) == (second[fullBytes].toInt() and mask)
    }

    private fun parseIp(raw: String): ByteArray? {
        val value = raw.removePrefix("[").removeSuffix("]")
        val ipv4Parts = value.split('.')
        if (ipv4Parts.size == 4 && ipv4Parts.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
            val octets = ipv4Parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return null }
            return ByteArray(4) { octets[it].toByte() }
        }
        if (!value.contains(':') || !value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' }) return null
        return runCatching { InetAddress.getByName(value).takeIf { it is Inet6Address }?.address }.getOrNull()
    }

    private fun formatIp(address: ByteArray): String = requireNotNull(InetAddress.getByAddress(address).hostAddress).lowercase()

    private fun validDomain(host: String): Boolean = host.length <= 253 && host.split('.').all { label ->
        label.isNotEmpty() && label.length <= 63 && label.first().isLetterOrDigit() &&
            label.last().isLetterOrDigit() && label.all { it.isLetterOrDigit() || it == '-' }
    }
}
