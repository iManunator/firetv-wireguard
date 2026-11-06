package dev.firetv.wireguard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class ManualImportActivity : Activity() {
    private val app get() = application as App

    private lateinit var field: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        setContentView(R.layout.activity_manual_import)
        field = findViewById(R.id.config_text)
        status = findViewById(R.id.manual_status)

        findViewById<Button>(R.id.save).setOnClickListener { save() }
        findViewById<Button>(R.id.back).setOnClickListener { finish() }

        val shared = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent?.getStringExtra(EXTRA_CONFIG)
        when {
            !shared.isNullOrBlank() -> field.setText(shared)
            app.prefs.configText != null -> field.setText(app.prefs.configText)
        }
        field.setSelection(field.text.length)
        field.requestFocus()
        field.post { showKeyboard() }
    }

    override fun onResume() {
        super.onResume()
        if (::field.isInitialized && field.hasFocus()) field.post { showKeyboard() }
    }

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(field, InputMethodManager.SHOW_FORCED)
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

    companion object {
        const val EXTRA_CONFIG = "config"
    }
}
