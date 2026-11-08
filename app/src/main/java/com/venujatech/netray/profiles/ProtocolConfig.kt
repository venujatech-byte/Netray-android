package com.venujatech.netray.profiles

import androidx.annotation.StringRes
import com.venujatech.netray.R

enum class ProtocolKind(
    val key: String,
    @StringRes val titleRes: Int,
    val badge: String
) {
    VMESS("vmess", R.string.protocol_vmess, "XRAY"),
    VLESS("vless", R.string.protocol_vless, "XRAY"),
    SOCKS("socks", R.string.protocol_socks, "XRAY"),
    TROJAN("trojan", R.string.protocol_trojan, "XRAY"),
    SHADOWSOCKSR("ssr", R.string.protocol_shadowsocksr, "XRAY"),
    SHADOWSOCKS2022("ss2022", R.string.protocol_shadowsocks2022, "XRAY"),
    WIREGUARD("wireguard", R.string.protocol_wireguard, "XRAY"),
    RAW_JSON("raw", R.string.protocol_raw_json, "XRAY"),
    SSH("ssh", R.string.protocol_ssh, "NATIVE"),
    DNSTT_SSH("dnstt_ssh", R.string.protocol_dnstt_ssh, "NATIVE"),
    HYSTERIA2("hysteria2", R.string.protocol_hysteria2, "NEW");

    companion object {
        val menuOrder = entries
        fun fromKey(key: String): ProtocolKind? = entries.firstOrNull { it.key == key }
    }
}

enum class ConfigFieldKind { TEXT, PASSWORD, MULTILINE, DROPDOWN, SWITCH, BANDWIDTH }

data class ConfigFieldSpec(
    val key: String,
    @StringRes val labelRes: Int,
    val kind: ConfigFieldKind = ConfigFieldKind.TEXT,
    val defaultValue: String = "",
    val choices: List<String> = emptyList(),
    val required: Boolean = false,
    val allowEmpty: Boolean = true,
    val visibleWhen: Pair<String, String>? = null
)

data class ConfigRowSpec(
    val fields: List<ConfigFieldSpec>,
    val dividerBefore: Boolean = false
)

data class ProtocolFormSchema(
    val protocol: ProtocolKind,
    val rows: List<ConfigRowSpec>,
    val hasHelp: Boolean = false
)

data class ConfigDraft(
    val protocol: ProtocolKind,
    val fields: Map<String, String>
) {
    operator fun get(key: String): String = fields[key].orEmpty()

    fun with(key: String, value: String): ConfigDraft =
        copy(fields = fields + (key to value))

    fun withAll(values: Map<String, String>): ConfigDraft =
        copy(fields = fields + values)

    companion object {
        fun create(protocol: ProtocolKind): ConfigDraft {
            val defaults = ProtocolFormSchemas[protocol]?.rows.orEmpty()
                .flatMap { it.fields }
                .associate { it.key to it.defaultValue }
                .toMutableMap()
            if (protocol == ProtocolKind.RAW_JSON) defaults[ConfigFieldKeys.RAW_JSON] = ""
            return ConfigDraft(protocol, defaults)
        }
    }
}

object ConfigFieldKeys {
    const val REMARKS = "remarks"
    const val ADDRESS = "address"
    const val PORT = "port"
    const val UUID = "uuid"
    const val ALTER_ID = "alter_id"
    const val VMESS_SECURITY = "vmess_security"
    const val ENCRYPTION = "encryption"
    const val FLOW = "flow"
    const val TRANSPORT = "transport"
    const val HEADER_TYPE = "header_type"
    const val HOST = "host"
    const val PATH = "path"
    const val TLS = "tls"
    const val SNI = "sni"
    const val ALPN = "alpn"
    const val FINGERPRINT = "fingerprint"
    const val PUBLIC_KEY = "public_key"
    const val SHORT_ID = "short_id"
    const val AUTHENTICATION = "authentication"
    const val USERNAME = "username"
    const val PASSWORD = "password"
    const val SSR_PROTOCOL = "ssr_protocol"
    const val PROTOCOL_PARAMETER = "protocol_parameter"
    const val OBFS = "obfs"
    const val OBFS_PARAMETER = "obfs_parameter"
    const val LOCAL_ADDRESS = "local_address"
    const val PRE_SHARED_KEY = "pre_shared_key"
    const val RAW_JSON = "raw_json"
    const val PAYLOAD = "payload"
    const val RESPONSE_STATUS = "response_status"
    const val RESPONSE_POSITION = "response_position"
    const val PROXY_TYPE = "proxy_type"
    const val DNS_ADDRESS = "dns_address"
    const val NAMESERVER = "nameserver"
    const val PORT_HOPPING = "port_hopping"
    const val PORT_HOPPING_INTERVAL = "port_hopping_interval"
    const val OBFS_PASSWORD = "obfs_password"
    const val BANDWIDTH_UP = "bandwidth_up"
    const val BANDWIDTH_UP_UNIT = "bandwidth_up_unit"
    const val BANDWIDTH_DOWN = "bandwidth_down"
    const val BANDWIDTH_DOWN_UNIT = "bandwidth_down_unit"
    const val INSECURE = "insecure"
}

