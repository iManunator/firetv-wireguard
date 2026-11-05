package dev.firetv.wireguard

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface

object NetUtil {
    /** LAN IPv4 of this device, ignoring VPN/tunnel interfaces. */
    fun localIpv4(): String? {
        val candidates = runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.name.startsWith("tun") && !it.name.startsWith("ppp") }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
        for (nif in candidates) {
            val addr = nif.inetAddresses.toList().firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            if (addr != null) return addr.hostAddress
        }
        return null
    }

    fun qrBitmap(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val pixels = IntArray(size * size) { i ->
            if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
    }

    fun formatBytes(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> "%.1f KiB".format(b / 1024.0)
        b < 1024L * 1024 * 1024 -> "%.1f MiB".format(b / 1048576.0)
        else -> "%.2f GiB".format(b / 1073741824.0)
    }
}
