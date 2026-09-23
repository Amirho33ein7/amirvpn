package com.amirvpn.manager

import android.app.Activity
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
import java.util.Base64
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
        actions.addView(Space(this), LinearLayout.LayoutParams(dp(8), dp(1)))
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
            hint = "VLESS / Trojan / VMess share link"
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

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun seedFromAsset() {
        val raw = try {
            assets.open("seed.txt").bufferedReader().readText().trim()
        } catch (_: Exception) {
            ""
        }
        if (raw.isBlank()) return
        val decoded = runCatching {
            Base64.getDecoder().decode(raw.replace("\\s".toRegex(), ""))
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
            throw IllegalStateException("HTTP $code ${err.take(120)}")
        }
        conn.disconnect()
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
                val parsed = parseShare(n.share) ?: return@forEachIndexed
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
                        .put("strict_route", true)
                        .put("stack", "mixed")
                )
            )
            val selector = JSONObject()
                .put("type", "selector")
                .put("tag", "proxy")
                .put("outbounds", tags)
            if (tags.length() > 0) selector.put("default", tags.optString(0))
            outbounds.put(selector)
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
            val scheme = u.scheme.lowercase()
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
                out.put("uuid", info)
            } else {
                out.put("password", dec(info))
            }

            if (query["type"]?.lowercase() == "ws") {
                out.put(
                    "transport",
                    JSONObject()
                        .put("type", "ws")
                        .put("path", query["path"] ?: "/")
                        .put(
                            "headers",
                            JSONObject().put("Host", query["host"] ?: (u.host ?: ""))
                        )
                )
            }

            if (query["security"]?.lowercase() == "tls") {
                val tls = JSONObject().put("enabled", true)
                val sni = query["sni"] ?: query["host"] ?: u.host
                if (!sni.isNullOrBlank()) tls.put("server_name", sni)
                query["fp"]?.let {
                    tls.put(
                        "utls",
                        JSONObject().put("enabled", true).put("fingerprint", it)
                    )
                }
                query["alpn"]?.takeIf { it.isNotBlank() }?.let {
                    val alpn = JSONArray()
                    it.split(',').forEach { a -> if (a.isNotBlank()) alpn.put(a.trim()) }
                    tls.put("alpn", alpn)
                }
                out.put("tls", tls)
            }
            out
        }.getOrNull()

        private fun dec(v: String): String =
            URLDecoder.decode(v, StandardCharsets.UTF_8.name())
    }
}