object ProtocolFormSchemas {
    private fun field(
        key: String,
        @StringRes label: Int,
        kind: ConfigFieldKind = ConfigFieldKind.TEXT,
        default: String = "",
        options: List<String> = emptyList(),
        required: Boolean = false,
        visibleWhen: Pair<String, String>? = null
    ) = ConfigFieldSpec(key, label, kind, default, options, required, visibleWhen = visibleWhen)

    private fun row(vararg fields: ConfigFieldSpec, divider: Boolean = false) = ConfigRowSpec(fields.toList(), divider)

    private val remarks = field(ConfigFieldKeys.REMARKS, R.string.field_remarks)
    private val address = field(ConfigFieldKeys.ADDRESS, R.string.field_address, required = true)
    private val port = field(ConfigFieldKeys.PORT, R.string.field_port, required = true)
    private val tls = field(ConfigFieldKeys.TLS, R.string.field_tls_type, ConfigFieldKind.DROPDOWN, "tls", listOf("none", "tls", "reality"))
    private val transport = field(ConfigFieldKeys.TRANSPORT, R.string.field_transfer_protocol, ConfigFieldKind.DROPDOWN, "tcp", listOf("tcp", "ws", "grpc", "http", "h2", "quic", "kcp"))
    private val header = field(ConfigFieldKeys.HEADER_TYPE, R.string.field_header_type, ConfigFieldKind.DROPDOWN, "none", listOf("none", "http"))
    private val sni = field(ConfigFieldKeys.SNI, R.string.field_sni)
    private val alpn = field(ConfigFieldKeys.ALPN, R.string.field_alpn, ConfigFieldKind.DROPDOWN, "none", listOf("none", "h2", "http/1.1", "h2,http/1.1"))
    private val fingerprint = field(ConfigFieldKeys.FINGERPRINT, R.string.field_fingerprint, ConfigFieldKind.DROPDOWN, "chrome", listOf("chrome", "firefox", "safari", "ios", "android", "random", "none"))
    private val password = field(ConfigFieldKeys.PASSWORD, R.string.field_password, ConfigFieldKind.PASSWORD)

