package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoQuality
import com.example.ui.theme.PsCamTheme
import com.example.ui.theme.PsDanger
import com.example.ui.theme.PsOrangeDark
import com.example.ui.theme.PsOrangePrimary
import com.example.ui.theme.PsSuccess
import com.example.ui.viewmodel.MainViewModel

private val AlfredAmberPrimary = Color(0xFFFFA000)
private val AlfredAmberDark = Color(0xFFFF8F00)

@Composable
fun MoreScreen(
    viewModel: MainViewModel,
    onNavigateToAuth: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = PsCamTheme.colors

    val currentUser by viewModel.currentUser.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val videoQuality by viewModel.videoQuality.collectAsState()
    val motionSensitivity by viewModel.motionSensitivity.collectAsState()
    val pairingPin by viewModel.pairingPin.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearDevicesDialog by remember { mutableStateOf(false) }
    var showSensitivityDialog by remember { mutableStateOf(false) }

    val userEmail = currentUser?.email ?: "pssom.com.br@gmail.com"
    val isAnonymous = currentUser?.isAnonymous == true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("more_screen"),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Alfred-style Modern Top Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(AlfredAmberPrimary, AlfredAmberDark)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Mais",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                        Text(
                            text = "Configurações, Conta e Preferências",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black.copy(alpha = 0.75f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Mais",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Account Profile Card (Alfred Camera Style)
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.surfaceBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("account_profile_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Avatar
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(PsOrangePrimary, PsOrangeDark)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userEmail.firstOrNull()?.uppercase() ?: "P",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userEmail,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(PsSuccess)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isAnonymous) "Modo Convidado" else "Conta Conectada • Nuvem",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary
                            )
                        }
                    }

                    if (currentUser == null || isAnonymous) {
                        OutlinedButton(
                            onClick = onNavigateToAuth,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Entrar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section: Câmera & Transmissão
        item {
            MoreSectionTitle("CÂMERA & TRANSMISSÃO")
            MoreCardContainer {
                // Quality Selector
                MoreListRow(
                    icon = Icons.Outlined.Hd,
                    title = "Qualidade do Vídeo",
                    subtitle = "Transmissão atual em ${videoQuality.name} (${if (videoQuality == VideoQuality.HD) "720p 30fps" else "480p econômico"})",
                    trailing = {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceLight)
                                .border(1.dp, colors.surfaceBorder, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (videoQuality == VideoQuality.SD) PsOrangePrimary else Color.Transparent)
                                    .clickable { viewModel.setVideoQuality(VideoQuality.SD) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "SD",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (videoQuality == VideoQuality.SD) Color.Black else colors.textSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (videoQuality == VideoQuality.HD) PsOrangePrimary else Color.Transparent)
                                    .clickable { viewModel.setVideoQuality(VideoQuality.HD) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "HD",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (videoQuality == VideoQuality.HD) Color.Black else colors.textSecondary
                                )
                            }
                        }
                    }
                )

                HorizontalDivider(color = colors.surfaceBorder.copy(alpha = 0.5f))

                // Motion Sensitivity
                val sensLabel = when {
                    motionSensitivity <= 0.3f -> "Baixa"
                    motionSensitivity <= 0.6f -> "Média"
                    else -> "Alta"
                }
                MoreListRow(
                    icon = Icons.Outlined.MotionPhotosOn,
                    title = "Sensibilidade de Movimento",
                    subtitle = "Nível atual: $sensLabel (${(motionSensitivity * 100).toInt()}%)",
                    onClick = { showSensitivityDialog = true },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )

                HorizontalDivider(color = colors.surfaceBorder.copy(alpha = 0.5f))

                // Pairing PIN Code
                MoreListRow(
                    icon = Icons.Outlined.QrCode,
                    title = "Código de Emparelhamento",
                    subtitle = "PIN Atual: $pairingPin",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PsOrangePrimary.copy(alpha = 0.12f))
                                    .border(1.dp, PsOrangePrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = ClipData.newPlainText("PS Cam PIN", pairingPin)
                                        clipboard?.setPrimaryClip(clip)
                                        Toast.makeText(context, "PIN $pairingPin copiado!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = pairingPin,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PsOrangeDark
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copiar",
                                        tint = PsOrangeDark,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceLight)
                                    .clickable {
                                        val newPin = viewModel.generateNewPin()
                                        Toast.makeText(context, "Novo PIN gerado: $newPin", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Gerar Novo",
                                    tint = PsOrangePrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                )
            }
        }

        // Section: Preferências do Sistema & Aparência
        item {
            MoreSectionTitle("PREFERÊNCIAS & SISTEMA")
            MoreCardContainer {
                // Dark Theme Toggle
                MoreListRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "Modo Escuro",
                    subtitle = if (isDarkTheme) "Visual escuro ativado" else "Visual claro ativado",
                    trailing = {
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { viewModel.toggleTheme() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = PsOrangePrimary,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = colors.surfaceLight
                            )
                        )
                    }
                )

                HorizontalDivider(color = colors.surfaceBorder.copy(alpha = 0.5f))

                // WebRTC & Cloud Protocol Info
                MoreListRow(
                    icon = Icons.Outlined.Sensors,
                    title = "Protocolo de Streaming",
                    subtitle = "WebRTC PeerJS & Google/Twilio STUN",
                    trailing = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PsSuccess.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "Ativo",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PsSuccess
                            )
                        }
                    }
                )

                HorizontalDivider(color = colors.surfaceBorder.copy(alpha = 0.5f))

                // Cloud Firebase Catalog Info
                MoreListRow(
                    icon = Icons.Outlined.CloudDone,
                    title = "Catálogo Firebase Nuvem",
                    subtitle = "Sincronização em tempo real Firestore",
                    trailing = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PsSuccess.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "Conectado",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PsSuccess
                            )
                        }
                    }
                )

                HorizontalDivider(color = colors.surfaceBorder.copy(alpha = 0.5f))

                // Clear Offline Cameras
                MoreListRow(
                    icon = Icons.Outlined.DeleteSweep,
                    title = "Limpar Câmeras Salvas",
                    subtitle = "Apagar histórico local de dispositivos",
                    onClick = { showClearDevicesDialog = true },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }
        }

        // Section: Sobre & Versão
        item {
            MoreSectionTitle("SOBRE O PS CAM")
            MoreCardContainer {
                MoreListRow(
                    icon = Icons.Outlined.Shield,
                    title = "PS Cam Segurança Inteligente",
                    subtitle = "Versão 1.0.0 Pro • Build 2026",
                    trailing = {
                        Text(
                            text = "v1.0",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textMuted
                        )
                    }
                )
            }
        }

        // Section: Botão de Sair Moderno & Minimalista (Alfred Camera Style)
        item {
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, PsDanger.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .clickable { showLogoutDialog = true }
                    .padding(16.dp)
                    .testTag("logout_button")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PsDanger.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = "Sair",
                                tint = PsDanger,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Sair da Conta",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PsDanger
                            )
                            Text(
                                text = "Encerrar sessão de $userEmail",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                        contentDescription = null,
                        tint = PsDanger.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }

    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = colors.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = null,
                        tint = PsDanger,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Sair da Conta",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
            },
            text = {
                Text(
                    text = "Deseja realmente desconectar da sua conta ($userEmail)? As transmissões ativas serão pausadas.",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.signOut()
                        Toast.makeText(context, "Sessão encerrada com sucesso!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PsDanger),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Sair", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar", color = colors.textPrimary)
                }
            }
        )
    }

    // Clear Saved Devices Dialog
    if (showClearDevicesDialog) {
        AlertDialog(
            onDismissRequest = { showClearDevicesDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    "Limpar Câmeras Salvas",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    "Isso removerá a lista de dispositivos salvos em cache local.",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDevicesDialog = false
                        viewModel.clearAllSavedDevices()
                        Toast.makeText(context, "Câmeras limpas com sucesso!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PsOrangePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Limpar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDevicesDialog = false }) {
                    Text("Cancelar", color = colors.textPrimary)
                }
            }
        )
    }

    // Motion Sensitivity Dialog
    if (showSensitivityDialog) {
        AlertDialog(
            onDismissRequest = { showSensitivityDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    "Sensibilidade de Movimento",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(
                        "Baixa (Recomendada para locais movimentados)" to 0.25f,
                        "Média (Padrão de segurança)" to 0.50f,
                        "Alta (Máxima precisão para áreas restritas)" to 0.80f
                    )
                    options.forEach { (label, value) ->
                        val isSelected = kotlin.math.abs(motionSensitivity - value) < 0.15f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) PsOrangePrimary.copy(alpha = 0.12f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) PsOrangePrimary else colors.surfaceBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.setMotionSensitivity(value)
                                    showSensitivityDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) PsOrangeDark else colors.textPrimary
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = PsOrangeDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSensitivityDialog = false }) {
                    Text("Fechar", color = colors.textPrimary)
                }
            }
        )
    }
}

@Composable
private fun MoreSectionTitle(title: String) {
    val colors = PsCamTheme.colors
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        color = colors.textMuted,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun MoreCardContainer(content: @Composable () -> Unit) {
    val colors = PsCamTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 4.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun MoreListRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null
) {
    val colors = PsCamTheme.colors
    val clickableModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PsOrangePrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        trailing()
    }
}
