package com.venujatech.netray.whitelist

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppWhitelistTest {
    @Test
    fun urlAndWildcardNormalizeToLinuxXrayDomainMatcher() {
        val url = WhitelistRuleParser.parse(" HTTPS://Example.COM:8443/private/path?q=1 ")
        val wildcard = WhitelistRuleParser.parse("*.example.com")

        assertEquals("example.com", url.value)
        assertEquals("domain:example.com", url.matcher)
        assertEquals("*.example.com", wildcard.value)
        assertTrue(WhitelistRuleParser.overlaps(url, wildcard))
        assertEquals("2001:db8:0:0:0:0:0:1", WhitelistRuleParser.parse("https://[2001:db8::1]:443/path").value)
    }

    @Test
    fun cidrNormalizesNetworkAndDetectsOverlappingRanges() {
        val broad = WhitelistRuleParser.parse("10.2.3.4/8")
        val narrow = WhitelistRuleParser.parse("10.20.0.0/16")

        assertEquals("10.0.0.0/8", broad.value)
        assertTrue(WhitelistRuleParser.overlaps(broad, narrow))
        assertEquals("2001:db8:0:0:0:0:0:0/32", WhitelistRuleParser.parse("2001:db8::1/32").value)
    }

    @Test
    fun rejectsInvalidDomainIpAndPrefix() {
        assertThrows(IllegalArgumentException::class.java) { WhitelistRuleParser.parse("-bad.example") }
        assertThrows(IllegalArgumentException::class.java) { WhitelistRuleParser.parse("300.1.1.1") }
        assertThrows(IllegalArgumentException::class.java) { WhitelistRuleParser.parse("192.168.1.1/33") }
    }

    @Test
    fun repositoryPersistsRulesAndPackageNamesAndRejectsDuplicates() {
        val storage = MemoryWhitelistStorage()
        val repository = AppWhitelistRepository(storage)
        repository.addRule("https://example.com:443/a")
        assertThrows(IllegalArgumentException::class.java) { repository.addRule("*.example.com") }
        repository.setApps(listOf("com.example.browser", "com.example.browser"))

        val restored = AppWhitelistRepository(storage).whitelist.value
        assertEquals(listOf("domain:example.com"), restored.rules.map(WhitelistRule::matcher))
        assertEquals(listOf("com.example.browser"), restored.appPackages)
        assertFalse(restored.rules.first().isIp)
    }

    @Test
    fun xrayConfigRoutesWhitelistDirectAndKeepsProxyEndpointAheadOfIt() {
        val config = JSONObject("""
            {"inbounds":[{"protocol":"tun"}],"outbounds":[{"tag":"proxy","protocol":"vless"}],
             "routing":{"rules":[{"type":"field","inboundTag":["tun-in"],"outboundTag":"proxy"}]}}
        """.trimIndent())
        val whitelist = AppWhitelist(
            rules = listOf(WhitelistRule("example.com", "domain:example.com", false), WhitelistRule("10.0.0.0/8", "10.0.0.0/8", true))
        )

        WhitelistEnforcer.addDirectRouting(config, whitelist, "proxy.example.net")

        val rules = config.getJSONObject("routing").getJSONArray("rules")
        assertEquals("proxy", rules.getJSONObject(0).getString("outboundTag"))
        assertEquals("direct", rules.getJSONObject(1).getString("outboundTag"))
        assertEquals("domain:example.com", rules.getJSONObject(1).getJSONArray("domain").getString(0))
        assertEquals("direct", rules.getJSONObject(2).getString("outboundTag"))
        assertEquals("10.0.0.0/8", rules.getJSONObject(2).getJSONArray("ip").getString(0))
        assertEquals("proxy", rules.getJSONObject(3).getString("outboundTag"))
        assertTrue(config.getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing").getBoolean("enabled"))
        assertEquals("quic", config.getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing").getJSONArray("destOverride").getString(2))
    }

    private class MemoryWhitelistStorage : AppWhitelistStorage {
        private var value = AppWhitelist()
        override fun load(): AppWhitelist = value
        override fun save(value: AppWhitelist) { this.value = value }
    }
}
