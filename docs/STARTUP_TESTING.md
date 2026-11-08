# Startup behavior checks

1. Grant notification permission, import at least one profile, connect once in TUN mode to grant Android VPN consent, and select a profile.
2. Enable **Connect at startup** and close/reopen Netray. It should start the selected profile after encrypted profiles finish loading. If Android notification permission is missing, Netray requests it before connecting.
3. Enable **Run at system startup** as well, reboot, and verify the selected profile reconnects in TUN mode. Repeat in local Proxy mode.
4. Revoke Netray's VPN consent, reboot with both settings enabled, and tap the startup notification. Netray should open and request VPN consent before connecting.
5. Disable **Connect at startup**, leave **Run at system startup** enabled, reboot, and verify the notification opens Netray without connecting.
6. Verify a missing/deleted selected profile produces an actionable startup notification instead of starting an unrelated profile silently.

Android does not allow a normal app to open its Activity directly from a boot receiver, so **Run at system startup** uses a notification when auto-connect is off. Boot auto-connect requires a previously granted VPN consent for TUN mode. Android 15 restricts some foreground-service types at boot; Netray's TUN and local proxy services use the VPN/special-use type for this path. Force-stopped apps will not receive boot broadcasts until the user opens them again.
