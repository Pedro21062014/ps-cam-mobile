package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SavedDeviceEntity
import com.example.firebase.CloudCameraDevice
import com.example.model.AppMode
import com.example.ui.components.AuthDialog
import com.example.ui.components.PsCamLogoIcon
import com.example.ui.theme.PsCamTheme
import com.example.ui.theme.PsDanger
import com.example.ui.theme.PsOrangePrimary
import com.example.ui.theme.PsSuccess
import com.example.ui.viewmodel.MainViewModel

// Alfred Warm Header Colors
private val AlfredAmberPrimary = Color(0xFFFFA000)
private val AlfredAmberDark = Color(0xFFFF8F00)

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val colors = PsCamTheme.colors
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val savedDevices by viewModel.savedDevices.collectAsState()
    val cloudCameras by viewModel.cloudCameras.collectAsState()
    val surveillanceEvents by viewModel.surveillanceEvents.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val localIp by viewModel.localIp.collectAsState()
    val motionSensitivity by viewModel.motionSensitivity.collectAsState()
    val autoSiren by viewModel.autoSirenOnMotion.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Câmera, 1: Eventos, 2: Transmissão, 3: Conectar, 4: Mais
    var showManualConnectDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showModeDropdown by remember { mutableStateOf(false) }

    // Check for recent motion alert to display floating Alfred notification
    val recentMotionCamera = cloudCameras.firstOrNull { it.motionDetected }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Alfred-Style Bottom Navigation Tabs (Abas lá embaixo)
            NavigationBar(
                containerColor = colors.surface,
                contentColor = colors.textPrimary,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.surfaceBorder)
                    .testTag("bottom_navigation_bar")
            ) {
                // Tab 1: Câmera (Feed / Viewer)
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.Videocam else Icons.Outlined.Videocam,
                            contentDescription = "Câmera",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Câmera",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = if (colors.isDark) colors.primary else Color.Black,
                        indicatorColor = AlfredAmberPrimary,
                        unselectedIconColor = colors.textMuted,
                        unselectedTextColor = colors.textMuted
                    ),
                    modifier = Modifier.testTag("tab_camera")
                )

                // Tab 2: Eventos / Timeline
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        viewModel.setAppMode(AppMode.TIMELINE)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "Eventos",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Eventos",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = colors.primary,
                        indicatorColor = AlfredAmberPrimary,
                        unselectedIconColor = colors.textMuted,
                        unselectedTextColor = colors.textMuted
                    ),
                    modifier = Modifier.testTag("tab_events")
                )

                // Tab 3: Transmissão (Modo Câmera / Transmissor)
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        viewModel.setAppMode(AppMode.CAMERA_HOST)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "Transmissor",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Transmissor",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = colors.primary,
                        indicatorColor = AlfredAmberPrimary,
                        unselectedIconColor = colors.textMuted,
                        unselectedTextColor = colors.textMuted
                    ),
                    modifier = Modifier.testTag("tab_host")
                )

                // Tab 4: Conectar / Scanner
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        viewModel.setAppMode(AppMode.SCANNER)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.QrCodeScanner,
                            contentDescription = "Conectar",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Conectar",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = colors.primary,
                        indicatorColor = AlfredAmberPrimary,
                        unselectedIconColor = colors.textMuted,
                        unselectedTextColor = colors.textMuted
                    ),
                    modifier = Modifier.testTag("tab_scanner")
                )

                // Tab 5: Mais (Configurações / Conta)
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = "Mais",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Mais",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = if (colors.isDark) colors.primary else Color.Black,
                        indicatorColor = AlfredAmberPrimary,
                        unselectedIconColor = colors.textMuted,
                        unselectedTextColor = colors.textMuted
                    ),
                    modifier = Modifier.testTag("tab_more")
                )
            }
        }
    ) { innerPadding ->
        if (selectedTab == 4) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                MoreScreen(
                    viewModel = viewModel,
                    onNavigateToAuth = { showAuthDialog = true }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(colors.background)
            ) {
                // Alfred-Style Amber Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(AlfredAmberPrimary, AlfredAmberDark)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left / Center: Dropdown Selector "Viewer ▾" / "PS.Cam ▾"
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showModeDropdown = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("viewer_mode_dropdown")
                        ) {
                            PsCamLogoIcon(size = 28.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Visualizador",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Icon(
                                imageVector = Icons.Outlined.ArrowDropDown,
                                contentDescription = "Selecionar Modo",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showModeDropdown,
                            onDismissRequest = { showModeDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Visibility, null, tint = AlfredAmberPrimary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Visualizador (Monitorar)", fontWeight = FontWeight.Bold)
                                    }
                                },
                                onClick = {
                                    showModeDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Videocam, null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Câmera (Transmitir Vídeo)")
                                    }
                                },
                                onClick = {
                                    showModeDropdown = false
                                    viewModel.setAppMode(AppMode.CAMERA_HOST)
                                }
                            )
                        }
                    }

                    // Right Actions (Bell Notifications + Account/Settings)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Notification Bell
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.setAppMode(AppMode.TIMELINE) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notificações",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                            if (surveillanceEvents.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(PsDanger)
                                )
                            }
                        }

                        // Account Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.15f))
                                .clickable {
                                    if (currentUser == null) {
                                        showAuthDialog = true
                                    } else {
                                        showAccountMenu = true
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("top_account_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (currentUser != null) PsSuccess else Color.Black.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (currentUser != null) {
                                        currentUser?.email?.substringBefore("@") ?: "Conta"
                                    } else {
                                        "Entrar"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Main Content Area
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                ) {
                    // Floating Motion Detected Banner (Alfred Style from screenshot)
                    if (recentMotionCamera != null) {
                        item {
                            AlfredMotionBanner(
                                cameraName = recentMotionCamera.deviceName,
                                onWatchNow = {
                                    viewModel.startViewer(
                                        recentMotionCamera.ipAddress,
                                        recentMotionCamera.port,
                                        recentMotionCamera.deviceName,
                                        recentMotionCamera.id
                                    )
                                }
                            )
                        }
                    }

                    // Role Quick Switcher Hero (Câmera vs Visualizar)
                    item {
                        AlfredModeHero(
                            localIp = localIp,
                            onStartCamera = { viewModel.setAppMode(AppMode.CAMERA_HOST) },
                            onConnectIp = { showManualConnectDialog = true }
                        )
                    }

                    // Cloud Cameras Section Title
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudDone,
                                    contentDescription = null,
                                    tint = AlfredAmberPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "MINHAS CÂMERAS (${cloudCameras.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = colors.textPrimary
                                )
                            }

                            if (currentUser == null) {
                                Text(
                                    text = "Fazer Login",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AlfredAmberDark,
                                    modifier = Modifier
                                        .clickable { showAuthDialog = true }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }

                    // Camera Cards List (Alfred Camera Style)
                    if (cloudCameras.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.surface)
                                    .border(1.dp, colors.surfaceBorder, RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(AlfredAmberPrimary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Videocam,
                                            contentDescription = null,
                                            tint = AlfredAmberDark,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Nenhuma câmera conectada",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Abra este aplicativo em outro celular e toque na aba 'Transmissor'. Ele aparecerá aqui automaticamente.",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(cloudCameras, key = { it.id }) { camera ->
                            AlfredCameraCard(
                                camera = camera,
                                onWatchLive = {
                                    viewModel.startViewer(
                                        camera.ipAddress,
                                        camera.port,
                                        camera.deviceName,
                                        camera.id
                                    )
                                },
                                onOpenEvents = {
                                    viewModel.setAppMode(AppMode.TIMELINE)
                                },
                                onDelete = {
                                    viewModel.removeCloudCamera(camera.id)
                                }
                            )
                        }
                    }

                    // Web Access Browser Card
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.surfaceBorder, RoundedCornerShape(16.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(AlfredAmberPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = null,
                                    tint = AlfredAmberDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Acesso Web / PC",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Abra http://$localIp:8080 no seu navegador",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }

                    // Local Saved Devices
                    if (savedDevices.isNotEmpty()) {
                        item {
                            Text(
                                text = "CONEXÕES LOCAIS RECENTES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = colors.textMuted
                            )
                        }

                        items(savedDevices, key = { it.id }) { device ->
                            SavedDeviceItem(
                                device = device,
                                onClick = {
                                    viewModel.startViewer(
                                        device.ipAddress,
                                        device.port,
                                        device.deviceName,
                                        device.id
                                    )
                                },
                                onDelete = {
                                    viewModel.deleteDevice(device)
                                }
                            )
                        }
                    }
                }
            }
        }
        }

        // Auth Dialog
        if (showAuthDialog) {
            AuthDialog(
                initialEmail = "pssom.com.br@gmail.com",
                onDismiss = { showAuthDialog = false },
                onSignIn = { email, pass, callback ->
                    viewModel.firebaseManager.signIn(email, pass, callback)
                },
                onSignUp = { email, pass, callback ->
                    viewModel.firebaseManager.signUp(email, pass, callback)
                },
                onGuestLogin = { callback ->
                    viewModel.firebaseManager.signInAnonymously(callback)
                }
            )
        }

        // Account Details Dialog / Menu
        if (showAccountMenu) {
            AlertDialog(
                onDismissRequest = { showAccountMenu = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AccountCircle, null, tint = AlfredAmberPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Conta Conectada", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Email: ${currentUser?.email ?: "Anônimo"}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "UID: ${currentUser?.uid?.take(10)}...",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "Suas câmeras sincronizam instantaneamente no Firebase Cloud e no painel Web.",
                            fontSize = 12.sp,
                            color = colors.textMuted
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.firebaseManager.signOut()
                            showAccountMenu = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PsDanger),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.Logout, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sair da Conta", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAccountMenu = false }) {
                        Text("Fechar", color = colors.textSecondary)
                    }
                }
            )
        }

        // Manual PIN / Code / IP Connect Dialog
        if (showManualConnectDialog) {
            var inputCode by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var isSearching by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showManualConnectDialog = false },
                containerColor = colors.surface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.QrCode,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Emparelhar com Código", fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Digite o PIN de 6 dígitos mostrado na câmera transmissora ou o endereço IP:",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )

                        OutlinedTextField(
                            value = inputCode,
                            onValueChange = {
                                inputCode = it
                                errorMessage = null
                            },
                            label = { Text("Código PIN (ex: 489-123) ou IP") },
                            placeholder = { Text("Ex: 489-123 ou 192.168.1.50") },
                            singleLine = true,
                            isError = errorMessage != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                focusedLabelColor = colors.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                fontSize = 11.sp,
                                color = PsDanger,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (inputCode.isNotBlank()) {
                                isSearching = true
                                viewModel.connectByCode(inputCode.trim()) { success, msg ->
                                    isSearching = false
                                    if (success) {
                                        showManualConnectDialog = false
                                    } else {
                                        errorMessage = msg
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isSearching
                    ) {
                        Text("Conectar", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualConnectDialog = false }) {
                        Text("Cancelar", color = colors.textSecondary)
                    }
                }
            )
        }

        // Settings Dialog
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Configurações & Ajustes", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Theme Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Modo Escuro", fontSize = 13.sp, color = colors.textPrimary)
                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = { viewModel.toggleTheme() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = AlfredAmberPrimary
                                )
                            )
                        }

                        // Motion Sensitivity
                        Column {
                            Text(
                                "Sensibilidade de Movimento: ${(motionSensitivity * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = colors.textPrimary
                            )
                            Slider(
                                value = motionSensitivity,
                                onValueChange = { viewModel.setMotionSensitivity(it) },
                                valueRange = 0.05f..0.80f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AlfredAmberPrimary,
                                    activeTrackColor = AlfredAmberPrimary
                                )
                            )
                        }

                        // Auto Siren Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sirene Automática", fontSize = 13.sp, color = colors.textPrimary)
                                Text("Dispara alarme sonoro ao detectar intruso", fontSize = 10.sp, color = colors.textMuted)
                            }
                            Switch(
                                checked = autoSiren,
                                onCheckedChange = { viewModel.setAutoSirenOnMotion(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = AlfredAmberPrimary
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSettingsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = AlfredAmberPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

// Alfred Style Camera Card (16:9 Image Preview with Overlays & Footer Bar)
@Composable
fun AlfredCameraCard(
    camera: CloudCameraDevice,
    onWatchLive: () -> Unit,
    onOpenEvents: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PsCamTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(18.dp))
            .testTag("alfred_camera_card_${camera.id}")
    ) {
        // 16:9 Video Snapshot Preview Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2D3748), Color(0xFF1A202C))
                    )
                )
                .clickable { onWatchLive() },
            contentAlignment = Alignment.Center
        ) {
            // Camera Center Play Overlay
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = "Assistir Ao Vivo",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Top-Left Status Pills (Online, Battery %, Viewers)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Online Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (camera.isOnline) PsSuccess else PsDanger)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (camera.isOnline) "Online" else "Offline",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Battery Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (camera.isCharging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryFull,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${camera.batteryLevel}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Viewer Count Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "1/1",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Top-Right Settings Gear Circle
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Configurações da Câmera",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Bottom Motion Detected Alert Bar
            if (camera.motionDetected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PsDanger)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Warning, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "MOVIMENTO DETECTADO",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Card Bottom Bar (Alfred Camera Footer Strip)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera Name + Edit Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = camera.deviceName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Editar Nome",
                    tint = colors.textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Action Buttons: "Assistir" / "Check" & "Eventos"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Assistir (Check)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceLight)
                        .clickable { onWatchLive() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = "Assistir",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Assistir",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                // Eventos (Folder)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceLight)
                        .clickable { onOpenEvents() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = "Eventos",
                        tint = AlfredAmberDark,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Eventos",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlfredAmberDark
                    )
                }
            }
        }
    }
}

