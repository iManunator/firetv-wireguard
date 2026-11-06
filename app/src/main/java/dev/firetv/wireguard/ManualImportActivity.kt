package dev.firetv.wireguard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.nio.charset.Charset

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

        findViewById<Button>(R.id.choose_file).setOnClickListener { openFilePicker() }
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

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/octet-stream", "*/*"))
        }
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.choose_file)), REQ_FILE)
        } catch (_: Exception) {
            showStatus(getString(R.string.file_picker_missing), error = true)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FILE || resultCode != RESULT_OK) return
        val uri = data?.data ?: run {
            showStatus(getString(R.string.file_read_failed), error = true)
            return
        }
        loadFromUri(uri)
    }

    private fun loadFromUri(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = ByteArray(MAX_FILE_BYTES + 1)
                var read = 0
                while (read <= MAX_FILE_BYTES) {
                    val n = input.read(bytes, read, bytes.size - read)
                    if (n < 0) break
                    read += n
                }
                if (read == 0) return showStatus(getString(R.string.file_empty), error = true)
                if (read > MAX_FILE_BYTES) return showStatus(getString(R.string.file_too_large), error = true)
                String(bytes, 0, read, Charset.forName("UTF-8"))
            }
        } catch (_: Exception) {
            null
        }
        if (text == null) {
            showStatus(getString(R.string.file_read_failed), error = true)
            return
        }
        val normalized = text.replace("\r\n", "\n").trim()
        field.setText(normalized)
        field.setSelection(field.text.length)
        TunnelController.validate(normalized)?.let { err ->
            showStatus(getString(R.string.import_error, err), error = true)
            return
        }
        showStatus(getString(R.string.file_loaded), error = false)
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
        private const val REQ_FILE = 20
        private const val MAX_FILE_BYTES = 64 * 1024
    }
}
