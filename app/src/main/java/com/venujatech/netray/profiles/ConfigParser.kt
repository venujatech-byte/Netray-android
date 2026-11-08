package com.venujatech.netray.profiles

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64 as JBase64

interface ProtocolConfigParser {
    val protocol: ProtocolKind
    fun parse(text: String): ConfigDraft
    fun serialize(draft: ConfigDraft): String
}

data class ImportBatch(
    val configs: List<ConfigDraft>,
    val skipped: Int,
    val errors: List<String>
)

object ConfigParserRouter {
    private val parsers: Map<ProtocolKind, ProtocolConfigParser> = listOf(
        VmessConfigParser,
        VlessConfigParser,
        SocksConfigParser,
        TrojanConfigParser,
        ShadowsocksRConfigParser,
        Shadowsocks2022ConfigParser,
        WireGuardConfigParser,
        RawJsonConfigParser,
        SshConfigParser,
        DnsttSshConfigParser,
        Hysteria2ConfigParser
    ).associateBy { it.protocol }

    fun parse(text: String): ConfigDraft {
        val input = text.trim()
        require(input.isNotEmpty()) { "Configuration is empty" }
        require(input.length <= MAX_CONFIG_CHARS) { "Configuration is too large" }
        val protocol = detect(input)
        return parser(protocol).parse(input)
    }

    fun serialize(draft: ConfigDraft): String {
        validate(draft)
        return parser(draft.protocol).serialize(draft)
    }

    fun parseMany(text: String): ImportBatch {
        var input = text.trim()
        if (input.isEmpty()) return ImportBatch(emptyList(), 0, listOf("Import is empty"))
        if (input.length > MAX_IMPORT_CHARS) return ImportBatch(emptyList(), 1, listOf("Import file is too large"))

        if (!looksLikeConfig(input)) {
            val decoded = runCatching { decodeBase64(input) }.getOrNull()?.trim().orEmpty()
            if (decoded.isNotBlank() && looksLikeConfig(decoded)) input = decoded
        }

        val candidates = runCatching {
            if (input.startsWith("[")) {
                val array = JSONArray(input)
                (0 until array.length()).map { index ->
                    when (val item = array.get(index)) {
                        is JSONObject -> item.toString()
                        is String -> item
                        else -> error("Entry ${index + 1} is not a config string or JSON object")
                    }
                }
            } else if (input.startsWith("{")) {
                listOf(input)
            } else {
                splitConfigEntries(input)
            }
        }.getOrElse { return ImportBatch(emptyList(), 1, listOf("Invalid JSON import")) }

        val parsed = mutableListOf<ConfigDraft>()
        val errors = mutableListOf<String>()
        candidates.forEachIndexed { index, candidate ->
            runCatching { parse(candidate) }
                .onSuccess(parsed::add)
                .onFailure { errors += "Entry ${index + 1}: ${it.message ?: "invalid config"}" }
        }
        return ImportBatch(parsed, errors.size, errors)
    }

    fun detect(text: String): ProtocolKind {
        val value = text.trimStart()
        if (value.startsWith("{")) return ProtocolKind.RAW_JSON
        val scheme = value.substringBefore("://", "").lowercase()
        return when (scheme) {
            "vmess" -> ProtocolKind.VMESS
            "vless" -> ProtocolKind.VLESS
            "socks", "socks5" -> ProtocolKind.SOCKS
            "trojan" -> ProtocolKind.TROJAN
            "ssr" -> ProtocolKind.SHADOWSOCKSR
            "ss" -> ProtocolKind.SHADOWSOCKS2022
            "wireguard" -> ProtocolKind.WIREGUARD
            "ssh" -> ProtocolKind.SSH
            "dnstt", "dnstt+ssh" -> ProtocolKind.DNSTT_SSH
            "hy2", "hysteria2" -> ProtocolKind.HYSTERIA2
            else -> throw IllegalArgumentException("Unsupported configuration scheme")
        }
    }

