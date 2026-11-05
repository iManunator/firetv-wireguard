package dev.firetv.wireguard

import android.content.Context

enum class SplitMode { ALL, EXCLUDE, INCLUDE }

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var configText: String?
        get() = sp.getString("config", null)
        set(v) {
            sp.edit().putString("config", v).commit()
        }

    var autoConnect: Boolean
        get() = sp.getBoolean("auto_connect", true)
        set(v) = sp.edit().putBoolean("auto_connect", v).apply()

    var splitMode: SplitMode
        get() = runCatching { SplitMode.valueOf(sp.getString("split_mode", null)!!) }.getOrDefault(SplitMode.ALL)
        set(v) = sp.edit().putString("split_mode", v.name).apply()

    var splitApps: Set<String>
        get() = sp.getStringSet("split_apps", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("split_apps", v).apply()
}
