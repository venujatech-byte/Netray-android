use jni::{
    objects::{JClass, JString},
    sys::jstring,
    JNIEnv,
};
use percent_encoding::percent_decode_str;
use serde::Serialize;
use url::Url;
use uuid::Uuid;

#[derive(Serialize)]
struct ParsedProfile {
    name: String,
    server: String,
    port: u16,
    transport: String,
    security: String,
}

fn parse_vless(input: &str) -> Result<ParsedProfile, &'static str> {
    if input.len() > 16_384 {
        return Err("Profile link is too long");
    }
    let uri = Url::parse(input.trim()).map_err(|_| "Invalid profile link")?;
    if uri.scheme() != "vless" {
        return Err("Only VLESS links are supported currently");
    }
    Uuid::parse_str(uri.username()).map_err(|_| "Invalid VLESS identifier")?;
    let server = uri
        .host_str()
        .ok_or("Server address is missing")?
        .to_owned();
    let port = uri
        .port()
        .filter(|port| *port > 0)
        .ok_or("Server port is missing or invalid")?;
    let security = uri
        .query_pairs()
        .find(|(key, _)| key == "security")
        .map(|(_, value)| value.to_ascii_lowercase())
        .unwrap_or_else(|| "none".to_owned());
    if !matches!(security.as_str(), "none" | "tls" | "reality") {
        return Err("Unsupported security mode");
    }
    let transport = uri
        .query_pairs()
        .find(|(key, _)| key == "type")
        .map(|(_, value)| value.into_owned())
        .unwrap_or_else(|| "tcp".to_owned());
    let name = uri
        .fragment()
        .filter(|value| !value.trim().is_empty())
        .map(|value| percent_decode_str(value).decode_utf8_lossy().into_owned())
        .unwrap_or_else(|| server.clone());
    Ok(ParsedProfile {
        name,
        server,
        port,
        transport,
        security,
    })
}

fn build_tun_config(outbounds: &str, tun_fd: i32) -> Result<String, &'static str> {
    let outbounds: serde_json::Value =
        serde_json::from_str(outbounds).map_err(|_| "Invalid Xray outbounds")?;
    let nodes = outbounds
        .as_array()
        .filter(|items| !items.is_empty())
        .ok_or("Profile did not contain a usable outbound")?;
    let tag = nodes[0]
        .get("tag")
        .and_then(serde_json::Value::as_str)
        .ok_or("Xray outbound tag is missing")?;
    let config = serde_json::json!({
        "log": { "loglevel": "info", "access": "none" },
        "env": { "xray.tun.fd": tun_fd.to_string() },
        "inbounds": [{
            "tag": "tun-in", "protocol": "tun",
            "settings": { "name": "netray", "mtu": 1500, "gateway": ["10.0.0.1/30", "fd00::1/126"] }
        }],
        "outbounds": nodes,
        "routing": { "rules": [{ "type": "field", "inboundTag": ["tun-in"], "outboundTag": tag }] }
    });
    serde_json::to_string(&config).map_err(|_| "Could not generate Xray config")
}

fn result_json<T: Serialize>(result: Result<T, &'static str>) -> String {
    match result {
        Ok(value) => serde_json::to_string(&value).unwrap_or_else(|_| "{}".to_owned()),
        Err(error) => serde_json::json!({"error": error}).to_string(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_venujatech_netray_profiles_NativeBackend_parseVless(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let response = env
        .get_string(&input)
        .map_err(|_| "Invalid profile text")
        .and_then(|value| parse_vless(value.to_str().unwrap_or_default()));
    env.new_string(result_json(response))
        .expect("JNI string allocation failed")
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_venujatech_netray_profiles_NativeBackend_buildTunConfig(
    mut env: JNIEnv,
    _class: JClass,
    outbounds: JString,
    tun_fd: i32,
) -> jstring {
    let response = env
        .get_string(&outbounds)
        .map_err(|_| "Invalid Xray outbounds")
        .and_then(|value| build_tun_config(value.to_str().unwrap_or_default(), tun_fd));
    // The success value is already JSON; wrapping it as a JSON string breaks Kotlin's object parser.
    let output = match response {
        Ok(config) => config,
        Err(error) => serde_json::json!({"error": error}).to_string(),
    };
    env.new_string(output)
        .expect("JNI string allocation failed")
        .into_raw()
}

#[cfg(test)]
mod tests {
    use super::*;

    const URI: &str = "vless://00000000-0000-4000-8000-000000000001@example.com:443?security=tls&type=ws#Home%20VPN";

    #[test]
    fn parses_vless_uri_fields() {
        let parsed = parse_vless(URI).unwrap();
        assert_eq!(parsed.name, "Home VPN");
        assert_eq!(parsed.server, "example.com");
        assert_eq!(parsed.port, 443);
        assert_eq!(parsed.transport, "ws");
        assert_eq!(parsed.security, "tls");
    }

    #[test]
    fn rejects_bad_schemes_identifiers_and_oversized_links() {
        assert!(parse_vless("vmess://example").is_err());
        assert!(parse_vless("vless://not-a-uuid@example.com:443").is_err());
        assert!(parse_vless(&("vless://".to_owned() + &"a".repeat(16_385))).is_err());
    }

    #[test]
    fn builds_config_with_android_tun_fd_and_selected_outbound() {
        let config: serde_json::Value = serde_json::from_str(
            &build_tun_config(r#"[{"tag":"proxy","protocol":"vless"}]"#, 17).unwrap(),
        )
        .unwrap();
        assert_eq!(config["env"]["xray.tun.fd"], "17");
        assert_eq!(config["routing"]["rules"][0]["outboundTag"], "proxy");
    }
}
