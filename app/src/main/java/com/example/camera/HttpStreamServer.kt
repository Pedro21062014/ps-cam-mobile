package com.example.camera

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class HttpStreamServer(
    val port: Int = 8080,
    var deviceId: String = "",
    var deviceName: String = "PS Cam",
    var pairingPin: String = "",
    var ipAddress: String = "",
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
    @Volatile var batteryLevel: Int = 100

    fun updateFrame(jpegBytes: ByteArray) {
        latestFrame = jpegBytes
        if (clientStreams.isEmpty()) return

        val boundary = "\r\n--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpegBytes.size}\r\n\r\n"
        val headerBytes = boundary.toByteArray()

        val iterator = clientStreams.iterator()
        while (iterator.hasNext()) {
            val stream = iterator.next()
            try {
                stream.write(headerBytes)
                stream.write(jpegBytes)
                stream.flush()
            } catch (_: Exception) {
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
                Log.e("HttpStreamServer", "Server error: ${e.message}")
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val path = fullPath.substringBefore("?")
            val output = socket.getOutputStream()

            // Handle CORS Preflight for any external Web App / Browser (React, Vue, Firebase, etc.)
            if (method == "OPTIONS") {
                val corsHeader = "HTTP/1.1 204 No Content\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With\r\n" +
                        "Access-Control-Max-Age: 86400\r\n" +
                        "Content-Length: 0\r\n\r\n"
                output.write(corsHeader.toByteArray())
                output.flush()
                socket.close()
                return
            }

            when {
                // Live MJPEG Stream (supports /video, /stream, /live, /mjpeg, /feed)
                path == "/video" || path == "/stream" || path == "/live" || path == "/mjpeg" || path == "/feed" -> {
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Type: multipart/x-mixed-replace; boundary=--frame\r\n" +
                            "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                            "Pragma: no-cache\r\n\r\n"
                    output.write(header.toByteArray())
                    output.flush()
                    clientStreams.add(output)
                    // Keep stream open
                    return
                }

                // Single Snapshot Image (supports /snapshot, /frame, /shot.jpg, /snapshot.jpg, /current.jpg)
                path == "/snapshot" || path == "/frame" || path == "/shot.jpg" || path == "/snapshot.jpg" || path == "/current.jpg" -> {
                    val frame = latestFrame
                    if (frame != null) {
                        val header = "HTTP/1.1 200 OK\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Content-Type: image/jpeg\r\n" +
                                "Content-Length: ${frame.size}\r\n" +
                                "Cache-Control: no-cache\r\n\r\n"
                        output.write(header.toByteArray())
                        output.write(frame)
                        output.flush()
                    } else {
                        val notFound = "HTTP/1.1 404 Not Found\r\nAccess-Control-Allow-Origin: *\r\n\r\nNo frame captured yet"
                        output.write(notFound.toByteArray())
                    }
                }

                // Status & Device Discovery JSON (supports /status, /api/status, /info, /device, /ping)
                path == "/status" || path == "/api/status" || path == "/info" || path == "/device" || path == "/ping" || path == "/cameras" -> {
                    val json = JSONObject().apply {
                        put("online", true)
                        put("status", "online")
                        put("deviceId", deviceId)
                        put("deviceName", deviceName)
                        put("name", deviceName)
                        put("pin", pairingPin)
                        put("rawPin", pairingPin.replace("-", ""))
                        put("ipAddress", ipAddress)
                        put("ip", ipAddress)
                        put("port", port)
                        put("streamUrl", "http://$ipAddress:$port/video")
                        put("snapshotUrl", "http://$ipAddress:$port/snapshot")
                        put("flash", isFlashOn)
                        put("siren", isSirenOn)
                        put("motion", isMotionDetected)
                        put("battery", batteryLevel)
                        put("batteryLevel", batteryLevel)
                        put("updatedAt", System.currentTimeMillis())
                    }
                    val body = json.toString()
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                            "Access-Control-Allow-Headers: *\r\n" +
                            "Content-Type: application/json; charset=UTF-8\r\n" +
                            "Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n\r\n"
                    output.write((header + body).toByteArray(Charsets.UTF_8))
                    output.flush()
                }

                // Remote Control Commands (supports /api/command, /control, /command, /cmd)
                path.startsWith("/api/command") || path.startsWith("/control") || path.startsWith("/command") || path.startsWith("/cmd") -> {
                    val query = fullPath.substringAfter("?", "")
                    val params = query.split("&").associate {
                        val kv = it.split("=")
                        if (kv.size == 2) kv[0] to kv[1] else "" to ""
                    }
                    val action = params["action"] ?: params["cmd"] ?: params["command"] ?: ""
                    val value = params["value"] ?: "1"

                    if (action.isNotEmpty()) {
                        onCommandReceived(action, value)
                    }

                    val jsonResp = JSONObject().apply {
                        put("status", "ok")
                        put("action", action)
                        put("value", value)
                        put("flash", isFlashOn)
                        put("siren", isSirenOn)
                    }.toString()

                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                            "Access-Control-Allow-Headers: *\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${jsonResp.length}\r\n\r\n"
                    output.write((header + jsonResp).toByteArray())
                    output.flush()
                }

                else -> {
                    // Serve Web Interface
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
            Log.e("HttpStreamServer", "Error closing server: ${e.message}")
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
    <title>PS Cam · Monitor Web</title>
    <style>
        * { margin:0; padding:0; box-sizing:border-box; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
        body { background: #0c0d0e; color: #f4f4f5; min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 20px; }
        .card { background: #18191b; border: 1px solid #27282b; border-radius: 20px; padding: 24px; max-width: 640px; width: 100%; box-shadow: 0 24px 48px rgba(0,0,0,0.8); }
        .header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
        .brand { display: flex; align-items: center; gap: 10px; }
        .brand-icon { width: 34px; height: 34px; border-radius: 10px; background: #f59e0b; display: flex; align-items: center; justify-content: center; font-weight: 900; color: #000; font-size: 16px; }
        .brand-title { font-size: 17px; font-weight: 700; color: #fff; }
        .brand-sub { font-size: 11px; color: #a1a1aa; }
        .badge { background: #22c55e22; color: #22c55e; border: 1px solid #22c55e44; padding: 5px 12px; border-radius: 99px; font-size: 11px; font-weight: 700; display: flex; align-items: center; gap: 6px; letter-spacing: 0.5px; }
        .badge::before { content:''; width:8px; height:8px; background:#22c55e; border-radius:50%; box-shadow: 0 0 8px #22c55e; }
        .video-box { position: relative; width: 100%; aspect-ratio: 4/3; background: #000; border-radius: 14px; overflow: hidden; display: flex; align-items: center; justify-content: center; border: 1px solid #2e3035; }
        .video-box img { width: 100%; height: 100%; object-fit: cover; }
        .motion-alert { position: absolute; top: 12px; left: 12px; background: #ef4444ee; color: #fff; padding: 6px 12px; border-radius: 8px; font-size: 11px; font-weight: 700; display: none; align-items: center; gap: 6px; }
        .pin-pill { display: flex; align-items: center; justify-content: space-between; background: #222327; border: 1px solid #32343a; padding: 10px 14px; border-radius: 12px; margin-top: 14px; }
        .pin-val { font-family: monospace; font-size: 16px; font-weight: 700; color: #f59e0b; }
        .controls { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-top: 14px; }
        .btn { background: #222327; color: #f4f4f5; border: 1px solid #32343a; border-radius: 12px; padding: 12px; font-size: 13px; font-weight: 600; cursor: pointer; display: flex; flex-direction: column; align-items: center; gap: 6px; transition: all 0.2s; }
        .btn:hover { background: #2c2e35; border-color: #4a4d57; }
        .btn:active { transform: scale(0.97); }
        .btn.danger { background: #ef44441a; border-color: #ef444444; color: #ef4444; }
        .btn.danger:hover { background: #ef444433; }
        .footer { font-size: 11px; color: #71717a; text-align: center; margin-top: 16px; }
    </style>
</head>
<body>
    <div class="card">
        <div class="header">
            <div class="brand">
                <div class="brand-icon">PS</div>
                <div>
                    <div class="brand-title" id="devName">${deviceName.ifEmpty { "PS Cam" }}</div>
                    <div class="brand-sub" id="devId">${deviceId}</div>
                </div>
            </div>
            <div class="badge" id="liveBadge">AO VIVO</div>
        </div>
        <div class="video-box">
            <img id="liveFeed" src="/video" alt="Câmera ao vivo" onerror="handleVideoError(this)">
            <div class="motion-alert" id="motionBox">⚠️ MOVIMENTO DETECTADO</div>
        </div>
        <div class="pin-pill">
            <span style="font-size:12px; color:#a1a1aa;">Código PIN de Pareamento:</span>
            <span class="pin-val" id="pinDisplay">${pairingPin.ifEmpty { "Disponível no app" }}</span>
        </div>
        <div class="controls">
            <button class="btn" onclick="sendCmd('flash')">⚡ Lanterna</button>
            <button class="btn" onclick="sendCmd('switch')">🔄 Alternar Lente</button>
            <button class="btn danger" onclick="sendCmd('siren')">🚨 Alarme</button>
        </div>
        <div class="footer">PS Cam · Conexão Direta e Nuvem</div>
    </div>
    <script>
        function sendCmd(action) {
            fetch('/api/command?action=' + action + '&value=1', { method: 'GET' })
                .then(r => r.json())
                .catch(e => console.log('Command sent'));
        }
        function handleVideoError(img) {
            setTimeout(() => {
                img.src = '/video?t=' + Date.now();
            }, 1000);
        }
        setInterval(() => {
            fetch('/status')
                .then(r => r.json())
                .then(data => {
                    if (data.deviceName) document.getElementById('devName').innerText = data.deviceName;
                    if (data.deviceId) document.getElementById('devId').innerText = data.deviceId;
                    if (data.pin) document.getElementById('pinDisplay').innerText = data.pin;
                    const mb = document.getElementById('motionBox');
                    if (mb) mb.style.display = data.motion ? 'flex' : 'none';
                })
                .catch(() => {});
        }, 2000);
    </script>
</body>
</html>
        """.trimIndent()
    }
}

