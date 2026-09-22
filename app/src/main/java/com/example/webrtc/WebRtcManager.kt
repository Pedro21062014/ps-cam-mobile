package com.example.webrtc

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WebRTC P2P Sender Manager for PS Cam.
 * Connects directly as a Camera Transmitter (Sender) to the VideoMeet service
 * (https://video-chat-bvo.pages.dev/) using a 6-digit PIN room (e.g. 319-813).
 * Captures camera and microphone in real time with ultra-low latency (< 200ms).
 */
class WebRtcManager(
    private val context: Context,
    private val onCommandReceived: (command: String, value: String) -> Unit = { _, _ -> },
    private val onConnectionStateChanged: (connected: Boolean, remotePeerId: String?) -> Unit = { _, _ -> }
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null

    private val _isWebRtcConnected = MutableStateFlow(false)
    val isWebRtcConnected: StateFlow<Boolean> = _isWebRtcConnected.asStateFlow()

    private val _connectedPeerId = MutableStateFlow<String?>(null)
    val connectedPeerId: StateFlow<String?> = _connectedPeerId.asStateFlow()

    var currentRoomId: String = ""
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun start(
        sessionId: String,
        initialFacing: String = "environment",
        initialQuality: String = "SD"
    ) {
        val cleanPin = sessionId.replace("-", "").replace("pscam_", "").trim().lowercase()
        val roomCode = if (sessionId.contains("-")) {
            sessionId.trim().lowercase()
        } else if (cleanPin.length == 6) {
            "${cleanPin.substring(0, 3)}-${cleanPin.substring(3)}"
        } else {
            cleanPin
        }
        currentRoomId = roomCode

        mainHandler.post {
            if (webView == null) {
                webView = WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(1, 1)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            Log.d("WebRtcManager", "Granting WebRTC permissions: ${request.resources.joinToString()}")
                            mainHandler.post {
                                request.grant(request.resources)
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.d("WebRtcManager", "Page loaded: $url for room=$roomCode")
                            // Inject transmitter bridge & auto-join handler
                            val facingMode = if (initialFacing == "front" || initialFacing == "user") "user" else "environment"
                            val qualityParam = if (initialQuality.equals("HD", ignoreCase = true)) "720p" else "480p"
                            evaluateJavascript("""
                                (function() {
                                    console.log('[PS-Cam-Transmitter] VideoMeet connected for room $roomCode');
                                    if (window.AndroidBridge) window.AndroidBridge.log('VideoMeet page ready on room $roomCode');

                                    // Ensure join triggers cleanly
                                    setTimeout(function() {
                                        const buttons = document.querySelectorAll('button');
                                        buttons.forEach(b => {
                                            const txt = (b.innerText || '').toLowerCase();
                                            if (txt.includes('entrar') || txt.includes('participar') || txt.includes('iniciar')) {
                                                b.click();
                                                console.log('[PS-Cam-Transmitter] Clicked join button: ' + txt);
                                            }
                                        });
                                    }, 800);

                                    // Hook RTCPeerConnection to report connection state back to Android UI
                                    if (!window._pscamHooked && window.RTCPeerConnection) {
                                        window._pscamHooked = true;
                                        const OrigPC = window.RTCPeerConnection;
                                        window.RTCPeerConnection = function(...args) {
                                            const pc = new OrigPC(...args);
                                            pc.addEventListener('connectionstatechange', function() {
                                                console.log('[PS-Cam-Transmitter] WebRTC State: ' + pc.connectionState);
                                                if (pc.connectionState === 'connected') {
                                                    if (window.AndroidBridge) window.AndroidBridge.onPeerConnected('$roomCode');
                                                } else if (pc.connectionState === 'disconnected' || pc.connectionState === 'closed' || pc.connectionState === 'failed') {
                                                    if (window.AndroidBridge) window.AndroidBridge.onPeerDisconnected();
                                                }
                                            });
                                            return pc;
                                        };
                                        window.RTCPeerConnection.prototype = OrigPC.prototype;
                                    }
                                })();
                            """.trimIndent(), null)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                Log.w("WebRtcManager", "Failed to load VideoMeet web: ${error?.description}. Falling back to embedded engine.")
                                val fallbackHtml = getWebRtcHtml(roomCode)
                                webView?.loadDataWithBaseURL("https://video-chat-bvo.pages.dev", fallbackHtml, "text/html", "UTF-8", null)
                            }
                        }
                    }

                    addJavascriptInterface(WebRtcBridge(), "AndroidBridge")
                }
            }

            // Direct VideoMeet URL with stream sender parameters
            val encodedName = Uri.encode("PS Cam - Transmissor")
            val videoMeetUrl = "https://video-chat-bvo.pages.dev/?room=$roomCode&mode=stream&role=sender&embed=true&name=$encodedName&audio=true&video=true"
            Log.d("WebRtcManager", "Connecting to VideoMeet room: $videoMeetUrl")
            webView?.loadUrl(videoMeetUrl)
        }
    }

    fun callMonitor(monitorPeerId: String) {
        if (monitorPeerId.isBlank() || monitorPeerId == currentRoomId) return
        mainHandler.post {
            Log.d("WebRtcManager", "Calling monitor peer: $monitorPeerId")
            webView?.evaluateJavascript("if (typeof callMonitor === 'function') { callMonitor('$monitorPeerId'); }", null)
        }
    }

    fun switchCamera(facing: String) {
        mainHandler.post {
            webView?.evaluateJavascript("""
                (function() {
                    if (typeof switchFacing === 'function') {
                        switchFacing('$facing');
                    } else {
                        const camBtns = document.querySelectorAll('button[title*="câmera"], button[title*="Camera"], #btn-preview-toggle-video');
                        if (camBtns.length > 0) camBtns[0].click();
                    }
                })();
            """.trimIndent(), null)
        }
    }

    fun setQuality(quality: String) {
        mainHandler.post {
            webView?.evaluateJavascript("if (typeof setQuality === 'function') { setQuality('$quality'); }", null)
        }
    }

    fun setAudioMuted(muted: Boolean) {
        mainHandler.post {
            webView?.evaluateJavascript("""
                (function() {
                    const micBtn = document.querySelector('button[title*="microfone"], button[title*="Microfone"], #btn-preview-toggle-audio');
                    if (micBtn) {
                        micBtn.click();
                    }
                })();
            """.trimIndent(), null)
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                webView?.evaluateJavascript("if (typeof destroyPeer === 'function') { destroyPeer(); }", null)
                webView?.stopLoading()
                webView?.destroy()
            } catch (_: Exception) {}
            webView = null
            _isWebRtcConnected.value = false
            _connectedPeerId.value = null
        }
    }

    inner class WebRtcBridge {
        @JavascriptInterface
        fun onPeerOpen(id: String) {
            Log.i("WebRtcManager", "WebRTC Transmitter Room Ready: $id")
        }

        @JavascriptInterface
        fun onPeerConnected(remotePeerId: String) {
            Log.i("WebRtcManager", "WebRTC Viewer Connected to Room: $remotePeerId")
            _isWebRtcConnected.value = true
            _connectedPeerId.value = remotePeerId
            mainHandler.post {
                onConnectionStateChanged(true, remotePeerId)
            }
        }

        @JavascriptInterface
        fun onPeerDisconnected() {
            Log.i("WebRtcManager", "WebRTC Viewer Disconnected")
            _isWebRtcConnected.value = false
            _connectedPeerId.value = null
            mainHandler.post {
                onConnectionStateChanged(false, null)
            }
        }

        @JavascriptInterface
        fun onCommandReceived(command: String, value: String) {
            Log.i("WebRtcManager", "Remote command received: $command = $value")
            mainHandler.post {
                onCommandReceived(command, value)
            }
        }

        @JavascriptInterface
        fun log(message: String) {
            Log.d("WebRtcManager", "[VideoMeet-WebRTC] $message")
        }
    }

    private fun getWebRtcHtml(roomPin: String): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>PS Cam WebRTC Transmitter Fallback</title>
    <script src="https://unpkg.com/peerjs@1.5.4/dist/peerjs.min.js"></script>
