package com.venujatech.netray.vpn

import java.math.BigInteger
import java.net.InetAddress

object VpnRoutePolicy {
    val lanExclusions = listOf(
        "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16",
        "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4",
        "fc00::/7", "fe80::/10", "ff00::/8"
    )

    fun androidExcludeRoutes(ipv6Enabled: Boolean): List<String> = lanExclusions
        .filter { ipv6Enabled || ':' !in it }
        // Loopback is already local, and Android rejects it as an excluded VPN route.
        .filterNot { it == "127.0.0.0/8" }

    fun includedRoutes(bypassLan: Boolean, ipv6Enabled: Boolean = true): List<String> = buildList {
        if (bypassLan) addAll(complement(32, lanExclusions.filterNot { ':' in it }))
        else add("0.0.0.0/0")
        if (ipv6Enabled) {
            if (bypassLan) addAll(complement(128, lanExclusions.filter { ':' in it }))
            else add("::/0")
        }
    }

    fun contains(cidr: String, address: String): Boolean {
        val prefix = parse(cidr) ?: return false
        val candidate = runCatching { InetAddress.getByName(address).address }.getOrNull() ?: return false
        if (candidate.size * 8 != prefix.bits) return false
        return network(BigInteger(1, candidate), prefix.length, prefix.bits) == prefix.network
    }

    private data class Prefix(val bits: Int, val length: Int, val network: BigInteger)

    private fun complement(bits: Int, exclusions: List<String>): List<String> {
        val blocked = exclusions.mapNotNull(::parse).filter { it.bits == bits }
        val output = mutableListOf<String>()
        fun visit(value: BigInteger, length: Int) {
            val current = Prefix(bits, length, network(value, length, bits))
            if (blocked.any { covers(it, current) }) return
            val overlaps = blocked.any { covers(current, it) }
            if (!overlaps || length == bits) {
                output += "${format(current.network, bits)}/$length"
                return
            }
            val childLength = length + 1
            visit(current.network, childLength)
            visit(current.network.setBit(bits - childLength), childLength)
        }
        visit(BigInteger.ZERO, 0)
        return output
    }

    private fun covers(parent: Prefix, child: Prefix): Boolean =
        parent.bits == child.bits && parent.length <= child.length &&
            network(child.network, parent.length, parent.bits) == parent.network

    private fun parse(value: String): Prefix? = runCatching {
        val (address, prefixText) = value.split('/', limit = 2).let { it[0] to it[1] }
        val bytes = InetAddress.getByName(address).address
        val bits = bytes.size * 8
        val length = prefixText.toInt().takeIf { it in 0..bits } ?: return null
        Prefix(bits, length, network(BigInteger(1, bytes), length, bits))
    }.getOrNull()

    private fun network(value: BigInteger, length: Int, bits: Int): BigInteger =
        if (length == 0) BigInteger.ZERO else value.shiftRight(bits - length).shiftLeft(bits - length)

    private fun format(value: BigInteger, bits: Int): String {
        val size = bits / 8
        val raw = value.toByteArray()
        val bytes = ByteArray(size)
        val sourceStart = (raw.size - size).coerceAtLeast(0)
        val copySize = (raw.size - sourceStart).coerceAtMost(size)
        raw.copyInto(bytes, size - copySize, sourceStart, sourceStart + copySize)
        return requireNotNull(InetAddress.getByAddress(bytes).hostAddress).lowercase()
    }
}