    fun validate(draft: ConfigDraft) {
        val schema = ProtocolFormSchemas[draft.protocol] ?: return
        schema.rows.flatMap { it.fields }.filter { it.visibleWhen == null || draft[it.visibleWhen.first] == it.visibleWhen.second }
            .forEach { field ->
                if (field.required && draft[field.key].isBlank()) {
                    throw IllegalArgumentException("${field.key} is required")
                }
            }
        if (draft.protocol == ProtocolKind.DNSTT_SSH) {
            require(draft[ConfigFieldKeys.DNS_ADDRESS].isNotBlank()) { "DNS address is required" }
            require(draft[ConfigFieldKeys.NAMESERVER].isNotBlank()) { "Nameserver is required" }
        } else if (draft.protocol != ProtocolKind.RAW_JSON) {
            val port = draft[ConfigFieldKeys.PORT].toIntOrNull()
            require(port != null && port in 1..65535) { "Port must be between 1 and 65535" }
            require(draft[ConfigFieldKeys.ADDRESS].isNotBlank()) { "Address is required" }
        } else {
            JSONObject(draft[ConfigFieldKeys.RAW_JSON])
        }
    }

    private fun parser(protocol: ProtocolKind): ProtocolConfigParser =
        parsers[protocol] ?: error("No config parser for ${protocol.key}")

    private fun looksLikeConfig(text: String): Boolean = text.trimStart().let { it.startsWith("{") || it.startsWith("[") } ||
        SCHEME_REGEX.containsMatchIn(text)

    private fun splitConfigEntries(text: String): List<String> {
        return text.lineSequence().flatMap { line ->
            val starts = SCHEME_REGEX.findAll(line).map { it.range.first }.toList()
            if (starts.isEmpty()) sequenceOf(line.trim()).filter(String::isNotEmpty)
            else starts.mapIndexed { index, start ->
                val end = starts.getOrNull(index + 1) ?: line.length
                line.substring(start, end).trim()
            }.asSequence().filter(String::isNotEmpty)
        }.toList()
    }

    private const val MAX_CONFIG_CHARS = 1_048_576
    private const val MAX_IMPORT_CHARS = 4_194_304
    private val SCHEME_REGEX = Regex("(?i)(?:vmess|vless|trojan|ss|ssr|socks5?|hysteria2|hy2|wireguard|ssh|dnstt\\+ssh|dnstt)://")
}

private object VmessConfigParser : ProtocolConfigParser {
    override val protocol = ProtocolKind.VMESS

    override fun parse(text: String): ConfigDraft {
        val payload = text.substringAfter("://", "")
        val json = JSONObject(decodeBase64(payload))
        return ConfigDraft.create(protocol).withAll(mapOf(
            ConfigFieldKeys.REMARKS to json.optString("ps"),
            ConfigFieldKeys.ADDRESS to json.optString("add"),
            ConfigFieldKeys.PORT to json.optString("port", "443"),
            ConfigFieldKeys.UUID to json.optString("id"),
            ConfigFieldKeys.ALTER_ID to json.optString("aid", "0"),
            ConfigFieldKeys.VMESS_SECURITY to json.optString("scy", "auto"),
            ConfigFieldKeys.TRANSPORT to json.optString("net", "tcp"),
            ConfigFieldKeys.HEADER_TYPE to json.optString("type", "none"),
            ConfigFieldKeys.HOST to json.optString("host"),
            ConfigFieldKeys.PATH to json.optString("path"),
            ConfigFieldKeys.TLS to if (json.optString("tls") == "tls") "tls" else "none",
            ConfigFieldKeys.SNI to json.optString("sni", json.optString("host")),
            ConfigFieldKeys.ALPN to json.optString("alpn", "none"),
            ConfigFieldKeys.FINGERPRINT to json.optString("fp", "chrome")
        ))
    }