    val schemas: Map<ProtocolKind, ProtocolFormSchema> = mapOf(
        ProtocolKind.VMESS to ProtocolFormSchema(ProtocolKind.VMESS, listOf(
            row(remarks), row(address, port),
            row(field(ConfigFieldKeys.UUID, R.string.field_uuid, required = true), field(ConfigFieldKeys.ALTER_ID, R.string.field_alter_id, default = "0"), divider = true),
            row(field(ConfigFieldKeys.VMESS_SECURITY, R.string.field_security, ConfigFieldKind.DROPDOWN, "auto", listOf("auto", "none", "aes-128-gcm", "chacha20-poly1305"))),
            row(transport, header), row(field(ConfigFieldKeys.HOST, R.string.field_host), field(ConfigFieldKeys.PATH, R.string.field_path)),
            row(field(ConfigFieldKeys.TLS, R.string.field_tls_type, ConfigFieldKind.DROPDOWN, "tls", listOf("none", "tls")), divider = true),
            row(sni, alpn), row(fingerprint)
        )),
        ProtocolKind.VLESS to ProtocolFormSchema(ProtocolKind.VLESS, listOf(
            row(remarks), row(address, port),
            row(field(ConfigFieldKeys.UUID, R.string.field_uuid, required = true), divider = true),
            row(field(ConfigFieldKeys.ENCRYPTION, R.string.field_encrypt_method, ConfigFieldKind.DROPDOWN, "none", listOf("none", "zero", "aes-128-gcm", "chacha20-poly1305"))),
            row(field(ConfigFieldKeys.FLOW, R.string.field_flow, ConfigFieldKind.DROPDOWN, "none", listOf("none", "xtls-rprx-vision", "xtls-rprx-vision-udp443"))),
            row(transport, header), row(field(ConfigFieldKeys.HOST, R.string.field_host), field(ConfigFieldKeys.PATH, R.string.field_path)),
            row(tls, divider = true), row(sni, alpn), row(fingerprint),
            row(field(ConfigFieldKeys.PUBLIC_KEY, R.string.field_reality_public_key, visibleWhen = ConfigFieldKeys.TLS to "reality")),
            row(field(ConfigFieldKeys.SHORT_ID, R.string.field_short_id, visibleWhen = ConfigFieldKeys.TLS to "reality"))
        )),
        ProtocolKind.SOCKS to ProtocolFormSchema(ProtocolKind.SOCKS, listOf(
            row(remarks), row(address, port),
            row(field(ConfigFieldKeys.AUTHENTICATION, R.string.field_authentication, ConfigFieldKind.DROPDOWN, "noauth", listOf("noauth", "password")), divider = true),
            row(field(ConfigFieldKeys.USERNAME, R.string.field_username, visibleWhen = ConfigFieldKeys.AUTHENTICATION to "password")),
            row(password.copy(visibleWhen = ConfigFieldKeys.AUTHENTICATION to "password")),
            row(field(ConfigFieldKeys.TLS, R.string.field_tls_type, ConfigFieldKind.DROPDOWN, "none", listOf("none", "tls")), divider = true)
        )),
        ProtocolKind.TROJAN to ProtocolFormSchema(ProtocolKind.TROJAN, listOf(
            row(remarks), row(address, port), row(password.copy(required = true), divider = true),
            row(field(ConfigFieldKeys.TRANSPORT, R.string.field_transfer_protocol, ConfigFieldKind.DROPDOWN, "tcp", listOf("tcp", "ws", "grpc", "h2"))),
            row(field(ConfigFieldKeys.HEADER_TYPE, R.string.field_header_type, ConfigFieldKind.DROPDOWN, "none", listOf("none", "http"))),
            row(field(ConfigFieldKeys.TLS, R.string.field_tls_type, ConfigFieldKind.DROPDOWN, "none", listOf("none", "tls")), divider = true),
            row(sni, alpn)
        )),
        ProtocolKind.SHADOWSOCKSR to ProtocolFormSchema(ProtocolKind.SHADOWSOCKSR, listOf(
            row(remarks), row(address, port), row(field(ConfigFieldKeys.ENCRYPTION, R.string.field_encrypt_method, ConfigFieldKind.DROPDOWN, "none", listOf("none", "aes-128-cfb", "aes-256-cfb", "chacha20-ietf-poly1305")), divider = true),
            row(password.copy(required = true)), row(field(ConfigFieldKeys.SSR_PROTOCOL, R.string.field_protocol, ConfigFieldKind.DROPDOWN, "origin", listOf("origin", "auth_sha1_v4", "auth_aes128_md5", "auth_aes128_sha1"))),
            row(field(ConfigFieldKeys.PROTOCOL_PARAMETER, R.string.field_protocol_parameter), divider = true),
            row(field(ConfigFieldKeys.OBFS, R.string.field_obfs, ConfigFieldKind.DROPDOWN, "plain", listOf("plain", "http_simple", "http_post", "tls1.2_ticket_auth"))),
            row(field(ConfigFieldKeys.OBFS_PARAMETER, R.string.field_obfs_parameter))
        )),
        ProtocolKind.SHADOWSOCKS2022 to ProtocolFormSchema(ProtocolKind.SHADOWSOCKS2022, listOf(
            row(remarks), row(address, port), row(field(ConfigFieldKeys.ENCRYPTION, R.string.field_encrypt_method, ConfigFieldKind.DROPDOWN, "none", listOf("none", "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm")), divider = true),
            row(password.copy(required = true), divider = true),
            row(field(ConfigFieldKeys.TRANSPORT, R.string.field_transfer_protocol, ConfigFieldKind.DROPDOWN, "tcp", listOf("tcp", "ws", "grpc"))),
            row(field(ConfigFieldKeys.HEADER_TYPE, R.string.field_header_type, ConfigFieldKind.DROPDOWN, "none", listOf("none", "http"))),
            row(field(ConfigFieldKeys.TLS, R.string.field_tls_type, ConfigFieldKind.DROPDOWN, "none", listOf("none", "tls")), divider = true)
        )),
        ProtocolKind.WIREGUARD to ProtocolFormSchema(ProtocolKind.WIREGUARD, listOf(
            row(remarks), row(field(ConfigFieldKeys.ADDRESS, R.string.field_endpoint, required = true), port),
            row(field(ConfigFieldKeys.LOCAL_ADDRESS, R.string.field_local_address, default = "10.0.0.1, fd59:7153:2388:b5fd:0000:0000:0000:0001"), divider = true),
            row(field(ConfigFieldKeys.PASSWORD, R.string.field_private_key, ConfigFieldKind.PASSWORD, required = true)),
            row(field(ConfigFieldKeys.PUBLIC_KEY, R.string.field_public_key, required = true)),
            row(field(ConfigFieldKeys.PRE_SHARED_KEY, R.string.field_pre_shared_key))
        )),
        ProtocolKind.RAW_JSON to ProtocolFormSchema(ProtocolKind.RAW_JSON, emptyList()),
        ProtocolKind.SSH to ProtocolFormSchema(ProtocolKind.SSH, listOf(
            row(remarks), row(address, port),
            row(field(ConfigFieldKeys.USERNAME, R.string.field_username, required = true), divider = true),
            row(password.copy(required = true)), row(field(ConfigFieldKeys.PAYLOAD, R.string.field_ssh_payload, ConfigFieldKind.MULTILINE), divider = true),
            row(field(ConfigFieldKeys.RESPONSE_STATUS, R.string.field_response_status, ConfigFieldKind.DROPDOWN, "200 Connection established", listOf("200 Connection established", "200 OK", "101 Switching Protocols"))),
            row(field(ConfigFieldKeys.RESPONSE_POSITION, R.string.field_response_position, ConfigFieldKind.DROPDOWN, "pre_request", listOf("pre_request", "post_request"))),
            row(field(ConfigFieldKeys.PROXY_TYPE, R.string.field_proxy_type, ConfigFieldKind.DROPDOWN, "none", listOf("none", "direct", "socks5")), divider = true),
            row(field(ConfigFieldKeys.TLS, R.string.field_tls_type, ConfigFieldKind.DROPDOWN, "none", listOf("none", "tls")), divider = true)
        ), hasHelp = true),
        ProtocolKind.DNSTT_SSH to ProtocolFormSchema(ProtocolKind.DNSTT_SSH, listOf(
            row(remarks), row(field(ConfigFieldKeys.DNS_ADDRESS, R.string.field_dns_address, default = "8.8.8.8"), divider = true),
            row(field(ConfigFieldKeys.NAMESERVER, R.string.field_nameserver, required = true)), row(field(ConfigFieldKeys.PUBLIC_KEY, R.string.field_public_key, required = true)),
            row(field(ConfigFieldKeys.USERNAME, R.string.field_username, required = true), divider = true), row(password.copy(required = true))
        )),
        ProtocolKind.HYSTERIA2 to ProtocolFormSchema(ProtocolKind.HYSTERIA2, listOf(
            row(remarks), row(address, port),
            row(field(ConfigFieldKeys.PORT_HOPPING, R.string.field_port_hopping, default = "20000-50000")),
            row(field(ConfigFieldKeys.PORT_HOPPING_INTERVAL, R.string.field_port_hopping_interval, default = "10")),
            row(password.copy(required = true), field(ConfigFieldKeys.OBFS_PASSWORD, R.string.field_obfs_password), divider = true),
            row(field(ConfigFieldKeys.BANDWIDTH_UP, R.string.field_bandwidth_up, ConfigFieldKind.BANDWIDTH, "100"), field(ConfigFieldKeys.BANDWIDTH_DOWN, R.string.field_bandwidth_down, ConfigFieldKind.BANDWIDTH, "100"), divider = true),
            row(field(ConfigFieldKeys.TLS, R.string.field_tls_type, ConfigFieldKind.DROPDOWN, "none", listOf("none", "tls")), divider = true),
            row(sni), row(field(ConfigFieldKeys.INSECURE, R.string.field_insecure, ConfigFieldKind.SWITCH, "false"))
        ))
    )

    operator fun get(protocol: ProtocolKind): ProtocolFormSchema? = schemas[protocol]
}
