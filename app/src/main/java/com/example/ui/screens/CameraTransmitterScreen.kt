package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.webkit.WebView
import android.widget.Toast
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.AppMode
import com.example.model.CameraFacing
import com.example.ui.theme.PsCamTheme
import com.example.ui.theme.PsCyan
import com.example.ui.theme.PsDanger
import com.example.ui.theme.PsOrangeDark
import com.example.ui.theme.PsOrangePrimary
import com.example.ui.theme.PsSuccess
import com.example.ui.viewmodel.MainViewModel
import com.example.util.QRCodeUtil

@Composable
fun CameraTransmitterScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = PsCamTheme.colors

    val isFlashOn by viewModel.isFlashOn.collectAsState()
    val isSirenOn by viewModel.isSirenOn.collectAsState()
    val cameraFacing by viewModel.cameraFacing.collectAsState()
    val videoQuality by viewModel.videoQuality.collectAsState()
    val isBlackScreen by viewModel.isBlackScreen.collectAsState()
    val motionScore by viewModel.motionScore.collectAsState()
    val isMotionDetected by viewModel.isMotionDetected.collectAsState()
    val localIp by viewModel.localIp.collectAsState()
    val pairingPin by viewModel.pairingPin.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val accountEmail = if (currentUser != null && !currentUser!!.isAnonymous && !currentUser!!.email.isNullOrEmpty()) {
        currentUser!!.email!!
    } else {
        "pssom.com.br@gmail.com"
    }

    var showQrDialog by remember { mutableStateOf(false) }

    val cameraManager = remember {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    // Direct Torch Hardware Control
    LaunchedEffect(isFlashOn) {
        try {
            val cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: "0"
            cameraManager?.setTorchMode(cameraId, isFlashOn)
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // VideoMeet WebRTC P2P Hardware Transmitter View
        AndroidView(
            factory = { ctx ->
                viewModel.webRtcManager?.getOrCreateWebView(ctx) ?: WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Controls Bar
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
                        .testTag("host_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }

                // Pairing PIN & QR Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(1.dp, PsOrangePrimary.copy(alpha = 0.4f), RoundedCornerShape(99.dp))
                        .clickable { showQrDialog = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PsSuccess)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PIN: $pairingPin",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.QrCode,
                        contentDescription = "Ver QR",
                        tint = PsOrangePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Resolution Badge Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .clickable { viewModel.toggleQuality() }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("host_quality_btn")
                ) {
                    Text(
                        text = videoQuality.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PsCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Account Linked Confirmation Pill
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.Black.copy(alpha = 0.70f))
                    .border(1.dp, PsSuccess.copy(alpha = 0.4f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDone,
                    contentDescription = null,
                    tint = PsSuccess,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Salva na conta: $accountEmail",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        // Live Motion Detection HUD Banner
        AnimatedVisibility(
            visible = isMotionDetected,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 105.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(PsDanger.copy(alpha = 0.88f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("motion_detected_banner"),
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
                    text = "MOVIMENTO DETECTADO (${"%.0f".format(motionScore)}%)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Motion Sensitivity Activity Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp, vertical = 80.dp)
                .align(Alignment.BottomCenter)
        ) {
            if (motionScore > 1f) {
                LinearProgressIndicator(
                    progress = { (motionScore / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = if (isMotionDetected) PsDanger else colors.primary,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            }
        }

        // Bottom Controls Floating Toolbar
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
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flashlight Toggle
                IconButton(
                    onClick = { viewModel.toggleFlash() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isFlashOn) colors.primary.copy(alpha = 0.25f) else Color.Transparent)
                        .testTag("host_flash_btn")
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                        contentDescription = "Lanterna",
                        tint = if (isFlashOn) colors.primary else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Siren Alarm Toggle
                IconButton(
                    onClick = { viewModel.toggleSiren() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isSirenOn) PsDanger.copy(alpha = 0.3f) else Color.Transparent)
                        .testTag("host_siren_btn")
                ) {
                    Icon(
                        imageVector = if (isSirenOn) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                        contentDescription = "Sirene",
                        tint = if (isSirenOn) PsDanger else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Snap Photo Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                        .clickable {
                            val wv = viewModel.webRtcManager?.getOrCreateWebView(context)
                            if (wv != null && wv.width > 0 && wv.height > 0) {
                                try {
                                    val bitmap = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(bitmap)
                                    wv.draw(canvas)
                                    viewModel.saveSnapshot(bitmap, "Câmera Local")
                                    Toast.makeText(context, "Foto salva com sucesso!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        .testTag("host_snapshot_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = "Snapshot",
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Switch Front/Back Camera
                IconButton(
                    onClick = { viewModel.toggleCameraFacing() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .testTag("host_switch_cam_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cameraswitch,
                        contentDescription = "Trocar Câmera",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Black Screen Stealth Mode
                IconButton(
                    onClick = { viewModel.toggleBlackScreen() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .testTag("host_black_screen_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VisibilityOff,
                        contentDescription = "Modo Segundo Plano (Black Screen)",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Stealth Mode / Black Screen Overlay
        if (isBlackScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { viewModel.setBlackScreen(false) },
                            onLongPress = { viewModel.setBlackScreen(false) }
                        )
                    }
                    .testTag("black_screen_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isMotionDetected) PsDanger else PsSuccess.copy(alpha = 0.3f))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "PS.Cam Operando em Segundo Plano",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Toque 2 vezes na tela para reativar",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }

        // QR Code Pairing Dialog
        if (showQrDialog) {
            val rawPin = pairingPin.replace("-", "")
            val pairUri = "pscam://pair?id=${viewModel.localDeviceId}&pin=$rawPin&ip=$localIp:8080"
            val qrBitmap = remember(pairUri) {
                QRCodeUtil.generateQRCode(pairUri, 512, 512)
            }

            AlertDialog(
                onDismissRequest = { showQrDialog = false },
                containerColor = colors.surface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.QrCode,
                            contentDescription = null,
                            tint = PsOrangePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Código de Emparelhamento",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Digite o código abaixo no outro celular ou no painel Web para conectar instantaneamente:",
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Large Pairing Code Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(PsOrangePrimary.copy(alpha = 0.12f))
                                .border(1.5.dp, PsOrangePrimary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("PS Cam PIN", pairingPin)
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, "Código $pairingPin copiado!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "CÓDIGO DE ACESSO",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = PsOrangeDark
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = pairingPin,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 3.sp,
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copiar",
                                        tint = PsOrangeDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Generate New PIN Button
                        Button(
                            onClick = {
                                val newPin = viewModel.generateNewPin()
                                Toast.makeText(context, "Novo código gerado: $newPin", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.surfaceLight,
                                contentColor = colors.textPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Gerar Novo Código",
                                tint = PsOrangePrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Gerar Novo Código",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // High-contrast QR Code
                        if (qrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .border(2.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showQrDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = PsOrangePrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "Fechar",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    }
}
