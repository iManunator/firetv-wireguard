package dev.firetv.wireguard

import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class ManualImportActivity : Activity() {
    private val app get() = application as App

    private lateinit var field: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_import)
        field = findViewById(R.id.config_text)
        status = findViewById(R.id.manual_status)

        findViewById<Button>(R.id.paste).setOnClickListener { pasteFromClipboard() }
        findViewById<Button>(R.id.save).setOnClickListener { save() }
        findViewById<Button>(R.id.back).setOnClickListener { finish() }

        val shared = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent?.getStringExtra(EXTRA_CONFIG)
        when {
            !shared.isNullOrBlank() -> field.setText(shared)
            app.prefs.configText != null -> field.setText(app.prefs.configText)
        }
        findViewById<Button>(R.id.paste).requestFocus()
    }

    companion object {
        const val EXTRA_CONFIG = "config"
    }

    private fun pasteFromClipboard() {
        val clip = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = clip.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            showStatus(getString(R.string.clipboard_empty), error = true)
            return
        }
        field.setText(text)
        field.setSelection(field.text.length)
        showStatus(getString(R.string.clipboard_pasted), error = false)
    }

    private fun save() {
        val normalized = field.text.toString().replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) {
            showStatus(getString(R.string.manual_empty), error = true)
            return
        }
        TunnelController.validate(normalized)?.let { err ->
            showStatus(getString(R.string.import_error, err), error = true)
            return
        }
        app.prefs.configText = normalized
        app.tunnel.reapplyIfUp()
        showStatus(getString(R.string.import_ok), error = false)
        status.postDelayed({ finish() }, 1200)
    }

    private fun showStatus(message: String, error: Boolean) {
        status.visibility = View.VISIBLE
        status.text = message
        status.setTextColor(colorOf(if (error) R.color.error else R.color.ok))
    }
}
