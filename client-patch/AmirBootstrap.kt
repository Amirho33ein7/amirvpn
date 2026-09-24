package io.nekohasekai.sfa

import android.util.Base64
import android.util.Log
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object AmirBootstrap {
    private const val TAG = "AmirVPN"
    private const val ASSET_NAME = "amirs-servers.b64"

    suspend fun ensure(): Boolean {
        return runCatching {
            val raw = Application.application.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
                .replace("\\s".toRegex(), "")
            val decoded = Base64.decode(raw, Base64.DEFAULT).toString(StandardCharsets.UTF_8.name())
            val links = decoded.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("vless://", true) || it.startsWith("trojan://", true) }
                .distinct()
                .toList()
            require(links.isNotEmpty()) { "No bundled VPN servers" }

            val existing = ProfileManager.list().filter { it.name.startsWith("AmirVPN • ") }
            val desiredIds = mutableSetOf<Long>()
            var changed = existing.size != links.size

            links.forEachIndexed { index, share ->
                val name = deriveName(share, index + 1)
                val config = buildConfig(share)
                Libbox.checkConfig(config)

                val current = existing.firstOrNull { it.name == name }
                val file = if (current != null) {
                    File(current.typed.path)
                } else {
                    File(
                        File(Application.application.filesDir, "configs").also { it.mkdirs() },
                        ProfileManager.nextFileID().toString() + ".json"
                    )
                }

                val old = if (file.exists()) file.readText() else ""
                if (old != config) {
                    file.writeText(config)
                    changed = true
                }

                val profile = if (current == null) {
                    Profile(
                        name = name,
                        typed = TypedProfile().apply {
                            type = TypedProfile.Type.Local
                            path = file.path
                        }
                    ).apply { userOrder = ProfileManager.nextOrder() }
                } else {
                    current
                }

                if (profile.typed.path != file.path) {
                    profile.typed.path = file.path
                    changed = true
                }

                if (current == null) {
                    ProfileManager.create(profile, andSelect = false)
                    changed = true
                } else {
                    ProfileManager.update(profile)
                }

                desiredIds += profile.id
            }

            existing.filter { it.id !in desiredIds }.forEach {
                ProfileManager.delete(it)
                changed = true
            }

            if (Settings.selectedProfile == -1L || !desiredIds.contains(Settings.selectedProfile)) {
                ProfileManager.list()
                    .firstOrNull { desiredIds.contains(it.id) }
                    ?.let { Settings.selectedProfile = it.id }
            }

            Log.i(TAG, "Bundled servers installed: " + links.size)
            changed
        }.getOrElse {
            Log.e(TAG, "Bundled server install failed", it)
            false
        }
    }

    private fun buildConfig(share: String): String {
        val outbound = parseShare(share) ?: error("Unsupported server")
        outbound.put("tag", "proxy")

        val outbounds = JSONArray()
            .put(outbound)
            .put(JSONObject().put("type", "direct").put("tag", "direct"))
            .put(JSONObject().put("type", "dns").put("tag", "dns-out"))

        return JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put(
                "dns",
                JSONObject()
                    .put(
                        "servers",
                        JSONArray().put(
                            JSONObject()
                                .put("tag", "cloudflare")
                                .put("address", "https://1.1.1.1/dns-query")
                                .put("detour", "direct")
                        )
                    )
                    .put("final", "cloudflare")
            )
            .put(
                "inbounds",
                JSONArray().put(
                    JSONObject()
                        .put("type", "tun")
                        .put("tag", "tun-in")
                        .put("address", JSONArray().put("172.19.0.1/30"))
                        .put("auto_route", true)
                        .put("stack", "mixed")
                )
            )
            .put("outbounds", outbounds)
            .put(
                "route",
                JSONObject()
                    .put(
                        "rules",
                        JSONArray().put(
                            JSONObject().put("protocol", "dns").put("outbound", "dns-out")
                        )
                    )
                    .put("auto_detect_interface", true)
                    .put("final", "proxy")
            )
            .toString()
    }

    private fun parseShare(link: String): JSONObject? = runCatching {
        val u = URI(link.trim())
        val scheme = u.scheme?.lowercase() ?: return null
        if (scheme != "vless" && scheme != "trojan") return null

        val out = JSONObject()
            .put("type", scheme)
            .put("server", u.host ?: "")
            .put("server_port", if (u.port > 0) u.port else 443)

        val info = u.userInfo ?: return null
        val query = linkedMapOf<String, String>()
        u.rawQuery.orEmpty()
            .split('&')
            .filter { it.isNotBlank() }
            .forEach { pair ->
                val p = pair.indexOf('=')
                if (p > 0) {
                    query[decode(pair.substring(0, p))] = decode(pair.substring(p + 1))
                }
            }

        if (scheme == "vless") {
            out.put("uuid", decode(info))
            query["flow"]?.takeIf { it.isNotBlank() }?.let { out.put("flow", it) }
        } else {
            out.put("password", decode(info))
        }

        query["network"]?.lowercase()?.let {
            if (it == "tcp" || it == "udp") out.put("network", it)
        }

        when (query["type"]?.lowercase()) {
            "ws" -> {
                val headers = JSONObject()
                query["host"]?.takeIf { it.isNotBlank() }?.let { headers.put("Host", it) }
                out.put(
                    "transport",
                    JSONObject()
                        .put("type", "ws")
                        .put("path", query["path"] ?: "/")
                        .put("headers", headers)
                )
            }

            "grpc" -> {
                out.put(
                    "transport",
                    JSONObject()
                        .put("type", "grpc")
                        .put("service_name", query["serviceName"] ?: query["service_name"] ?: "")
                )
            }

            "httpupgrade" -> {
                val headers = JSONObject()
                query["host"]?.takeIf { it.isNotBlank() }?.let { headers.put("Host", it) }
                out.put(
                    "transport",
                    JSONObject()
                        .put("type", "httpupgrade")
                        .put("host", query["host"] ?: "")
                        .put("path", query["path"] ?: "/")
                        .put("headers", headers)
                )
            }

            "http" -> {
                val hosts = JSONArray()
                query["host"].orEmpty().split(',').map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { hosts.put(it) }
                val transport = JSONObject().put("type", "http").put("path", query["path"] ?: "/")
                if (hosts.length() > 0) transport.put("host", hosts)
                out.put("transport", transport)
            }

            "quic" -> out.put("transport", JSONObject().put("type", "quic"))
        }

        val security = query["security"]?.lowercase()
        if (security == "tls" || security == "reality") {
            val tls = JSONObject().put("enabled", true)
            val sni = query["sni"] ?: query["serverName"] ?: query["host"] ?: u.host
            if (!sni.isNullOrBlank()) tls.put("server_name", sni)

            query["fp"]?.takeIf { it.isNotBlank() }?.let {
                tls.put(
                    "utls",
                    JSONObject()
                        .put("enabled", true)
                        .put("fingerprint", it)
                )
            }

            query["alpn"]?.takeIf { it.isNotBlank() }?.let {
                val alpn = JSONArray()
                it.split(',').forEach { value ->
                    if (value.isNotBlank()) alpn.put(value.trim())
                }
                if (alpn.length() > 0) tls.put("alpn", alpn)
            }

            query["allowInsecure"]?.lowercase()?.let {
                if (it == "true" || it == "1") tls.put("insecure", true)
            }

            if (security == "reality") {
                val reality = JSONObject().put("enabled", true)
                query["pbk"]?.takeIf { it.isNotBlank() }?.let { reality.put("public_key", it) }
                query["sid"]?.takeIf { it.isNotBlank() }?.let { reality.put("short_id", it) }
                tls.put("reality", reality)
            }

            out.put("tls", tls)
        }

        if (out.optString("server").isBlank()) return null
        out
    }.getOrNull()

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun deriveName(link: String, index: Int): String = runCatching {
        val uri = URI(link)
        val label =
            uri.rawFragment?.takeIf { it.isNotBlank() }?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.name())
            } ?: (
                uri.scheme.uppercase() + " " +
                    (uri.host ?: "Server") + ":" +
                    if (uri.port > 0) uri.port else ""
                )
        "AmirVPN • " + label
    }.getOrElse { "AmirVPN • Server " + index }
}