// Floating Motion Alert Banner (Alfred Style from screenshot)
@Composable
fun AlfredMotionBanner(
    cameraName: String,
    onWatchNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFFFCC80), RoundedCornerShape(16.dp))
            .clickable { onWatchNow() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AlfredAmberPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Movimento Detectado - $cameraName",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Toque para assistir agora em tempo real",
                fontSize = 11.sp,
                color = Color(0xFF6B7280)
            )
        }
        Text(
            text = "Agora",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = AlfredAmberDark
        )
    }
}

// Mode Selection Hero
@Composable
fun AlfredModeHero(
    localIp: String,
    onStartCamera: () -> Unit,
    onConnectIp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PsCamTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Modo do Dispositivo",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = AlfredAmberDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Escolha como deseja usar este aparelho",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            // IP Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceLight)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(PsSuccess)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$localIp:8080",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. CÂMERA (Transmissor) - Botão Laranja com Texto Preto
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AlfredAmberPrimary)
                    .clickable { onStartCamera() }
                    .padding(vertical = 12.dp, horizontal = 12.dp)
                    .testTag("connect_camera_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Videocam,
                        contentDescription = "Câmera",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "CÂMERA",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                        Text(
                            text = "Transmitir",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // 2. VISUALIZAR (Monitor) - Botão Preto com Borda e Texto Laranja
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (colors.isDark) Color(0xFF141417) else Color(0xFF09090B))
                    .border(1.5.dp, AlfredAmberPrimary, RoundedCornerShape(14.dp))
                    .clickable { onConnectIp() }
                    .padding(vertical = 12.dp, horizontal = 12.dp)
                    .testTag("new_monitor_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = "Monitor",
                        tint = AlfredAmberPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "VISUALIZAR",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = AlfredAmberPrimary
                        )
                        Text(
                            text = "Conectar por IP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

// Saved Device Item
@Composable
fun SavedDeviceItem(
    device: SavedDeviceEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PsCamTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp)
            .testTag("device_item_${device.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Smartphone,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = device.deviceName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "${device.ipAddress}:${device.port}",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Excluir",
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
