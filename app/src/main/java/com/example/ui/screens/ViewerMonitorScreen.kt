package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.firebase.database.ValueEventListener
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppMode
import com.example.ui.theme.PsCamTheme
import com.example.ui.theme.PsCyan
import com.example.ui.theme.PsDanger
import com.example.ui.theme.PsSkyPrimary
import com.example.ui.theme.PsSuccess
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@Composable
fun ViewerMonitorScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val colors = PsCamTheme.colors
    val hostIp by viewModel.targetHostIp.collectAsState()
    val port by viewModel.targetPort.collectAsState()
    val deviceName by viewModel.targetDeviceName.collectAsState()
    val targetDeviceId by viewModel.targetDeviceId.collectAsState()

    var latestBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isConnected by remember { mutableStateOf(false) }
    var isRemoteFlashOn by remember { mutableStateOf(false) }
    var isRemoteSirenOn by remember { mutableStateOf(false) }
    var isRemoteMotionDetected by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val okHttpClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    val activeDeviceId = remember(targetDeviceId, hostIp) {
        if (targetDeviceId.isNotEmpty()) targetDeviceId else {
            if (hostIp.startsWith("cam_")) hostIp else ""
        }
    }

    // 1. Cloud / P2P Real-time Live Stream Observer (Internet / Without IP)
    DisposableEffect(activeDeviceId) {
        if (activeDeviceId.isEmpty()) return@DisposableEffect onDispose {}

        val listener = viewModel.firebaseManager.observeLiveStream(activeDeviceId) { base64Str ->
            try {
                val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                if (bmp != null) {
                    latestBitmap = bmp
                    isConnected = true
                }
            } catch (_: Exception) {}
        }

        onDispose {
            if (listener != null) {
                viewModel.firebaseManager.removeLiveStreamListener(activeDeviceId, listener)
            }
        }
    }

    // 2. High-speed local stream fallback (when on same Wi-Fi / IP available)
    LaunchedEffect(hostIp, port) {
        if (hostIp.isEmpty() || hostIp.startsWith("cam_")) return@LaunchedEffect

        while (isActive) {
            try {
                // Fetch frame snapshot
                val snapshotUrl = "http://$hostIp:$port/snapshot"
                val request = Request.Builder().url(snapshotUrl).build()
                val response = withContext(Dispatchers.IO) {
                    try { okHttpClient.newCall(request).execute() } catch (_: Exception) { null }
                }

                if (response != null && response.isSuccessful) {
                    val bytes = withContext(Dispatchers.IO) { response.body?.bytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            latestBitmap = bmp
                            isConnected = true
                        }
                    }
                    response.close()
                }

                // Poll status
                val statusUrl = "http://$hostIp:$port/status"
                val statusReq = Request.Builder().url(statusUrl).build()
                val statusResp = withContext(Dispatchers.IO) {
                    try { okHttpClient.newCall(statusReq).execute() } catch (_: Exception) { null }
                }
                if (statusResp != null && statusResp.isSuccessful) {
                    val bodyStr = withContext(Dispatchers.IO) { statusResp.body?.string() }
                    if (bodyStr != null) {
                        val json = JSONObject(bodyStr)
                        isRemoteFlashOn = json.optBoolean("flash", false)
                        isRemoteSirenOn = json.optBoolean("siren", false)
                        isRemoteMotionDetected = json.optBoolean("motion", false)
                    }
                    statusResp.close()
                }
            } catch (_: Exception) {
                // If cloud stream already provides frames, stay connected
                if (latestBitmap == null) {
                    isConnected = false
                }
            }

            delay(120) // Polling interval ~8 FPS
        }
    }

    fun sendRemoteCommand(command: String) {
        // Send command over Cloud / P2P
        if (activeDeviceId.isNotEmpty()) {
            viewModel.firebaseManager.sendRemoteCommand(activeDeviceId, command)
        }
        // Also send via local HTTP if host IP is available
        if (hostIp.isNotEmpty() && !hostIp.startsWith("cam_")) {
            scope.launch(Dispatchers.IO) {
                try {
                    val url = "http://$hostIp:$port/control?cmd=$command"
                    val req = Request.Builder().url(url).build()
                    okHttpClient.newCall(req).execute().close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video Stream Render or Connecting Placeholder
        if (latestBitmap != null) {
            Image(
                bitmap = latestBitmap!!.asImageBitmap(),
                contentDescription = "Fluxo de Vídeo Remoto",
                contentScale = if (isFullscreen) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("remote_video_player")
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surface),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.primary,
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Conectando ao fluxo de vídeo...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$hostIp:$port",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        // Top Header Controls
        if (!isFullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.setAppMode(AppMode.DASHBOARD) },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .testTag("viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }

                    // Device Info Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(99.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) PsSuccess else PsDanger)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = deviceName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isConnected) "AO VIVO" else "CONECTANDO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) PsSuccess else PsDanger
                        )
                    }

                    // Fullscreen Toggle
                    IconButton(
                        onClick = { isFullscreen = !isFullscreen },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .testTag("fullscreen_btn")
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                            contentDescription = "Tela Cheia",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Remote Motion Detected Warning Banner
        AnimatedVisibility(
            visible = isRemoteMotionDetected,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isFullscreen) 24.dp else 76.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(PsDanger.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("remote_motion_banner"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = "Alerta",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "INTRUSÃO DETECTADA NA CÂMERA",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Bottom Remote Control Deck
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp, start = 20.dp, end = 20.dp)
                .align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Remote Flashlight
                IconButton(
                    onClick = { sendRemoteCommand("flash") },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isRemoteFlashOn) colors.primary.copy(alpha = 0.25f) else Color.Transparent)
                        .testTag("remote_flash_btn")
                ) {
                    Icon(
                        imageVector = if (isRemoteFlashOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                        contentDescription = "Lanterna Remota",
                        tint = if (isRemoteFlashOn) colors.primary else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Remote Siren
                IconButton(
                    onClick = { sendRemoteCommand("siren") },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isRemoteSirenOn) PsDanger.copy(alpha = 0.3f) else Color.Transparent)
                        .testTag("remote_siren_btn")
                ) {
                    Icon(
                        imageVector = if (isRemoteSirenOn) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                        contentDescription = "Sirene Remota",
                        tint = if (isRemoteSirenOn) PsDanger else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Remote Snapshot Capture
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                        .clickable {
                            latestBitmap?.let { bmp ->
                                viewModel.saveSnapshot(bmp, deviceName)
                            }
                        }
                        .testTag("remote_snapshot_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = "Salvar Snapshot",
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Switch Camera Facing (Front/Back)
                IconButton(
                    onClick = { sendRemoteCommand("switch") },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .testTag("remote_switch_cam_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cameraswitch,
                        contentDescription = "Alternar Lente Remota",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Fullscreen button
                IconButton(
                    onClick = { isFullscreen = !isFullscreen },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .testTag("toggle_fs_btn")
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        contentDescription = "Alternar Tela Cheia",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