    override fun serialize(draft: ConfigDraft): String {
        val json = JSONObject()
            .put("v", "2")
            .put("ps", draft[ConfigFieldKeys.REMARKS])
            .put("add", draft[ConfigFieldKeys.ADDRESS])
            .put("port", draft[ConfigFieldKeys.PORT].toInt())
            .put("id", draft[ConfigFieldKeys.UUID])
            .put("aid", draft[ConfigFieldKeys.ALTER_ID].ifBlank { "0" }.toInt())
            .put("scy", draft[ConfigFieldKeys.VMESS_SECURITY].ifBlank { "auto" })
            .put("net", draft[ConfigFieldKeys.TRANSPORT].ifBlank { "tcp" })
            .put("type", draft[ConfigFieldKeys.HEADER_TYPE].ifBlank { "none" })
            .put("host", draft[ConfigFieldKeys.HOST])
            .put("path", draft[ConfigFieldKeys.PATH])
            .put("tls", if (draft[ConfigFieldKeys.TLS] == "none") "" else "tls")
            .put("sni", draft[ConfigFieldKeys.SNI])
            .put("alpn", draft[ConfigFieldKeys.ALPN].takeUnless { it == "none" }.orEmpty())
            .put("fp", draft[ConfigFieldKeys.FINGERPRINT].takeUnless { it == "none" }.orEmpty())
        return "vmess://${encodeBase64(json.toString())}"
    }
}

private object VlessConfigParser : ProtocolConfigParser {
    override val protocol = ProtocolKind.VLESS
    override fun parse(text: String): ConfigDraft {
        val uri = URI(text)
        val query = uri.queryParams()
        val defaults = ConfigDraft.create(protocol)
        return defaults.withAll(mapOf(
            ConfigFieldKeys.REMARKS to uri.remark(),
            ConfigFieldKeys.ADDRESS to uri.hostName(),
            ConfigFieldKeys.PORT to uri.portOr("443"),
            ConfigFieldKeys.UUID to uri.userName(),
            ConfigFieldKeys.ENCRYPTION to query["encryption"].orEmpty().ifBlank { "none" },
            ConfigFieldKeys.FLOW to query["flow"].orEmpty().ifBlank { "none" },
            ConfigFieldKeys.TRANSPORT to query["type"].orEmpty().ifBlank { query["net"].orEmpty().ifBlank { "tcp" } },
            ConfigFieldKeys.HEADER_TYPE to query["headerType"].orEmpty().ifBlank { "none" },
            ConfigFieldKeys.HOST to query["host"].orEmpty(),
            ConfigFieldKeys.PATH to query["path"].orEmpty(),
            ConfigFieldKeys.TLS to query["security"].orEmpty().ifBlank { "tls" },
            ConfigFieldKeys.SNI to query["sni"].orEmpty().ifBlank { query["peer"].orEmpty() },
            ConfigFieldKeys.ALPN to query["alpn"].orEmpty().ifBlank { "none" },
            ConfigFieldKeys.FINGERPRINT to query["fp"].orEmpty().ifBlank { "chrome" },
            ConfigFieldKeys.PUBLIC_KEY to query["pbk"].orEmpty(),
            ConfigFieldKeys.SHORT_ID to query["sid"].orEmpty()
        ))
    }

    override fun serialize(draft: ConfigDraft): String {
        val query = linkedMapOf(
            "encryption" to draft[ConfigFieldKeys.ENCRYPTION].ifBlank { "none" },
            "security" to draft[ConfigFieldKeys.TLS].ifBlank { "none" },
            "type" to draft[ConfigFieldKeys.TRANSPORT].ifBlank { "tcp" }
        )
        addIfSet(query, "flow", draft[ConfigFieldKeys.FLOW], "none")
        addIfSet(query, "headerType", draft[ConfigFieldKeys.HEADER_TYPE], "none")
        addIfSet(query, "host", draft[ConfigFieldKeys.HOST])
        addIfSet(query, "path", draft[ConfigFieldKeys.PATH])
        addIfSet(query, "sni", draft[ConfigFieldKeys.SNI])
        addIfSet(query, "alpn", draft[ConfigFieldKeys.ALPN], "none")
        addIfSet(query, "fp", draft[ConfigFieldKeys.FINGERPRINT], "none")
        if (draft[ConfigFieldKeys.TLS] == "reality") {
            addIfSet(query, "pbk", draft[ConfigFieldKeys.PUBLIC_KEY])
            addIfSet(query, "sid", draft[ConfigFieldKeys.SHORT_ID])
        }
        return buildUri("vless", "${draft[ConfigFieldKeys.UUID]}@${formatHost(draft[ConfigFieldKeys.ADDRESS])}", draft[ConfigFieldKeys.PORT], query, draft[ConfigFieldKeys.REMARKS])
    }
}

