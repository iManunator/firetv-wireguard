package dev.firetv.wireguard

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config

class MainActivity : Activity() {
    private val app get() = application as App
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var error: TextView
    private lateinit var connect: Button
    private lateinit var importBtn: Button
    private lateinit var splitBtn: Button
    private lateinit var autoBtn: Button

    private var parsedFor: String? = null
    private var parsed: Config? = null
    private var permissionError: String? = null

    private val listener: () -> Unit = { render() }
    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        details = findViewById(R.id.details)
        error = findViewById(R.id.error)
        connect = findViewById(R.id.connect)
        importBtn = findViewById(R.id.import_btn)
        splitBtn = findViewById(R.id.split_btn)
        autoBtn = findViewById(R.id.auto_btn)

        connect.setOnClickListener { toggle() }
        importBtn.setOnClickListener { startActivity(Intent(this, ImportActivity::class.java)) }
        splitBtn.setOnClickListener { startActivity(Intent(this, AppsActivity::class.java)) }
        autoBtn.setOnClickListener {
            app.prefs.autoConnect = !app.prefs.autoConnect
            render()
        }
        connect.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        app.tunnel.addListener(listener)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        app.tunnel.removeListener(listener)
        handler.removeCallbacks(ticker)
    }

    private fun toggle() {
        val tunnel = app.tunnel
        if (tunnel.busy) return
        permissionError = null
        when {
            app.prefs.configText == null -> startActivity(Intent(this, ImportActivity::class.java))
            tunnel.state == Tunnel.State.UP -> tunnel.setUp(false)
            else -> {
                val consent = VpnService.prepare(this)
                if (consent != null) startActivityForResult(consent, REQ_VPN) else tunnel.setUp(true)
            }
        }
        render()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_VPN) return
        if (resultCode == RESULT_OK) app.tunnel.setUp(true)
        else permissionError = "VPN permission was denied. Press Connect and choose OK to allow it."
        render()
    }

    private fun config(): Config? {
        val text = app.prefs.configText ?: return null
        if (text != parsedFor) {
            parsedFor = text
            parsed = runCatching { TunnelController.parse(text) }.getOrNull()
        }
        return parsed
    }

    private fun render() {
        val tunnel = app.tunnel
        val cfg = config()
        val up = tunnel.state == Tunnel.State.UP

        when {
            cfg == null -> {
                status.setText(R.string.status_no_config)
                status.setTextColor(getColor(R.color.muted))
                connect.setText(R.string.import_config)
            }
            tunnel.busy -> {
                status.setText(if (up) R.string.status_disconnecting else R.string.status_connecting)
                status.setTextColor(getColor(R.color.warn))
                connect.setText(if (up) R.string.disconnect else R.string.connect)
            }
            up -> {
                status.setText(R.string.status_connected)
                status.setTextColor(getColor(R.color.ok))
                connect.setText(R.string.disconnect)
            }
            else -> {
                status.setText(R.string.status_disconnected)
                status.setTextColor(getColor(R.color.muted))
                connect.setText(R.string.connect)
            }
        }
        details.text = buildDetails(cfg, up)

        val err = permissionError ?: tunnel.lastError
        error.visibility = if (err == null) View.GONE else View.VISIBLE
        error.text = err

        val apps = app.prefs.splitApps.size
        splitBtn.text = when (app.prefs.splitMode) {
            SplitMode.ALL -> getString(R.string.split_all)
            SplitMode.EXCLUDE -> getString(R.string.split_exclude, apps)
            SplitMode.INCLUDE -> getString(R.string.split_include, apps)
        }
        autoBtn.setText(if (app.prefs.autoConnect) R.string.auto_on else R.string.auto_off)
    }

    private fun buildDetails(cfg: Config?, up: Boolean): String {
        if (cfg == null) return getString(R.string.no_config_hint)
        val sb = StringBuilder()
        val peer = cfg.peers.firstOrNull()
        sb.append("Server:   ").append(peer?.endpoint?.orElse(null)?.toString() ?: "-").append('\n')
        sb.append("Address:  ").append(cfg.`interface`.addresses.joinToString()).append('\n')

        val stats = app.tunnel.statistics()
        if (up && stats != null) {
            sb.append("Traffic:  ↓ ").append(NetUtil.formatBytes(stats.totalRx()))
                .append("   ↑ ").append(NetUtil.formatBytes(stats.totalTx())).append('\n')
            val handshake = stats.peers().mapNotNull { stats.peer(it)?.latestHandshakeEpochMillis }.maxOrNull() ?: 0L
            if (handshake > 0) {
                val ago = (System.currentTimeMillis() - handshake) / 1000
                sb.append("Handshake: ${ago}s ago")
                if (ago > 180) sb.append("  ⚠ server not responding")
            } else if (System.currentTimeMillis() - app.tunnel.upSince > 10_000) {
                sb.append("Handshake: none ⚠ check Endpoint, UDP port forward, keys")
            } else {
                sb.append("Handshake: waiting…")
            }
        }
        return sb.toString()
    }

    companion object {
        private const val REQ_VPN = 1
    }
}
