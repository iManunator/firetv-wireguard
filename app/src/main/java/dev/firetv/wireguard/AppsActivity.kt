package dev.firetv.wireguard

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.TextView
import kotlin.concurrent.thread

class AppsActivity : Activity() {
    private class AppEntry(val pkg: String, val label: String, val icon: Drawable)

    private val app get() = application as App
    private var apps: List<AppEntry> = emptyList()
    private var changed = false

    private lateinit var list: ListView
    private lateinit var hint: TextView
    private lateinit var modeButtons: Map<SplitMode, Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        list = findViewById(R.id.apps)
        hint = findViewById(R.id.split_hint)
        modeButtons = mapOf(
            SplitMode.ALL to findViewById(R.id.mode_all),
            SplitMode.EXCLUDE to findViewById(R.id.mode_exclude),
            SplitMode.INCLUDE to findViewById(R.id.mode_include),
        )
        modeButtons.forEach { (mode, btn) ->
            btn.setOnClickListener {
                app.prefs.splitMode = mode
                changed = true
                renderMode()
            }
        }
        list.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        list.setOnItemClickListener { _, _, position, _ ->
            val pkg = apps[position].pkg
            val selected = app.prefs.splitApps.toMutableSet()
            if (list.isItemChecked(position)) selected.add(pkg) else selected.remove(pkg)
            app.prefs.splitApps = selected
            changed = true
        }
        renderMode()
        modeButtons.getValue(app.prefs.splitMode).requestFocus()
        loadApps()
    }

    override fun onPause() {
        super.onPause()
        if (changed) {
            changed = false
            app.tunnel.reapplyIfUp()
        }
    }

    private fun renderMode() {
        val mode = app.prefs.splitMode
        modeButtons.forEach { (m, b) -> b.isSelected = m == mode }
        hint.setText(
            when (mode) {
                SplitMode.ALL -> R.string.split_hint_all
                SplitMode.EXCLUDE -> R.string.split_hint_exclude
                SplitMode.INCLUDE -> R.string.split_hint_include
            }
        )
        list.isEnabled = mode != SplitMode.ALL
        list.isFocusable = mode != SplitMode.ALL
        list.alpha = if (mode == SplitMode.ALL) 0.4f else 1f
    }

    private fun loadApps() {
        thread {
            val pm = packageManager
            val iconSize = (48 * resources.displayMetrics.density).toInt()
            val found = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
                .flatMap { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
                .distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName != packageName }
                .map { ri ->
                    AppEntry(
                        ri.activityInfo.packageName,
                        ri.loadLabel(pm).toString(),
                        ri.loadIcon(pm).apply { setBounds(0, 0, iconSize, iconSize) },
                    )
                }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                apps = found
                list.adapter = Adapter()
                val selected = app.prefs.splitApps
                apps.forEachIndexed { i, a -> list.setItemChecked(i, a.pkg in selected) }
            }
        }
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = (convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)) as CheckedTextView
            val entry = apps[position]
            view.text = entry.label
            view.setCompoundDrawablesRelative(entry.icon, null, null, null)
            return view
        }
    }
}
