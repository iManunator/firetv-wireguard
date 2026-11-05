package dev.firetv.wireguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        if (!app.prefs.autoConnect || app.prefs.configText == null) return
        if (VpnService.prepare(context) != null) {
            Log.w(TAG, "Auto-connect skipped: VPN permission not granted yet")
            return
        }
        Log.i(TAG, "Auto-connect triggered by ${intent.action}")
        ConnectService.start(context)
    }

    companion object {
        private const val TAG = "FireWG"
    }
}