private object SocksConfigParser : ProtocolConfigParser {
    override val protocol = ProtocolKind.SOCKS
    override fun parse(text: String): ConfigDraft {
        val uri = URI(text)
        val info = uri.userInfo.orEmpty().decodeComponent()
        val credentials = if (info.isNotEmpty() && !info.contains(':')) decodeBase64OrOriginal(info) else info
        val parts = credentials.split(':', limit = 2)
        val auth = parts.size == 2
        return ConfigDraft.create(protocol).withAll(mapOf(
            ConfigFieldKeys.REMARKS to uri.remark(),
            ConfigFieldKeys.ADDRESS to uri.hostName(),
            ConfigFieldKeys.PORT to uri.portOr("1080"),
            ConfigFieldKeys.AUTHENTICATION to if (auth) "password" else "noauth",
            ConfigFieldKeys.USERNAME to parts.firstOrNull().orEmpty().takeIf { auth }.orEmpty(),
            ConfigFieldKeys.PASSWORD to parts.getOrNull(1).orEmpty(),
            ConfigFieldKeys.TLS to uri.queryParams()["security"].orEmpty().ifBlank { "none" }
        ))
    }
    override fun serialize(draft: ConfigDraft): String {
        val auth = if (draft[ConfigFieldKeys.AUTHENTICATION] == "password") {
            "${encodeComponent(draft[ConfigFieldKeys.USERNAME])}:${encodeComponent(draft[ConfigFieldKeys.PASSWORD])}@"
        } else ""
        return buildUri("socks5", "$auth${formatHost(draft[ConfigFieldKeys.ADDRESS])}", draft[ConfigFieldKeys.PORT], mapOf("security" to draft[ConfigFieldKeys.TLS]), draft[ConfigFieldKeys.REMARKS])
    }
}

private object TrojanConfigParser : ProtocolConfigParser {
    override val protocol = ProtocolKind.TROJAN
    override fun parse(text: String): ConfigDraft {
        val uri = URI(text)
        val query = uri.queryParams()
        return ConfigDraft.create(protocol).withAll(mapOf(
            ConfigFieldKeys.REMARKS to uri.remark(), ConfigFieldKeys.ADDRESS to uri.hostName(), ConfigFieldKeys.PORT to uri.portOr("443"),
            ConfigFieldKeys.PASSWORD to uri.rawUserInfo.orEmpty().decodeComponent(), ConfigFieldKeys.TRANSPORT to query["type"].orEmpty().ifBlank { "tcp" },
            ConfigFieldKeys.HEADER_TYPE to query["headerType"].orEmpty().ifBlank { "none" },
            ConfigFieldKeys.TLS to query["security"].orEmpty().ifBlank { "tls" },
            ConfigFieldKeys.SNI to query["sni"].orEmpty(), ConfigFieldKeys.ALPN to query["alpn"].orEmpty().ifBlank { "none" }
        ))
    }
    override fun serialize(draft: ConfigDraft): String {
        val query = linkedMapOf("security" to draft[ConfigFieldKeys.TLS].ifBlank { "none" })
        addIfSet(query, "type", draft[ConfigFieldKeys.TRANSPORT], "tcp")
        addIfSet(query, "headerType", draft[ConfigFieldKeys.HEADER_TYPE], "none")
        addIfSet(query, "sni", draft[ConfigFieldKeys.SNI])
        addIfSet(query, "alpn", draft[ConfigFieldKeys.ALPN], "none")
        return buildUri("trojan", "${encodeComponent(draft[ConfigFieldKeys.PASSWORD])}@${formatHost(draft[ConfigFieldKeys.ADDRESS])}", draft[ConfigFieldKeys.PORT], query, draft[ConfigFieldKeys.REMARKS])
    }
}