</head>
<body style="background: black; margin: 0; overflow: hidden;">
    <video id="localVideo" autoplay playsinline muted style="width: 1px; height: 1px; opacity: 0.01;"></video>
    <script>
        let peer = null;
        let localStream = null;
        let activeCall = null;
        let currentFacing = "environment";
        const room = "$roomPin";

        const STUN_CONFIG = {
            config: {
                iceServers: [
                    { urls: 'stun:stun.l.google.com:19302' },
                    { urls: 'stun:stun1.l.google.com:19302' },
                    { urls: 'stun:stun.cloudflare.com:3478' },
                    { urls: 'stun:global.stun.twilio.com:3478' }
                ]
            }
        };

        async function acquireMedia() {
            if (localStream) {
                localStream.getTracks().forEach(t => t.stop());
            }
            try {
                localStream = await navigator.mediaDevices.getUserMedia({
                    video: { facingMode: currentFacing, width: { ideal: 1280 }, height: { ideal: 720 } },
                    audio: true
                });
                const videoEl = document.getElementById('localVideo');
                if (videoEl) videoEl.srcObject = localStream;
                return localStream;
            } catch(e) {
                localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
                return localStream;
            }
        }

        function init() {
            try {
                peer = new Peer(room, STUN_CONFIG);
                peer.on('open', (id) => {
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onPeerOpen(id);
                        window.AndroidBridge.log("Fallback Peer ready: " + id);
                    }
                    acquireMedia();
                });
                peer.on('call', (call) => {
                    activeCall = call;
                    if (window.AndroidBridge) window.AndroidBridge.onPeerConnected(call.peer);
                    acquireMedia().then(stream => {
                        call.answer(stream);
                    });
                    call.on('close', () => {
                        if (window.AndroidBridge) window.AndroidBridge.onPeerDisconnected();
                    });
                });
                peer.on('error', (err) => {
                    if (window.AndroidBridge) window.AndroidBridge.log("Peer error: " + err.message);
                });
            } catch(e) {
                if (window.AndroidBridge) window.AndroidBridge.log("Peer init error: " + e.message);
            }
        }

        function switchFacing(facing) {
            currentFacing = (facing === 'user' || facing === 'front') ? 'user' : 'environment';
            acquireMedia().catch(() => {});
        }

        function destroyPeer() {
            if (localStream) localStream.getTracks().forEach(t => t.stop());
            if (peer) peer.destroy();
        }

        window.onload = init;
    </script>
</body>
</html>
        """.trimIndent()
    }
}
