package com.example.camera

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class HttpStreamServer(
    val port: Int = 8080,
    private val onCommandReceived: (command: String, value: String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var serverJob: Job? = null
    private val clientStreams = CopyOnWriteArrayList<OutputStream>()
    @Volatile private var latestFrame: ByteArray? = null

    @Volatile var isFlashOn: Boolean = false
    @Volatile var isSirenOn: Boolean = false
    @Volatile var isMotionDetected: Boolean = false

    fun updateFrame(jpegBytes: ByteArray) {
        latestFrame = jpegBytes
        if (clientStreams.isEmpty()) return

        val boundary = "\r\n--boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpegBytes.size}\r\n\r\n"
        val headerBytes = boundary.toByteArray()

        val iterator = clientStreams.iterator()
        while (iterator.hasNext()) {
            val stream = iterator.next()
            try {
                stream.write(headerBytes)
                stream.write(jpegBytes)
                stream.flush()
            } catch (e: Exception) {
                clientStreams.remove(stream)
            }
        }
    }

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                while (isActive && isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val path = parts[1]
            val output = socket.getOutputStream()

            when {
                path.startsWith("/video") -> {
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Type: multipart/x-mixed-replace; boundary=boundary\r\n" +
                            "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                            "Pragma: no-cache\r\n\r\n"
                    output.write(header.toByteArray())
                    output.flush()
                    clientStreams.add(output)
                    // Keep stream open
                    return
                }

                path.startsWith("/snapshot") -> {
                    val frame = latestFrame
                    if (frame != null) {
                        val header = "HTTP/1.1 200 OK\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Content-Type: image/jpeg\r\n" +
                                "Content-Length: ${frame.size}\r\n\r\n"
                        output.write(header.toByteArray())
                        output.write(frame)
                        output.flush()
                    } else {
                        val notFound = "HTTP/1.1 404 Not Found\r\n\r\nNo frame"
                        output.write(notFound.toByteArray())
                    }
                }

                path.startsWith("/api/command") -> {
                    // Extract query params e.g. /api/command?action=flash&value=1
                    val query = path.substringAfter("?", "")
                    val params = query.split("&").associate {
                        val pair = it.split("=")
                        if (pair.size == 2) pair[0] to pair[1] else "" to ""
                    }
                    val action = params["action"] ?: ""
                    val value = params["value"] ?: ""
                    if (action.isNotEmpty()) {
                        onCommandReceived(action, value)
                    }

                    val jsonResp = "{\"status\":\"ok\",\"action\":\"$action\",\"value\":\"$value\"}"
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${jsonResp.length}\r\n\r\n"
                    output.write((header + jsonResp).toByteArray())
                    output.flush()
                }

                path.startsWith("/status") -> {
                    val jsonResp = "{\"online\":true,\"flash\":$isFlashOn,\"siren\":$isSirenOn,\"motion\":$isMotionDetected}"
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${jsonResp.length}\r\n\r\n"
                    output.write((header + jsonResp).toByteArray())
                    output.flush()
                }

                else -> {
                    // Serve PS Cam Web Interface
                    val html = getWebPageHtml()
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: ${html.toByteArray(Charsets.UTF_8).size}\r\n\r\n"
                    output.write(header.toByteArray())
                    output.write(html.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            }

            socket.close()
        } catch (e: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        clientStreams.clear()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
    }

    private fun getWebPageHtml(): String {
        return """
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>PS Cam · Monitor ao Vivo</title>
    <style>
        * { margin:0; padding:0; box-sizing:border-box; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
        body { background: #09090b; color: #f8fafc; min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 16px; }
        .card { background: #18181b; border: 1px solid #27272a; border-radius: 20px; padding: 20px; max-width: 600px; width: 100%; box-shadow: 0 20px 40px rgba(0,0,0,0.8); }
        .header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
        .logo { font-size: 20px; font-weight: 700; color: #fff; display: flex; align-items: center; gap: 6px; }
        .logo span { color: #38bdf8; }
        .badge { background: #22c55e22; color: #22c55e; padding: 4px 10px; border-radius: 99px; font-size: 12px; font-weight: 600; display: flex; align-items: center; gap: 6px; }
        .badge::before { content:''; width:8px; height:8px; background:#22c55e; border-radius:50%; }
        .video-box { position: relative; width: 100%; aspect-ratio: 4/3; background: #000; border-radius: 12px; overflow: hidden; display: flex; align-items: center; justify-content: center; border: 1px solid #27272a; }
        .video-box img { width: 100%; height: 100%; object-fit: cover; }
        .controls { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-top: 16px; }
        .btn { background: #27272a; color: #fff; border: 1px solid #3f3f46; border-radius: 12px; padding: 12px; font-size: 13px; font-weight: 600; cursor: pointer; display: flex; flex-direction: column; align-items: center; gap: 6px; transition: all 0.2s; }
        .btn:hover { background: #3f3f46; }
        .btn.danger { background: #ef444422; border-color: #ef444455; color: #ef4444; }
        .btn.danger:hover { background: #ef444444; }
        .footer { font-size: 12px; color: #64748b; text-align: center; margin-top: 16px; }
    </style>
</head>
<body>
    <div class="card">
        <div class="header">
            <div class="logo">PS<span>.</span>Cam Monitor</div>
            <div class="badge">AO VIVO</div>
        </div>
        <div class="video-box">
            <img src="/video" alt="Transmissão ao vivo">
        </div>
        <div class="controls">
            <button class="btn" onclick="sendCommand('flash')">⚡ Lanterna</button>
            <button class="btn" onclick="sendCommand('switch')">🔄 Alternar Câmera</button>
            <button class="btn danger" onclick="sendCommand('siren')">🚨 Alarme / Sirene</button>
        </div>
        <div class="footer">PS Cam · Câmera de Segurança Inteligente</div>
    </div>
    <script>
        function sendCommand(action) {
            fetch('/api/command?action=' + action + '&value=1');
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}
