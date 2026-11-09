package com.venujatech.netray.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsServerConfigTest {
    @Test
    fun normalizesIpv4AndIpv6AndRejectsHostnamesAndPorts() {
        assertEquals("1.0.0.1", DnsServerConfig.normalize(" 001.000.000.001 "))
        assertEquals("2001:4860:4860:0:0:0:0:8888", DnsServerConfig.normalize("2001:4860:4860::8888"))
        assertNull(DnsServerConfig.normalize("dns.example.com"))
        assertNull(DnsServerConfig.normalize("1.1.1.1:53"))
    }

    @Test
    fun formatsIpv6EndpointForLibXray() {
        assertEquals("[2001:4860:4860::8888]:53", DnsServerConfig.endpoint("2001:4860:4860::8888"))
    }

    @Test
    fun disabledIpv6UsesOnlyIpv4DnsAndFallsBackWhenNeeded() {
        assertEquals(listOf("1.1.1.1"), DnsServerConfig.activeServers("2001:4860:4860::8888", "1.1.1.1", false))
        assertEquals(
            listOf(DnsServerConfig.DEFAULT_PRIMARY, DnsServerConfig.DEFAULT_SECONDARY),
            DnsServerConfig.activeServers("2001:4860:4860::8888", "2606:4700:4700::1001", false)
        )
    }
}
