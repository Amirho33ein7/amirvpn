package com.amirvpn.manager

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import android.util.Base64
import io.nekohasekai.libbox.Libbox
import kotlin.concurrent.thread

data class Node(
    val id: Long,
    val name: String,
    val share: String,
    var enabled: Boolean = true
)

object Sync {
    const val URL = "https://api.jsonstorage.net/v1/json/amirvpn-core-9d8c1b6e/servers-4f7a2c91"
}

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("amirvpn_manager", MODE_PRIVATE) }
    private lateinit var listBox: LinearLayout
    private lateinit var status: TextView
    private val nodes = mutableListOf<Node>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        if (nodes.isEmpty()) seedFromAsset()
        buildUi()
        render()
        autoPublish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
            setBackgroundColor(Color.rgb(5, 9, 18))
        }

        val title = TextView(this).apply {
            text = "AmirVPN Manager"
            setTextColor(Color.WHITE)
            textSize = 25f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))

        val subtitle = TextView(this).apply {
            text = "مدیریت کانفیگ‌ها • انتشار مستقیم برای AmirVPN"
            setTextColor(Color.rgb(146, 165, 190))
            textSize = 13f
            setPadding(0, dp(4), 0, dp(12))
        }
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(button("＋ افزودن", Color.rgb(22, 31, 48)) { showAddDialog() },
            LinearLayout.LayoutParams(0, dp(46), 1f))
        actions.addView(Space(this), LinearLayout.LayoutParams(dp(6), dp(1)))
        actions.addView(button("⇩ ورود گروهی", Color.rgb(15, 57, 103)) { importBundleDialog() },
            LinearLayout.LayoutParams(0, dp(46), 1.25f))
        actions.addView(Space(this), LinearLayout.LayoutParams(dp(6), dp(1)))
        actions.addView(button("⟳ انتشار", Color.rgb(20, 76, 145)) { publish() },
            LinearLayout.LayoutParams(0, dp(46), 1f))
        root.addView(actions)

        status = TextView(this).apply {
            text = "وضعیت: آماده"
            setTextColor(Color.rgb(90, 196, 255))
            textSize = 12f
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(status)

        val scroll = ScrollView(this)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listBox)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun button(label: String, bg: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(bg)
            setOnClickListener { action() }
            isAllCaps = false
        }

    private fun render() {
        listBox.removeAllViews()
        if (nodes.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "هیچ کانفیگی ثبت نشده"
                setTextColor(Color.rgb(150, 160, 175))
                gravity = Gravity.CENTER
                setPadding(0, dp(28), 0, dp(28))
            })
            return
        }
        nodes.forEachIndexed { index, node ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(Color.rgb(12, 20, 34))
            }

            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val name = TextView(this).apply {
                text = "${index + 1}. ${node.name}"
                setTextColor(Color.WHITE)
                textSize = 16f
            }
            top.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            val toggle = Switch(this).apply {
                isChecked = node.enabled
                setOnCheckedChangeListener { _, checked ->
                    node.enabled = checked
                    save()
                    publish()
                }
            }
            top.addView(toggle, LinearLayout.LayoutParams(-2, -2))
            card.addView(top)

            val meta = TextView(this).apply {
                text = "${node.share.substringBefore("://").uppercase()} • ${endpoint(node.share)}"
                setTextColor(Color.rgb(128, 150, 180))
                textSize = 12f
                setPadding(0, dp(3), 0, dp(8))
            }
            card.addView(meta)

            val del = Button(this).apply {
                text = "حذف"
                isAllCaps = false
                setTextColor(Color.rgb(255, 120, 120))
                setBackgroundColor(Color.rgb(45, 18, 24))
                setOnClickListener {
                    nodes.removeAt(index)
                    save()
                    render()
                    publish()
                }
            }
            card.addView(del, LinearLayout.LayoutParams(-1, dp(42)))
            listBox.addView(card, LinearLayout.LayoutParams(-1, dp(142)).apply {
                setMargins(0, 0, 0, dp(8))
            })
        }
    }

    private fun showAddDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), 0)
        }
        val name = EditText(this).apply {
            hint = "نام سرور"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val link = EditText(this).apply {
            hint = "VLESS / Trojan share link"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            minLines = 3
        }
        box.addView(name)
        box.addView(link)
        AlertDialog.Builder(this)
            .setTitle("افزودن کانفیگ")
            .setView(box)
            .setNegativeButton("لغو", null)
            .setPositiveButton("افزودن") { _, _ ->
                val share = link.text.toString().trim()
                if (share.contains("://")) {
                    val derived = name.text.toString().trim().ifEmpty { deriveName(share) }
                    nodes.add(Node(System.currentTimeMillis(), derived, share, true))
                    save()
                    render()
                    publish()
                } else {
                    toast("لینک معتبر وارد کن")
                }
            }.show()
    }

    private fun importBundleDialog() {
        val input = EditText(this).apply {
            hint = "لینک‌های VLESS/Trojan یا Base64"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            minLines = 6
            maxLines = 12
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(input, FrameLayout.LayoutParams(-1, dp(220)))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("ورود گروهی کانفیگ‌ها")
            .setMessage("Base64 چندخطی یا چند لینک را یکجا وارد کن.")
            .setView(scroll)
            .setNegativeButton("لغو", null)
            .setPositiveButton("وارد کردن", null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val shares = decodeImport(input.text.toString())
                    if (shares.isEmpty()) {
                        throw IllegalArgumentException("هیچ لینک VLESS/Trojan پیدا نشد")
                    }

                    val existing = nodes.mapTo(mutableSetOf()) { it.share }
                    var added = 0
                    shares.distinct().forEach { share ->
                        if (existing.add(share)) {
                            nodes.add(Node(System.currentTimeMillis() + added, deriveName(share), share, true))
                            added++
                        }
                    }

                    save()
                    render()
                    publish()
                    toast("${added} کانفیگ وارد شد")
                    dialog.dismiss()
                } catch (e: Exception) {
                    toast("خطا در ورود: ${e.message ?: "داده نامعتبر"}")
                }
            }
        }
        dialog.show()
    }

    private fun decodeImport(rawInput: String): List<String> {
        val raw = rawInput.trim()
        if (raw.isBlank()) return emptyList()

        fun filterLines(text: String): List<String> =
            text.lines()
                .map { it.trim() }
                .filter { it.startsWith("vless://", true) || it.startsWith("trojan://", true) }

        val direct = filterLines(raw)
        if (direct.isNotEmpty()) return direct

        val compact = raw.replace("\\s".toRegex(), "")
        val decoded = Base64.decode(compact, Base64.DEFAULT).toString(StandardCharsets.UTF_8)
        return filterLines(decoded)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun seedFromAsset() {
        val raw = try {
            assets.open("seed.txt").bufferedReader().readText().trim()
        } catch (_: Exception) {
            ""
        }
        if (raw.isBlank()) return
        val decoded = runCatching {
            Base64.decode(raw.replace("\\s".toRegex(), ""), Base64.DEFAULT)
                .toString(StandardCharsets.UTF_8)
        }.getOrNull() ?: return
        decoded.lines().map { it.trim() }.filter { it.contains("://") }.forEachIndexed { i, share ->
            nodes.add(Node(i.toLong() + 1, deriveName(share), share, true))
        }
        save()
    }

    private fun load() {
        nodes.clear()
        val text = prefs.getString("nodes", "[]") ?: "[]"
        runCatching {
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                nodes.add(Node(
                    o.optLong("id", i.toLong()),
                    o.optString("name", "Server ${i + 1}"),
                    o.optString("share", ""),
                    o.optBoolean("enabled", true)
                ))
            }
        }
    }

    private fun save() {
        val arr = JSONArray()
        nodes.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("share", it.share)
                put("enabled", it.enabled)
            })
        }
        prefs.edit().putString("nodes", arr.toString()).apply()
    }

    private fun autoPublish() {
        thread {
            runCatching { publishBlocking() }
        }
    }

    private fun publish() {
        status.text = "وضعیت: در حال انتشار…"
        thread {
            try {
                publishBlocking()
                runOnUiThread { status.text = "وضعیت: منتشر شد ✓" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "وضعیت: خطا — ${e.message ?: "network"}" }
            }
        }
    }

    private fun publishBlocking() {
        val config = ConfigBuilder.build(nodes.filter { it.enabled })
        Libbox.checkConfig(config.toString())
        val conn = (URL(Sync.URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        conn.outputStream.use { it.write(config.toString().toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            throw IllegalStateException("HTTP $code ${err.take(120)}")
        }
        conn.disconnect()

        // Verify the exact remote endpoint now returns the configuration we published.
        val verify = (URL(Sync.URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
        }
        val verifyCode = verify.responseCode
        if (verifyCode !in 200..299) {
            val err = verify.errorStream?.bufferedReader()?.readText().orEmpty()
            verify.disconnect()
            throw IllegalStateException("انتشار انجام شد ولی دریافت مجدد ناموفق بود: HTTP $verifyCode ${err.take(120)}")
        }
        val remoteContent = verify.inputStream.bufferedReader().use { it.readText() }
        verify.disconnect()

        val remoteJson = runCatching { JSONObject(remoteContent) }
            .getOrElse { throw IllegalStateException("داده منتشرشده JSON معتبر برنگرداند") }
        if (remoteJson.toString() != config.toString()) {
            throw IllegalStateException("سرور نسخه جدید را برنگرداند؛ Sync تأیید نشد")
        }
        Libbox.checkConfig(remoteContent)
    }

    private fun endpoint(link: String): String = runCatching {
        val u = URI(link)
        "${u.host ?: "?"}:${if (u.port > 0) u.port else "default"}"
    }.getOrElse { "unknown" }

    private fun deriveName(link: String): String = runCatching {
        val u = URI(link)
        val fragment = u.rawFragment
        if (!fragment.isNullOrBlank()) URLDecoder.decode(fragment, "UTF-8")
        else "${u.scheme.uppercase()} • ${u.host ?: "server"}:${if (u.port > 0) u.port else ""}"
    }.getOrElse { link.substringBefore("://") }

    object ConfigBuilder {
        fun build(active: List<Node>): JSONObject {
            val outbounds = JSONArray()
            val tags = JSONArray()

            active.forEachIndexed { index, n ->
                val parsed = parseShare(n.share)
                    ?: throw IllegalArgumentException("کانفیگ ${n.name} قابل تبدیل به sing-box نیست")
                val tag = "node-${index + 1}"
                parsed.put("tag", tag)
                outbounds.put(parsed)
                tags.put(tag)
            }

            outbounds.put(JSONObject().put("type", "direct").put("tag", "direct"))
            outbounds.put(JSONObject().put("type", "block").put("tag", "block"))
            outbounds.put(JSONObject().put("type", "dns").put("tag", "dns-out"))

            val root = JSONObject()
            root.put("log", JSONObject().put("level", "warn"))
            root.put(
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
            root.put(
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
            if (tags.length() > 0) {
                val selector = JSONObject()
                    .put("type", "selector")
                    .put("tag", "proxy")
                    .put("outbounds", tags)
                    .put("default", tags.optString(0))
                outbounds.put(selector)
            }
            root.put("outbounds", outbounds)
            root.put(
                "route",
                JSONObject()
                    .put("auto_detect_interface", true)
                    .put(
                        "rules",
                        JSONArray().put(
                            JSONObject()
                                .put("protocol", "dns")
                                .put("outbound", "dns-out")
                        )
                    )
                    .put("final", if (tags.length() > 0) "proxy" else "direct")
            )
            return root
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
            val query = mutableMapOf<String, String>()
            u.rawQuery.orEmpty()
                .split('&')
                .filter { it.isNotBlank() }
                .forEach { pair ->
                    val p = pair.indexOf('=')
                    if (p > 0) query[dec(pair.substring(0, p))] = dec(pair.substring(p + 1))
                }

            if (scheme == "vless") {
                out.put("uuid", dec(info))
                query["flow"]?.takeIf { it.isNotBlank() }?.let { out.put("flow", it) }
            } else {
                out.put("password", dec(info))
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
                        JSONObject().put("enabled", true).put("fingerprint", it)
                    )
                }

                query["alpn"]?.takeIf { it.isNotBlank() }?.let {
                    val alpn = JSONArray()
                    it.split(',').forEach { a -> if (a.isNotBlank()) alpn.put(a.trim()) }
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

        private fun dec(v: String): String =
            URLDecoder.decode(v, StandardCharsets.UTF_8.name())
    }
}
