package dev.firetv.wireguard

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

class TunnelController(private val context: Context, private val prefs: Prefs) {
    private val backend by lazy { GoBackend(context.applicationContext) }
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile var state: Tunnel.State = Tunnel.State.DOWN
        private set
    @Volatile var busy = false
        private set
    @Volatile var lastError: String? = null
        private set
    @Volatile var upSince = 0L
        private set

    private val tunnel = object : Tunnel {
        override fun getName() = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            state = newState
            notifyListeners()
        }
    }

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)

    private fun notifyListeners() = main.post { listeners.forEach { it() } }

    fun setUp(up: Boolean, done: ((Boolean) -> Unit)? = null) {
        busy = true
        notifyListeners()
        executor.execute {
            var ok = true
            try {
                val config = if (up) buildConfig() else null
                state = backend.setState(tunnel, if (up) Tunnel.State.UP else Tunnel.State.DOWN, config)
                if (state == Tunnel.State.UP) upSince = System.currentTimeMillis()
                lastError = null
            } catch (e: Throwable) {
                ok = false
                lastError = describe(e)
                state = runCatching { backend.getState(tunnel) }.getOrDefault(Tunnel.State.DOWN)
            }
            busy = false
            notifyListeners()
            done?.let { main.post { it(ok) } }
        }
    }

    /** Re-applies the config (e.g. after split tunneling changed) if the tunnel is up. */
    fun reapplyIfUp() {
        if (state == Tunnel.State.UP) setUp(true)
    }

    fun statistics(): Statistics? =
        if (state == Tunnel.State.UP) runCatching { backend.getStatistics(tunnel) }.getOrNull() else null

    fun buildConfig(): Config {
        val text = prefs.configText ?: throw IllegalStateException("No config imported yet")
        return parse(applySplit(text))
    }

    private fun applySplit(text: String): String {
        val lines = text.lines().filterNot {
            val key = it.substringBefore('=').trim().lowercase()
            key == "includedapplications" || key == "excludedapplications"
        }.toMutableList()
        val mode = prefs.splitMode
        if (mode == SplitMode.ALL) return lines.joinToString("\n")

        val pm = context.packageManager
        val apps = prefs.splitApps.filter {
            runCatching { pm.getPackageInfo(it, 0) }.isSuccess || it == context.packageName
        }
        if (apps.isEmpty()) return lines.joinToString("\n")

        val key = if (mode == SplitMode.INCLUDE) "IncludedApplications" else "ExcludedApplications"
        val idx = lines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
        if (idx >= 0) lines.add(idx + 1, "$key = ${apps.joinToString(", ")}")
        return lines.joinToString("\n")
    }

    private fun describe(e: Throwable): String = when (e) {
        is BackendException -> when (e.reason) {
            BackendException.Reason.VPN_NOT_AUTHORIZED -> "VPN permission was not granted"
            BackendException.Reason.UNABLE_TO_START_VPN -> "Android refused to start the VPN (another VPN app active?)"
            BackendException.Reason.TUN_CREATION_ERROR -> "Could not create the VPN interface"
            BackendException.Reason.GO_ACTIVATION_ERROR_CODE -> "WireGuard failed to start (check keys/addresses)"
            BackendException.Reason.DNS_RESOLUTION_FAILURE -> "Could not resolve server address: ${e.format.joinToString()}"
            else -> "Backend error: ${e.reason}"
        }
        is BadConfigException -> "Bad config: ${e.section} ${e.location} (${e.reason})"
        is PackageManager.NameNotFoundException -> "Split tunneling app not found: ${e.message}"
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        const val TUNNEL_NAME = "firetv"

        fun parse(text: String): Config =
            Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))

        /** Returns null if valid, otherwise a human readable error. */
        fun validate(text: String): String? = try {
            val c = parse(text)
            if (c.peers.isEmpty()) "Config has no [Peer] section" else null
        } catch (e: BadConfigException) {
            "Invalid ${e.section} ${e.location}: ${e.reason}" + (e.text?.let { " ('$it')" } ?: "")
        } catch (e: Exception) {
            e.message ?: "Invalid config"
        }
    }
}