private object ShadowsocksRConfigParser : ProtocolConfigParser {
    override val protocol = ProtocolKind.SHADOWSOCKSR
    override fun parse(text: String): ConfigDraft {
        val encoded = text.substringAfter("://", "").substringBefore('#').substringBefore('?')
        val raw = decodeBase64(encoded)
        val base = raw.substringBefore("/?")
        val parts = base.split(':', limit = 6)
        require(parts.size >= 6) { "Malformed ShadowsocksR link" }
        val query = queryParameters(raw.substringAfter("/?", ""))
        return ConfigDraft.create(protocol).withAll(mapOf(
            ConfigFieldKeys.ADDRESS to parts[0].removePrefix("[").removeSuffix("]"),
            ConfigFieldKeys.PORT to parts[1], ConfigFieldKeys.SSR_PROTOCOL to parts[2],
            ConfigFieldKeys.ENCRYPTION to parts[3], ConfigFieldKeys.OBFS to parts[4],
            ConfigFieldKeys.PASSWORD to decodeBase64(parts[5]),
            ConfigFieldKeys.REMARKS to query["remarks"]?.let(::decodeBase64).orEmpty(),
            ConfigFieldKeys.PROTOCOL_PARAMETER to query["protoparam"]?.let(::decodeBase64).orEmpty(),
            ConfigFieldKeys.OBFS_PARAMETER to query["obfsparam"]?.let(::decodeBase64).orEmpty()
        ))
    }
    override fun serialize(draft: ConfigDraft): String {
        val raw = buildString {
            append(formatHost(draft[ConfigFieldKeys.ADDRESS])); append(':'); append(draft[ConfigFieldKeys.PORT]); append(':')
            append(draft[ConfigFieldKeys.SSR_PROTOCOL].ifBlank { "origin" }); append(':')
            append(draft[ConfigFieldKeys.ENCRYPTION].ifBlank { "none" }); append(':')
            append(draft[ConfigFieldKeys.OBFS].ifBlank { "plain" }); append(':')
            append(encodeBase64(draft[ConfigFieldKeys.PASSWORD]))
            val params = linkedMapOf<String, String>()
            addIfSet(params, "remarks", draft[ConfigFieldKeys.REMARKS].let(::encodeBase64))
            addIfSet(params, "protoparam", draft[ConfigFieldKeys.PROTOCOL_PARAMETER].let(::encodeBase64))
            addIfSet(params, "obfsparam", draft[ConfigFieldKeys.OBFS_PARAMETER].let(::encodeBase64))
            if (params.isNotEmpty()) append("/?").append(params.entries.joinToString("&") { "${it.key}=${it.value}" })
        }
        return "ssr://${encodeBase64Url(raw)}"
    }
}

private object Shadowsocks2022ConfigParser : ProtocolConfigParser {
    override val protocol = ProtocolKind.SHADOWSOCKS2022
    override fun parse(text: String): ConfigDraft {
        val uri = URI(text)
        val info = uri.userInfo.orEmpty().decodeComponent()
        val decodedInfo = decodeBase64OrOriginal(info)
        val credentials = decodedInfo.split(':', limit = 2)
        val query = uri.queryParams()
        return ConfigDraft.create(protocol).withAll(mapOf(
            ConfigFieldKeys.REMARKS to uri.remark(), ConfigFieldKeys.ADDRESS to uri.hostName(), ConfigFieldKeys.PORT to uri.portOr("8388"),
            ConfigFieldKeys.ENCRYPTION to credentials.firstOrNull().orEmpty().ifBlank { "none" },
            ConfigFieldKeys.PASSWORD to credentials.getOrNull(1).orEmpty(), ConfigFieldKeys.TRANSPORT to query["type"].orEmpty().ifBlank { "tcp" },
            ConfigFieldKeys.HEADER_TYPE to query["headerType"].orEmpty().ifBlank { "none" }, ConfigFieldKeys.TLS to query["security"].orEmpty().ifBlank { "none" }
        ))
    }
    override fun serialize(draft: ConfigDraft): String {
        val userInfo = encodeBase64("${draft[ConfigFieldKeys.ENCRYPTION]}:${draft[ConfigFieldKeys.PASSWORD]}")
        val query = linkedMapOf("type" to draft[ConfigFieldKeys.TRANSPORT].ifBlank { "tcp" })
        addIfSet(query, "headerType", draft[ConfigFieldKeys.HEADER_TYPE], "none")
        addIfSet(query, "security", draft[ConfigFieldKeys.TLS], "none")
        return buildUri("ss", "$userInfo@${formatHost(draft[ConfigFieldKeys.ADDRESS])}", draft[ConfigFieldKeys.PORT], query, draft[ConfigFieldKeys.REMARKS])
    }
}

