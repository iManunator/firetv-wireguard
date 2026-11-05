package dev.firetv.wireguard

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Tiny HTTP server that lets a phone on the same network paste or upload a
 * WireGuard config. Every request must carry the random token from the QR code.
 */
class ImportServer(private val onConfig: (String) -> String?) {
    val token: String = SecureRandom().let { rnd ->
        (1..16).map { TOKEN_ALPHABET[rnd.nextInt(TOKEN_ALPHABET.length)] }.joinToString("")
    }

    private var server: ServerSocket? = null
    val port: Int get() = server?.localPort ?: 0

    fun start(bindHost: String) {
        val address = InetAddress.getByName(bindHost)
        val s = ServerSocket()
        try {
            s.reuseAddress = true
            try {
                s.bind(InetSocketAddress(address, PREFERRED_PORT))
            } catch (_: Exception) {
                s.bind(InetSocketAddress(address, 0))
            }
        } catch (e: Exception) {
            s.close()
            throw e
        }
        server = s
        thread(name = "import-server", isDaemon = true) {
            while (!s.isClosed) {
                val client = runCatching { s.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { client.use { handle(it) } }
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 15_000
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val path = parts[1].substringBefore('?')
        val query = parts[1].substringAfter('?', "")
        val params = query.split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }

        var contentLength = 0
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            if (line.lowercase().startsWith("content-length:")) {
                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }

        if (params["t"] != token) return respond(socket, 403, "text/plain", "Invalid or expired link. Scan the QR code on the TV again.")

        when {
            method == "GET" && path == "/" -> respond(socket, 200, "text/html", page())
            method == "POST" && path == "/import" -> {
                if (contentLength <= 0 || contentLength > MAX_BODY) return respond(socket, 400, "text/plain", "Config is empty or too large")
                val body = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = input.read(body, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                if (read != contentLength) return respond(socket, 400, "text/plain", "Upload was interrupted")
                val error = onConfig(String(body, 0, read, Charsets.UTF_8))
                if (error == null) respond(socket, 200, "text/plain", "Imported! You can now connect on the TV.")
                else respond(socket, 400, "text/plain", error)
            }
            else -> respond(socket, 404, "text/plain", "Not found")
        }
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) return buf.toString("UTF-8").trimEnd('\r')
            buf.write(b)
            if (buf.size() > 8192) return null
        }
    }

    private fun respond(socket: Socket, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; else -> "Not Found" }
        val header = "HTTP/1.1 $code $status\r\nContent-Type: $type; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun page() = """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Send WireGuard config to Fire TV</title>
<style>
body{font-family:system-ui,sans-serif;background:#111;color:#eee;margin:0;padding:20px;max-width:640px;margin:auto}
h1{font-size:1.3em}textarea{width:100%;box-sizing:border-box;height:260px;background:#222;color:#eee;border:1px solid #444;border-radius:8px;padding:10px;font-family:monospace;font-size:13px}
button,label.btn{display:block;width:100%;box-sizing:border-box;padding:14px;margin:10px 0;border:0;border-radius:8px;font-size:1em;text-align:center;cursor:pointer}
button{background:#88171a;color:#fff}label.btn{background:#333;color:#eee}input[type=file]{display:none}
#msg{padding:12px;border-radius:8px;display:none}.ok{background:#1b5e20}.err{background:#7f1d1d}
</style></head><body>
<h1>Send WireGuard config to your Fire TV</h1>
<label class="btn">Choose .conf file<input type="file" id="f" accept=".conf,text/plain,*/*"></label>
<p>or paste it here:</p>
<textarea id="t" placeholder="[Interface]&#10;PrivateKey = ...&#10;Address = ...&#10;&#10;[Peer]&#10;PublicKey = ...&#10;Endpoint = ..."></textarea>
<button id="s">Send to TV</button>
<div id="msg"></div>
<script>
const t=document.getElementById('t'),m=document.getElementById('msg');
document.getElementById('f').onchange=e=>{const f=e.target.files[0];if(!f)return;const r=new FileReader();r.onload=()=>t.value=r.result;r.readAsText(f)};
document.getElementById('s').onclick=async()=>{
 m.style.display='block';m.className='';m.textContent='Sending...';
 try{const r=await fetch('/import?t=$token',{method:'POST',headers:{'Content-Type':'text/plain'},body:t.value});
  m.textContent=await r.text();m.className=r.ok?'ok':'err';}catch(e){m.textContent='Could not reach the TV: '+e;m.className='err';}
};
</script></body></html>
""".trimIndent()

    companion object {
        const val PREFERRED_PORT = 8765
        private const val MAX_BODY = 64 * 1024
        private const val TOKEN_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
    }
}
