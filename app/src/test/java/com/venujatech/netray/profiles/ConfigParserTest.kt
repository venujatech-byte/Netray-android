package com.venujatech.netray.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigParserTest {
    @Test
    fun allProtocolParsersRoundTripThroughCanonicalSerialization() {
        ProtocolKind.entries.forEach { protocol ->
            val draft = validDraft(protocol)
            val serialized = ConfigParserRouter.serialize(draft)
            val parsed = ConfigParserRouter.parse(serialized)
            val serializedAgain = ConfigParserRouter.serialize(parsed)
            assertEquals("Protocol ${protocol.key}", protocol, parsed.protocol)
            assertEquals("Protocol ${protocol.key}", serialized, serializedAgain)
        }
    }

    @Test
    fun vlessFieldsWithReservedCharactersRoundTrip() {
        val draft = validDraft(ProtocolKind.VLESS).withAll(mapOf(
            ConfigFieldKeys.REMARKS to "QA node #1",
            ConfigFieldKeys.PATH to "/edge path?x=1",
            ConfigFieldKeys.SNI to "cdn.example.test",
            ConfigFieldKeys.TLS to "reality",
            ConfigFieldKeys.PUBLIC_KEY to "synthetic+public/key==",
            ConfigFieldKeys.SHORT_ID to "abcd1234"
        ))
        val parsed = ConfigParserRouter.parse(ConfigParserRouter.serialize(draft))
        assertEquals("QA node #1", parsed[ConfigFieldKeys.REMARKS])
        assertEquals("/edge path?x=1", parsed[ConfigFieldKeys.PATH])
        assertEquals("cdn.example.test", parsed[ConfigFieldKeys.SNI])
        assertEquals("reality", parsed[ConfigFieldKeys.TLS])
        assertEquals("synthetic+public/key==", parsed[ConfigFieldKeys.PUBLIC_KEY])
        assertEquals("abcd1234", parsed[ConfigFieldKeys.SHORT_ID])
    }

    @Test
    fun vmessAcceptsUrlSafeBase64WithoutPadding() {
        val valid = validDraft(ProtocolKind.VMESS)
        val uri = ConfigParserRouter.serialize(valid)
        val payload = uri.substringAfter("vmess://")
        val unpaddedUrlSafe = payload.replace('+', '-').replace('/', '_').trimEnd('=')
        val parsed = ConfigParserRouter.parse("vmess://$unpaddedUrlSafe")
        assertEquals(ProtocolKind.VMESS, parsed.protocol)
        assertEquals(valid[ConfigFieldKeys.ADDRESS], parsed[ConfigFieldKeys.ADDRESS])
        assertEquals(valid[ConfigFieldKeys.UUID], parsed[ConfigFieldKeys.UUID])
    }

    @Test
    fun importParserHandlesMultilineAndJsonArrayAndCountsFailures() {
        val good = ConfigParserRouter.serialize(validDraft(ProtocolKind.VLESS))
        val batch = ConfigParserRouter.parseMany("$good\nnot-a-config\n${ConfigParserRouter.serialize(validDraft(ProtocolKind.SOCKS))}")
        assertEquals(2, batch.configs.size)
        assertEquals(1, batch.skipped)

        val jsonBatch = ConfigParserRouter.parseMany("[{\"outbounds\":[{\"protocol\":\"freedom\"}]}, \"$good\"]")
        assertEquals(2, jsonBatch.configs.size)
        assertEquals(ProtocolKind.RAW_JSON, jsonBatch.configs.first().protocol)
        assertEquals(ProtocolKind.VLESS, jsonBatch.configs.last().protocol)
    }

    @Test
    fun duplicateKeyUsesProtocolEndpointAndCredential() {
        val original = Profile.fromDraft(validDraft(ProtocolKind.VLESS))
        val sameEndpointAndId = Profile.fromDraft(validDraft(ProtocolKind.VLESS).with(ConfigFieldKeys.REMARKS, "different remark"))
        val otherId = Profile.fromDraft(validDraft(ProtocolKind.VLESS).with(ConfigFieldKeys.UUID, "00000000-0000-4000-8000-000000000002"))
        assertEquals(original.duplicateKey, sameEndpointAndId.duplicateKey)
        assertFalse(original.duplicateKey == otherId.duplicateKey)
    }

    @Test
    fun invalidPortsAndRequiredFieldsAreRejected() {
        val missingAddress = validDraft(ProtocolKind.TROJAN).with(ConfigFieldKeys.ADDRESS, "")
        assertTrue(runCatching { ConfigParserRouter.validate(missingAddress) }.isFailure)

        val invalidPort = validDraft(ProtocolKind.SOCKS).with(ConfigFieldKeys.PORT, "70000")
        assertTrue(runCatching { ConfigParserRouter.validate(invalidPort) }.isFailure)

        val invalidRaw = validDraft(ProtocolKind.RAW_JSON).with(ConfigFieldKeys.RAW_JSON, "{not json}")
        assertTrue(runCatching { ConfigParserRouter.validate(invalidRaw) }.isFailure)
    }

    private fun validDraft(protocol: ProtocolKind): ConfigDraft {
        val draft = ConfigDraft.create(protocol).withAll(mapOf(
            ConfigFieldKeys.REMARKS to "Synthetic ${protocol.key}",
            ConfigFieldKeys.ADDRESS to "198.51.100.8",
            ConfigFieldKeys.PORT to "443",
            ConfigFieldKeys.UUID to "00000000-0000-4000-8000-000000000001",
            ConfigFieldKeys.USERNAME to "test-user",
            ConfigFieldKeys.PASSWORD to "test-password",
            ConfigFieldKeys.PUBLIC_KEY to "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
            ConfigFieldKeys.PRE_SHARED_KEY to "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=",
            ConfigFieldKeys.NAMESERVER to "ns.example.test",
            ConfigFieldKeys.DNS_ADDRESS to "8.8.8.8",
            ConfigFieldKeys.RAW_JSON to "{\"outbounds\":[{\"protocol\":\"freedom\",\"tag\":\"synthetic\"}]}",
            ConfigFieldKeys.TRANSPORT to "tcp",
            ConfigFieldKeys.TLS to "none",
            ConfigFieldKeys.ENCRYPTION to "aes-128-gcm"
        ))
        return when (protocol) {
            ProtocolKind.VLESS -> draft.with(ConfigFieldKeys.TLS, "tls")
            ProtocolKind.VMESS -> draft.with(ConfigFieldKeys.VMESS_SECURITY, "auto")
            ProtocolKind.SOCKS -> draft.with(ConfigFieldKeys.AUTHENTICATION, "password")
            ProtocolKind.SHADOWSOCKSR -> draft.with(ConfigFieldKeys.SSR_PROTOCOL, "origin").with(ConfigFieldKeys.OBFS, "plain")
            ProtocolKind.WIREGUARD -> draft.with(ConfigFieldKeys.LOCAL_ADDRESS, "10.0.0.1/32")
            ProtocolKind.RAW_JSON -> draft
            ProtocolKind.SSH -> draft.with(ConfigFieldKeys.RESPONSE_STATUS, "200 Connection established")
            ProtocolKind.DNSTT_SSH -> draft
            ProtocolKind.HYSTERIA2 -> draft.with(ConfigFieldKeys.PORT_HOPPING, "20000-50000")
            else -> draft
        }
    }
}
