package dev.firetv.wireguard

import android.app.Service
import android.content.Context
import android.os.Build

fun Context.colorOf(id: Int): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getColor(id)
    else @Suppress("DEPRECATION") resources.getColor(id)

fun Service.stopForegroundCompat() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(Service.STOP_FOREGROUND_REMOVE)
    else @Suppress("DEPRECATION") stopForeground(true)
