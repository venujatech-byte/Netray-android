package com.venujatech.netray.proxy

/**
 * Hard guard that makes any accidental VPN API call fail loudly when
 * the app is in Proxy mode. Proxy mode must never call VpnService.prepare(), establish(),
 * protect(), or create a TUN interface.
 *
 * Call [enterProxyMode] / [leaveProxyMode] from the connection flow to track the active
 * mode. Any [assertNoVpnApi] call while in proxy mode throws an IllegalStateException.
 */
object ProxyModeGuard {
    @Volatile private var inProxyMode = false

    fun enterProxyMode() { inProxyMode = true }
    fun leaveProxyMode() { inProxyMode = false }

    /**
     * Asserts we are NOT in proxy mode. Use at the entry of any function that touches
     * the VPN API (VpnService.prepare, VpnService.Builder.establish, VpnService.protect,
     * etc.). Throws [IllegalStateException] if called while in proxy mode.
     */
    fun assertNoVpnApi(caller: String) {
        if (inProxyMode) {
            throw IllegalStateException(
                "VPN API call attempted in Proxy mode by $caller. " +
                "Proxy mode must never use VpnService, TUN, or protect()."
            )
        }
    }
}
