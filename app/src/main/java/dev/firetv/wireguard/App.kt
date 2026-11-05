package dev.firetv.wireguard

import android.app.Application

class App : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var tunnel: TunnelController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        tunnel = TunnelController(this, prefs)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
