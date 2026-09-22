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
 * (https://video-chat-bvo.pages.dev/) using the official URL API specification:
 * mode=stream&role=sender&room={roomCode}&clean=true&embed=true&header=false&toolbar=false&controls=none&audio=true&video=true&quality=720p
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

    var currentFacingMode: String = "environment"
        private set

    var currentQualityMode: String = "720p"
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(ctx: Context): WebView {
        if (webView == null) {
            webView = WebView(ctx.applicationContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
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
                        Log.d("WebRtcManager", "VideoMeet loaded: $url for room=$currentRoomId")
                        evaluateJavascript("""
                            (function() {
                                console.log('[PS-Cam-Transmitter] Ready for room: ' + '$currentRoomId');
                                if (window.AndroidBridge) window.AndroidBridge.log('Page ready on room $currentRoomId');

                                // Auto-confirm join if prompt is present
                                setTimeout(function() {
                                    const buttons = document.querySelectorAll('button');
                                    buttons.forEach(b => {
                                        const txt = (b.innerText || '').toLowerCase();
                                        if (txt.includes('entrar') || txt.includes('participar') || txt.includes('iniciar')) {
                                            b.click();
                                            console.log('[PS-Cam-Transmitter] Clicked join: ' + txt);
                                        }
                                    });
                                }, 600);

                                // Hook RTCPeerConnection to track status
                                if (!window._pscamHooked && window.RTCPeerConnection) {
                                    window._pscamHooked = true;
                                    const OrigPC = window.RTCPeerConnection;
                                    window.RTCPeerConnection = function(...args) {
                                        const pc = new OrigPC(...args);
                                        pc.addEventListener('connectionstatechange', function() {
                                            console.log('[PS-Cam-Transmitter] PeerConnection state: ' + pc.connectionState);
                                            if (pc.connectionState === 'connected') {
                                                if (window.AndroidBridge) window.AndroidBridge.onPeerConnected('$currentRoomId');
                                            } else if (pc.connectionState === 'disconnected' || pc.connectionState === 'failed' || pc.connectionState === 'closed') {
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
                            Log.w("WebRtcManager", "VideoMeet load error: ${error?.description}, loading fallback HTML")
                            val fallbackHtml = getWebRtcHtml(currentRoomId)
                            webView?.loadDataWithBaseURL("https://video-chat-bvo.pages.dev", fallbackHtml, "text/html", "UTF-8", null)
                        }
                    }
                }

                addJavascriptInterface(WebRtcBridge(), "AndroidBridge")
            }
        }
        return webView!!
    }

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
        currentFacingMode = if (initialFacing == "front" || initialFacing == "user") "user" else "environment"
        currentQualityMode = if (initialQuality.equals("HD", ignoreCase = true)) "720p" else "480p"

        mainHandler.post {
            val wv = getOrCreateWebView(context)
            val encodedName = Uri.encode("PS Cam - Transmissor")
            // Exact documentation query parameters:
            val videoMeetUrl = "https://video-chat-bvo.pages.dev/?mode=stream&role=sender&room=$roomCode&clean=true&embed=true&header=false&toolbar=false&controls=none&audio=true&video=true&quality=$currentQualityMode&name=$encodedName"
            Log.d("WebRtcManager", "Starting VideoMeet Sender URL: $videoMeetUrl")
            wv.loadUrl(videoMeetUrl)
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
        currentFacingMode = if (facing == "front" || facing == "user") "user" else "environment"
        mainHandler.post {
            webView?.evaluateJavascript("""
                (function() {
                    if (typeof switchFacing === 'function') {
                        switchFacing('$currentFacingMode');
                    } else {
                        const camBtns = document.querySelectorAll('button[title*="câmera"], button[title*="Camera"], #btn-preview-toggle-video');
                        if (camBtns.length > 0) camBtns[0].click();
                    }
                })();
            """.trimIndent(), null)
        }
    }

    fun setQuality(quality: String) {
        currentQualityMode = if (quality.equals("HD", ignoreCase = true)) "720p" else "480p"
        mainHandler.post {
            webView?.evaluateJavascript("if (typeof setQuality === 'function') { setQuality('$currentQualityMode'); }", null)
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
    <video id="localVideo" autoplay playsinline muted style="width: 100vw; height: 100vh; object-fit: cover;"></video>
    <script>
        let peer = null;
        let localStream = null;
        let activeCall = null;
        let currentFacing = "$currentFacingMode";
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
                const videoEl = document.getElementById('localVideo');
                if (videoEl) videoEl.srcObject = localStream;
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
