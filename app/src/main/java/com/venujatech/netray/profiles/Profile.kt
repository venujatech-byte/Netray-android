package com.venujatech.netray.profiles

import java.util.UUID

data class Profile(
    val id: String,
    val protocol: ProtocolKind,
    val fields: Map<String, String>,
    val link: String
) {
    val name: String
        get() = fields[ConfigFieldKeys.REMARKS].orEmpty().ifBlank { defaultName(protocol, server) }

    val server: String
        get() = fields[ConfigFieldKeys.ADDRESS].orEmpty()
            .ifBlank { fields[ConfigFieldKeys.DNS_ADDRESS].orEmpty() }
            .ifBlank { fields[ConfigFieldKeys.NAMESERVER].orEmpty() }
            .ifBlank { if (protocol == ProtocolKind.RAW_JSON) "RAW JSON" else "" }

    val port: Int
        get() = fields[ConfigFieldKeys.PORT]?.toIntOrNull() ?: 0

    val endpoint: String
        get() = when (protocol) {
            ProtocolKind.DNSTT_SSH -> fields[ConfigFieldKeys.DNS_ADDRESS].orEmpty().ifBlank { fields[ConfigFieldKeys.NAMESERVER].orEmpty() }
            ProtocolKind.RAW_JSON -> "RAW JSON"
            else -> if (port > 0) "$server:$port" else server
        }

    val transport: String
        get() = fields[ConfigFieldKeys.TRANSPORT].orEmpty()

    val security: String
        get() = when (protocol) {
            ProtocolKind.VLESS, ProtocolKind.VMESS, ProtocolKind.TROJAN, ProtocolKind.SOCKS,
            ProtocolKind.SHADOWSOCKS2022, ProtocolKind.HYSTERIA2 -> fields[ConfigFieldKeys.TLS].orEmpty()
            else -> "none"
        }

    val cardTags: List<String>
        get() = when (protocol) {
            ProtocolKind.VLESS -> buildList {
                add("VLESS")
                fields[ConfigFieldKeys.TRANSPORT].takeIf { !it.isNullOrBlank() }?.let { add(it.uppercase()) }
                when (fields[ConfigFieldKeys.TLS]) {
                    "tls" -> add("TLS")
                    "reality" -> add("RLTY")
                }
            }
            ProtocolKind.VMESS -> buildList {
                add("VMESS")
                fields[ConfigFieldKeys.TRANSPORT].takeIf { !it.isNullOrBlank() }?.let { add(it.uppercase()) }
                if (fields[ConfigFieldKeys.TLS] == "tls") add("TLS")
            }
            ProtocolKind.TROJAN -> listOfNotNull("TROJAN", fields[ConfigFieldKeys.TRANSPORT]?.uppercase(), fields[ConfigFieldKeys.TLS]?.uppercase().takeUnless { it == "NONE" || it.isNullOrBlank() })
            ProtocolKind.SOCKS -> listOfNotNull("SOCKS", "TCP", fields[ConfigFieldKeys.TLS]?.uppercase().takeUnless { it == "NONE" || it.isNullOrBlank() })
            ProtocolKind.SHADOWSOCKSR -> listOf("SSR", fields[ConfigFieldKeys.ENCRYPTION].orEmpty().uppercase()).filter(String::isNotBlank)
            ProtocolKind.SHADOWSOCKS2022 -> listOf("SS2022", fields[ConfigFieldKeys.ENCRYPTION].orEmpty().uppercase()).filter(String::isNotBlank)
            ProtocolKind.WIREGUARD -> listOf("WIREGUARD")
            ProtocolKind.RAW_JSON -> listOf("RAW JSON")
            ProtocolKind.SSH -> listOf("SSH")
            ProtocolKind.DNSTT_SSH -> listOf("DNSTT + SSH")
            ProtocolKind.HYSTERIA2 -> listOf("HYSTERIA2")
        }

    val duplicateKey: String
        get() {
            val credentials = when (protocol) {
                ProtocolKind.VLESS, ProtocolKind.VMESS -> fields[ConfigFieldKeys.UUID]
                ProtocolKind.SOCKS, ProtocolKind.SSH -> "${fields[ConfigFieldKeys.USERNAME]}:${fields[ConfigFieldKeys.PASSWORD]}"
                ProtocolKind.TROJAN, ProtocolKind.SHADOWSOCKSR, ProtocolKind.SHADOWSOCKS2022, ProtocolKind.HYSTERIA2 -> fields[ConfigFieldKeys.PASSWORD]
                ProtocolKind.WIREGUARD -> fields[ConfigFieldKeys.PASSWORD]
                ProtocolKind.DNSTT_SSH -> "${fields[ConfigFieldKeys.USERNAME]}:${fields[ConfigFieldKeys.PASSWORD]}:${fields[ConfigFieldKeys.PUBLIC_KEY]}"
                ProtocolKind.RAW_JSON -> link
            }
            return listOf(protocol.key, server.lowercase(), port.toString(), credentials.orEmpty()).joinToString("|")
        }

    companion object {
        fun parse(link: String): Profile = ProfileParserRouter.parse(link)

        fun fromDraft(draft: ConfigDraft, id: String = UUID.randomUUID().toString()): Profile {
            ConfigParserRouter.validate(draft)
            val serialized = ConfigParserRouter.serialize(draft)
            val parsed = parse(serialized)
            val remarks = draft[ConfigFieldKeys.REMARKS].trim()
            val allFields = if (remarks.isBlank()) parsed.fields else parsed.fields + (ConfigFieldKeys.REMARKS to remarks)
            return parsed.copy(id = id, fields = allFields)
        }

        private fun defaultName(protocol: ProtocolKind, server: String): String =
            server.takeIf(String::isNotBlank) ?: protocol.name
    }
}

object ProfileParserRouter {
    fun parse(link: String, id: String = UUID.randomUUID().toString()): Profile {
        val draft = ConfigParserRouter.parse(link)
        val fields = draft.fields.toMutableMap()
        if (fields[ConfigFieldKeys.REMARKS].isNullOrBlank()) {
            fields[ConfigFieldKeys.REMARKS] = fields[ConfigFieldKeys.ADDRESS]
                .orEmpty().ifBlank { fields[ConfigFieldKeys.NAMESERVER].orEmpty() }
        }
        return Profile(id, draft.protocol, fields.toMap(), link.trim())
    }
}
