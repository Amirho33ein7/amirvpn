# AmirVPN

**AmirVPN** is the end-user Android VPN client based on sing-box and Android `VpnService`.

The app bundles the configured VLESS/Trojan server profiles locally, asks Android for VPN permission through the normal system permission flow, and starts the VPN tunnel after permission is granted.

GitHub Actions builds a single client APK, runs an Android emulator smoke test for the permission dialog and `tun0` tunnel, publishes the APK as the current release, and removes older releases and build artifacts.
