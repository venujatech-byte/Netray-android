# DNS and LAN bypass checks

1. While disconnected, set distinct primary and secondary IPv4 DNS addresses in Settings, apply them, connect, and confirm name lookups work. Repeat with valid IPv6 addresses.
2. Enter a hostname, an address with a port, an invalid IP, and identical primary/secondary values. Confirm Apply stays disabled and the error is shown.
3. While TUN or Proxy mode is connected, confirm the DNS fields, Apply button, and LAN bypass switch are disabled. Disconnect, change settings, reconnect, and confirm the changes apply.
4. With **Bypass LAN traffic** off, verify the device follows normal full-tunnel routing. Turn it on, reconnect, and verify the router admin page (for example `192.168.1.1`), another device on the LAN, and `.local` discovery work directly while a public IP check still shows the proxy egress.
5. Repeat the LAN check on Android 13+ (route exclusions) and Android 8–12 (complementary included routes). Also check IPv6 link-local and ULA devices when available.

LAN bypass covers private, carrier-shared, link-local, and multicast ranges. It does not bypass arbitrary custom subnets outside those ranges. DNS servers remain the selected primary and secondary; Xray's core DNS uses them in order as fallback, while libXray's process resolver API accepts one upstream and uses the primary.
