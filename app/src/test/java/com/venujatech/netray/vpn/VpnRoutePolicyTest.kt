package com.venujatech.netray.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnRoutePolicyTest {
    @Test
    fun normalModeKeepsDefaultRoutes() {
        assertEquals(listOf("0.0.0.0/0", "::/0"), VpnRoutePolicy.includedRoutes(false))
    }

    @Test
    fun disablingIpv6OmitsIpv6Routes() {
        val routes = VpnRoutePolicy.includedRoutes(bypassLan = false, ipv6Enabled = false)
        assertEquals(listOf("0.0.0.0/0"), routes)
        assertTrue(routes.none { ':' in it })
    }

    @Test
    fun disablingIpv6WithLanBypassKeepsOnlyIpv4Routes() {
        val routes = VpnRoutePolicy.includedRoutes(bypassLan = true, ipv6Enabled = false)
        assertTrue(routes.isNotEmpty())
        assertTrue(routes.none { ':' in it })
        assertFalse(routes.any { VpnRoutePolicy.contains(it, "192.168.1.1") })
        assertTrue(routes.any { VpnRoutePolicy.contains(it, "1.1.1.1") })
    }

    @Test
    fun androidExcludeRoutesOmitLoopbackAndRespectIpv6Setting() {
        val withIpv6 = VpnRoutePolicy.androidExcludeRoutes(ipv6Enabled = true)
        assertFalse(withIpv6.contains("127.0.0.0/8"))
        assertTrue(withIpv6.any { ':' in it })
        assertTrue(VpnRoutePolicy.androidExcludeRoutes(ipv6Enabled = false).none { ':' in it })
    }

    @Test
    fun lanBypassExcludesPrivateAndLocalAddressesButKeepsPublicAddresses() {
        val routes = VpnRoutePolicy.includedRoutes(true)
        for (address in listOf("10.2.3.4", "172.20.1.2", "192.168.1.1", "169.254.10.4", "fd12::1", "fe80::1", "ff02::fb")) {
            assertFalse("$address should bypass the VPN", routes.any { VpnRoutePolicy.contains(it, address) })
        }
        for (address in listOf("1.1.1.1", "8.8.8.8", "2001:4860:4860::8888")) {
            assertTrue("$address should remain routed through the VPN", routes.any { VpnRoutePolicy.contains(it, address) })
        }
    }
}
