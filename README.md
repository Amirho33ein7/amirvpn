# AmirVPN

Two Android apps:

- **AmirVPN** — end-user VPN client based on sing-box/Android VpnService.
- **AmirVPN Manager** — management app for adding, enabling/disabling and deleting proxy share links, then publishing the active configuration.

The manager publishes a generated sing-box JSON document to a shared JSON endpoint. The client loads the same endpoint as a remote profile and auto-updates it.

The client build uses the open-source sing-box Android stack rather than a fake/local-only connection layer.

GitHub Actions builds both APKs and publishes them as release assets.