private object WireGuardConfigParser : ProtocolConfigParser {
    override val protocol = ProtocolKind.WIREGUARD
    override fun parse(text: String): ConfigDraft {
        val uri = URI(text)
        val query = uri.queryParams()
        return ConfigDraft.create(protocol).withAll(mapOf(
            ConfigFieldKeys.REMARKS to uri.remark(), ConfigFieldKeys.PASSWORD to uri.userName(),
            ConfigFieldKeys.ADDRESS to uri.hostName(), ConfigFieldKeys.PORT to uri.portOr("51820"),
            ConfigFieldKeys.LOCAL_ADDRESS to (query["address"] ?: "10.0.0.1, fd59:7153:2388:b5fd:0000:0000:0000:0001"),
            ConfigFieldKeys.PUBLIC_KEY to (query["public_key"] ?: query["publicKey"] ?: query["pubkey"].orEmpty()),
            ConfigFieldKeys.PRE_SHARED_KEY to (query["preshared_key"] ?: query["presharedKey"].orEmpty())
        ))
    }
    override fun serialize(draft: ConfigDraft): String {
        val query = linkedMapOf(
            "public_key" to draft[ConfigFieldKeys.PUBLIC_KEY],
            "address" to draft[ConfigFieldKeys.LOCAL_ADDRESS]
        )
        addIfSet(query, "preshared_key", draft[ConfigFieldKeys.PRE_SHARED_KEY])
        return buildUri("wireguard", "${encodeComponent(draft[ConfigFieldKeys.PASSWORD])}@${formatHost(draft[ConfigFieldKeys.ADDRESS])}", draft[ConfigFieldKeys.PORT], query, draft[ConfigFieldKeys.REMARKS])
    }
}

private object RawJsonConfigParser : ProtocolConfigParser {
    override val protocol = ProtocolKind.RAW_JSON
    override fun parse(text: String): ConfigDraft {
        val json = JSONObject(text)
        val outbounds = json.optJSONArray("outbounds")
        val tag = outbounds?.optJSONObject(0)?.optString("tag").orEmpty()
        return ConfigDraft.create(protocol).withAll(mapOf(ConfigFieldKeys.REMARKS to tag, ConfigFieldKeys.RAW_JSON to text.trim()))
    }
    override fun serialize(draft: ConfigDraft): String {
        JSONObject(draft[ConfigFieldKeys.RAW_JSON])
        return draft[ConfigFieldKeys.RAW_JSON]
    }
}

