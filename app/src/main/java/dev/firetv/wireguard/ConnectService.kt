package dev.firetv.wireguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Background apps may not start the VpnService directly, so boot-time connects
 * run inside this short-lived foreground service.
 */
class ConnectService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        val app = application as App
        app.tunnel.setUp(true) { ok ->
            Log.i(TAG, "Auto-connect: ${if (ok) "up" else app.tunnel.lastError}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
            )
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }.setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.status_connecting))
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "FireWG"
        private const val CHANNEL = "connect"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, ConnectService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
