package dev.firetv.wireguard

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

class ImportActivity : Activity() {
    private val app get() = application as App
    private var server: ImportServer? = null

    private lateinit var url: TextView
    private lateinit var status: TextView
    private lateinit var qr: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)
        url = findViewById(R.id.url)
        status = findViewById(R.id.import_status)
        qr = findViewById(R.id.qr)
        findViewById<Button>(R.id.back).apply {
            setOnClickListener { finish() }
            requestFocus()
        }
    }

    override fun onStart() {
        super.onStart()
        val ip = NetUtil.localIpv4()
        if (ip == null) {
            url.setText(R.string.no_network)
            return
        }
        val s = ImportServer(::onConfigReceived)
        try {
            s.start(ip)
        } catch (e: Exception) {
            url.text = getString(R.string.server_failed, e.message)
            return
        }
        server = s
        val link = "http://$ip:${s.port}/?t=${s.token}"
        url.text = link
        qr.setImageBitmap(NetUtil.qrBitmap(link, 512))
    }

    override fun onStop() {
        super.onStop()
        server?.stop()
        server = null
    }

    /** Called on a server thread. Returns an error for the phone, or null on success. */
    private fun onConfigReceived(text: String): String? {
        val normalized = text.replace("\r\n", "\n").trim()
        TunnelController.validate(normalized)?.let { err ->
            runOnUiThread { status.text = getString(R.string.import_error, err) }
            return err
        }
        app.prefs.configText = normalized
        runOnUiThread {
            status.setText(R.string.import_ok)
            status.setTextColor(colorOf(R.color.ok))
            app.tunnel.reapplyIfUp()
            status.postDelayed({ finish() }, 1500)
        }
        return null
    }
}