private object SshConfigParser : ProtocolConfigParser {
    override val protocol = ProtocolKind.SSH
    override fun parse(text: String): ConfigDraft {
        val uri = URI(text)
        val userInfo = uri.userInfo.orEmpty().decodeComponent().split(':', limit = 2)
        val query = uri.queryParams()
        return ConfigDraft.create(protocol).withAll(mapOf(
            ConfigFieldKeys.REMARKS to uri.remark(), ConfigFieldKeys.ADDRESS to uri.hostName(), ConfigFieldKeys.PORT to uri.portOr("22"),
            ConfigFieldKeys.USERNAME to userInfo.firstOrNull().orEmpty(), ConfigFieldKeys.PASSWORD to userInfo.getOrNull(1).orEmpty(),
            ConfigFieldKeys.PAYLOAD to query["payload"].orEmpty(), ConfigFieldKeys.RESPONSE_STATUS to query["responseStatus"].orEmpty().ifBlank { "200 Connection established" },
            ConfigFieldKeys.RESPONSE_POSITION to query["responsePosition"].orEmpty().ifBlank { "pre_request" },
            ConfigFieldKeys.PROXY_TYPE to query["proxyType"].orEmpty().ifBlank { "none" }, ConfigFieldKeys.TLS to query["security"].orEmpty().ifBlank { "none" }
        ))
    }
    override fun serialize(draft: ConfigDraft): String {
        val query = linkedMapOf<String, String>()
        addIfSet(query, "payload", draft[ConfigFieldKeys.PAYLOAD])
        query["responseStatus"] = draft[ConfigFieldKeys.RESPONSE_STATUS]
        query["responsePosition"] = draft[ConfigFieldKeys.RESPONSE_POSITION]
        query["proxyType"] = draft[ConfigFieldKeys.PROXY_TYPE]
        query["security"] = draft[ConfigFieldKeys.TLS]
        val auth = "${encodeComponent(draft[ConfigFieldKeys.USERNAME])}:${encodeComponent(draft[ConfigFieldKeys.PASSWORD])}"
        return buildUri("ssh", "$auth@${formatHost(draft[ConfigFieldKeys.ADDRESS])}", draft[ConfigFieldKeys.PORT], query, draft[ConfigFieldKeys.REMARKS])
    }
}

private object DnsttSshConfigParser : ProtocolConfigParser {
    override val protocol = ProtocolKind.DNSTT_SSH
    override fun parse(text: String): ConfigDraft {
        val uri = URI(text)
        val query = uri.queryParams()
        val ssh = query["ssh"].orEmpty().substringBefore('@').decodeComponent().split(':', limit = 2)
        return ConfigDraft.create(protocol).withAll(mapOf(
            ConfigFieldKeys.REMARKS to uri.remark(), ConfigFieldKeys.DNS_ADDRESS to (query["dns"] ?: uri.hostName()),
            ConfigFieldKeys.NAMESERVER to (query["nameserver"] ?: uri.hostName()), ConfigFieldKeys.PUBLIC_KEY to (query["pubkey"] ?: query["public_key"].orEmpty()),
            ConfigFieldKeys.USERNAME to (query["username"] ?: ssh.firstOrNull().orEmpty()), ConfigFieldKeys.PASSWORD to (query["password"] ?: ssh.getOrNull(1).orEmpty())
        ))
    }
    override fun serialize(draft: ConfigDraft): String {
        val query = linkedMapOf(
            "dns" to draft[ConfigFieldKeys.DNS_ADDRESS], "nameserver" to draft[ConfigFieldKeys.NAMESERVER],
            "pubkey" to draft[ConfigFieldKeys.PUBLIC_KEY], "username" to draft[ConfigFieldKeys.USERNAME],
            "password" to draft[ConfigFieldKeys.PASSWORD]
        )
        return buildUri("dnstt+ssh", formatHost(draft[ConfigFieldKeys.NAMESERVER]), "53", query, draft[ConfigFieldKeys.REMARKS])
    }
}

