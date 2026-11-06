package dev.firetv.wireguard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

class ImportActivity : Activity() {
    private val app get() = application as App
    private val handler = Handler(Looper.getMainLooper())
    private var server: ImportServer? = null
    private var finished = false

    private lateinit var url: TextView
    private lateinit var status: TextView
    private lateinit var qr: ImageView
    private lateinit var manual: Button

    private val showManual = Runnable {
        if (finished || isFinishing) return@Runnable
        manual.visibility = View.VISIBLE
        status.setText(R.string.import_qr_stuck)
        status.setTextColor(colorOf(R.color.warn))
        manual.requestFocus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)
        url = findViewById(R.id.url)
        status = findViewById(R.id.import_status)
        qr = findViewById(R.id.qr)
        manual = findViewById(R.id.manual)
        manual.setOnClickListener {
            handler.removeCallbacks(showManual)
            startActivity(Intent(this, ManualImportActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.back).apply {
            setOnClickListener { finish() }
            requestFocus()
        }
    }

    override fun onStart() {
        super.onStart()
        finished = false
        manual.visibility = View.GONE
        status.setText(R.string.import_waiting)
        status.setTextColor(colorOf(R.color.warn))
        handler.removeCallbacks(showManual)
        handler.postDelayed(showManual, MANUAL_AFTER_MS)

        val ip = NetUtil.localIpv4()
        if (ip == null) {
            url.setText(R.string.no_network)
            // No QR possible: offer manual entry immediately.
            handler.removeCallbacks(showManual)
            handler.post(showManual)
            return
        }
        val s = ImportServer(::onConfigReceived)
        try {
            s.start(ip)
        } catch (e: Exception) {
            url.text = getString(R.string.server_failed, e.message)
            handler.removeCallbacks(showManual)
            handler.post(showManual)
            return
        }
        server = s
        val link = "http://$ip:${s.port}/?t=${s.token}"
        url.text = link
        qr.setImageBitmap(NetUtil.qrBitmap(link, 512))
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(showManual)
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
            finished = true
            handler.removeCallbacks(showManual)
            manual.visibility = View.GONE
            status.setText(R.string.import_ok)
            status.setTextColor(colorOf(R.color.ok))
            app.tunnel.reapplyIfUp()
            status.postDelayed({ finish() }, 1500)
        }
        return null
    }

    companion object {
        private const val MANUAL_AFTER_MS = 8_000L
    }
}
