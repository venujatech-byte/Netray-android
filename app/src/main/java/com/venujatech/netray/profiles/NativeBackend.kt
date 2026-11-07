package com.venujatech.netray.profiles

object NativeBackend {
    init { System.loadLibrary("netray_backend") }
    external fun parseVless(link: String): String
    external fun buildTunConfig(outbounds: String, tunFd: Int): String
    external fun buildProxyConfig(outbounds: String, socksPort: Int, httpPort: Int): String
}