private object Hysteria2ConfigParser : ProtocolConfigParser {
    override val protocol = ProtocolKind.HYSTERIA2
    override fun parse(text: String): ConfigDraft {
        val uri = URI(text)
        val query = uri.queryParams()
        return ConfigDraft.create(protocol).withAll(mapOf(
            ConfigFieldKeys.REMARKS to uri.remark(), ConfigFieldKeys.ADDRESS to uri.hostName(), ConfigFieldKeys.PORT to uri.portOr("443"),
            ConfigFieldKeys.PASSWORD to uri.rawUserInfo.orEmpty().decodeComponent(), ConfigFieldKeys.PORT_HOPPING to (query["mport"] ?: "20000-50000"),
            ConfigFieldKeys.PORT_HOPPING_INTERVAL to (query["hop_interval"] ?: "10"), ConfigFieldKeys.OBFS_PASSWORD to query["obfs-password"].orEmpty(),
            ConfigFieldKeys.BANDWIDTH_UP to (query["upmbps"] ?: "100"), ConfigFieldKeys.BANDWIDTH_DOWN to (query["downmbps"] ?: "100"),
            ConfigFieldKeys.TLS to if (query["insecure"] == "1" || query["insecure"] == "true") "none" else "tls",
            ConfigFieldKeys.SNI to query["sni"].orEmpty(), ConfigFieldKeys.INSECURE to (query["insecure"] ?: "false"),
            ConfigFieldKeys.OBFS to query["obfs"].orEmpty()
        ))
    }
    override fun serialize(draft: ConfigDraft): String {
        val query = linkedMapOf<String, String>()
        addIfSet(query, "mport", draft[ConfigFieldKeys.PORT_HOPPING])
        addIfSet(query, "hop_interval", draft[ConfigFieldKeys.PORT_HOPPING_INTERVAL])
        addIfSet(query, "obfs", draft[ConfigFieldKeys.OBFS])
        addIfSet(query, "obfs-password", draft[ConfigFieldKeys.OBFS_PASSWORD])
        addIfSet(query, "upmbps", draft[ConfigFieldKeys.BANDWIDTH_UP])
        addIfSet(query, "downmbps", draft[ConfigFieldKeys.BANDWIDTH_DOWN])
        addIfSet(query, "sni", draft[ConfigFieldKeys.SNI])
        query["insecure"] = if (draft[ConfigFieldKeys.INSECURE] == "true" || draft[ConfigFieldKeys.TLS] == "none") "1" else "0"
        return buildUri("hysteria2", "${encodeComponent(draft[ConfigFieldKeys.PASSWORD])}@${formatHost(draft[ConfigFieldKeys.ADDRESS])}", draft[ConfigFieldKeys.PORT], query, draft[ConfigFieldKeys.REMARKS])
    }
}

private fun URI.queryParams(): Map<String, String> = queryParameters(rawQuery.orEmpty())

private fun queryParameters(raw: String): Map<String, String> = raw.split('&').filter(String::isNotBlank).associate { pair ->
    val key = pair.substringBefore('=').decodeComponent()
    val value = pair.substringAfter('=', "").decodeComponent()
    key to value
}

private fun URI.hostName(): String = host?.removePrefix("[")?.removeSuffix("]")
    ?: rawAuthority.orEmpty().substringAfterLast('@').substringBeforeLast(':').removePrefix("[").removeSuffix("]")

private fun URI.portOr(default: String): String = port.takeIf { it in 1..65535 }?.toString() ?: default
private fun URI.userName(): String = rawUserInfo.orEmpty().substringBefore(':').decodeComponent()
private fun URI.remark(): String = rawFragment.orEmpty().decodeComponent()

private fun String.decodeComponent(): String = runCatching { URLDecoder.decode(replace("+", "%2B"), StandardCharsets.UTF_8.name()) }.getOrDefault(this)
private fun encodeComponent(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
private fun encodeBase64(value: String): String = JBase64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
private fun encodeBase64Url(value: String): String = JBase64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

private fun decodeBase64(value: String): String {
    val clean = value.trim().replace("\\s".toRegex(), "")
    val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
    val bytes = runCatching { JBase64.getDecoder().decode(padded) }
        .recoverCatching { JBase64.getUrlDecoder().decode(padded) }
        .getOrThrow()
    return String(bytes, StandardCharsets.UTF_8)
}

private fun decodeBase64OrOriginal(value: String): String = runCatching { decodeBase64(value) }.getOrDefault(value)
private fun formatHost(value: String): String = value.trim().let { if (it.contains(':') && !it.startsWith("[")) "[$it]" else it }

private fun buildUri(scheme: String, authority: String, port: String, query: Map<String, String>, remark: String): String {
    val queryString = query.entries.joinToString("&") { "${encodeComponent(it.key)}=${encodeComponent(it.value)}" }
    return buildString {
        append(scheme); append("://"); append(authority); append(':'); append(port)
        if (queryString.isNotEmpty()) { append('?'); append(queryString) }
        if (remark.isNotBlank()) { append('#'); append(encodeComponent(remark)) }
    }
}

private fun addIfSet(target: MutableMap<String, String>, key: String, value: String, omitValue: String? = null) {
    if (value.isNotBlank() && value != omitValue) target[key] = value
}
