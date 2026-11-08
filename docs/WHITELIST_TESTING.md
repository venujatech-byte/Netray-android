# App whitelist routing checks

Use a test profile and destinations you control. Record the device Android version, selected ABI, and whether the active mode is TUN or Proxy.

## Manual checks

1. In TUN mode, leave a browser out of the whitelist and confirm its public IP is the proxy endpoint's egress IP.
2. Add that browser package to **Settings → App Whitelist → Apps**, disconnect, and reconnect. Confirm Android still shows the VPN as active while the browser reports the device network's direct public IP. Confirm a non-whitelisted app still reports the proxy egress IP.
3. In **URLs**, add a domain, `*.example.test`, an IPv4/IPv6 address, and IPv4/IPv6 CIDRs. Reconnect, then compare an allowed test host against a non-whitelisted host. Confirm the domain rule matches the apex and subdomains, and the IP rules match only their ranges.
4. Change or remove rules while connected. Confirm the screen says to reconnect; existing traffic keeps its previous routes until reconnect.
5. In local Proxy mode, configure a browser explicitly to use `127.0.0.1:10809`. Confirm a whitelisted domain uses the direct egress and other destinations use the proxy profile. The Android app-package exclusion cannot identify clients of this local proxy.
6. Disconnect Netray and confirm all test apps use the normal device network.
7. Enable Android's **Block connections without VPN** lockdown and verify Android may block excluded apps. Disable lockdown before treating a blocked app as a whitelist failure.
8. Try invalid hosts, invalid IPs/prefixes, duplicate domains (`example.test` plus `*.example.test`), and overlapping CIDRs. Verify invalid/overlapping additions are rejected with a message.
9. Select a system app with the toggle enabled, uninstall a selected app and reopen the screen, and verify the missing package is labeled and can be removed. Check that the picker only lists apps visible to the current Android user/work profile.
10. Add a large list (1,000+ rules/apps), filter it, rotate/reopen the screen, and verify settings persist without UI stalls or crashes.
11. Test DNS and IPv6 separately. Domain requests are sniffed by Xray, but DNS queries still use the VPN's configured DNS; this feature does not provide per-domain DNS isolation.

## Routing limits

- TUN app rules use `VpnService.Builder.addDisallowedApplication`, which sends those packages directly outside the VPN. Android lockdown can block that direct traffic.
- Domain and IP rules are matched by Xray and sent through its `freedom` direct outbound. Xray protects its outbound sockets from re-entering the VPN. Domain matching depends on HTTP/TLS/QUIC hostname sniffing; encrypted or unavailable hostnames cannot be matched reliably.
- The local Proxy mode only accepts connections from apps explicitly configured to use its loopback SOCKS/HTTP ports. Android does not expose a general per-app system proxy control, so a selected package that is manually configured to use the local proxy cannot be forced direct by package name.
- DNS queries continue to use the VPN DNS path. The app list is scoped to packages visible to the current Android user; work-profile packages are separate.
- Installed-app discovery uses a launcher-intent `<queries>` declaration rather than `QUERY_ALL_PACKAGES`; it lists launchable apps visible to Android's package-visibility rules.
